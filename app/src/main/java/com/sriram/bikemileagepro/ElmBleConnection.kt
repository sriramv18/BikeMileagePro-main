package com.sriram.bikemileagepro

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID

enum class ObdState { DISCONNECTED, CONNECTING, DISCOVERING, ADAPTER_READY, ECU_READY, ERROR }

/**
 * BLE GATT transport for ELM327-style adapters.
 *
 * Supports Nordic UART (6E40...) and HM-10 style (FFE0/FFE1) layouts first, then falls back to the
 * first service that has both a writable and a notifiable characteristic (many clones use FFF0/FFF1/FFF2).
 * After the UART is ready it runs the init sequence ATZ, ATE0, ATL0, ATS0, ATH0, ATSP0 and then asks the
 * bike "0100" (supported PIDs) so the UI can tell "adapter connected" apart from "ECU answered".
 *
 * Classic Bluetooth (SPP) ELM327 adapters are NOT BLE and cannot be used with this class.
 */
class ElmBleConnection(
    context: Context,
    private val db: TripDatabase? = null, // Added database dependency for auto-creating vehicle
    private val onState: (ObdState, String) -> Unit
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var tx: BluetoothGattCharacteristic? = null
    private val rx = StringBuilder()
    private val pending = ArrayDeque<String>()
    private var current: String? = null
    private var ecuAnswered = false
    private var fuelLevelSupported = false
    private var fuelRateSupported = false
    private var odometerSupported = false
    private var odometerKm: Double? = null
    private var timeout: Runnable? = null
    private var connectedDeviceName: String = ""

    private val knownServices = listOf(
        UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"),
        UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
    )
    private val knownTx = listOf(
        UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E"),
        UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
    )
    private val knownRx = listOf(
        UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"),
        UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
    )
    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    private fun report(state: ObdState, message: String) {
        main.post { onState(state, message) }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        teardown()
        val name = try { device.name } catch (_: SecurityException) { null }
        connectedDeviceName = name ?: "OBD Device"
        report(ObdState.CONNECTING, "Connecting to $connectedDeviceName…")
        try {
            gatt = if (Build.VERSION.SDK_INT >= 23)
                device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            else device.connectGatt(appContext, false, callback)
            if (gatt == null) report(ObdState.ERROR, "Could not start the BLE connection.")
        } catch (_: SecurityException) {
            report(ObdState.ERROR, "Bluetooth permission denied.")
        }
    }

    fun disconnect() {
        teardown()
        report(ObdState.DISCONNECTED, "Disconnected")
    }

    @SuppressLint("MissingPermission")
    private fun teardown() {
        main.post { cancelTimeout(); pending.clear(); current = null; rx.setLength(0) }
        val g = gatt
        gatt = null         // callbacks from the old GATT are ignored once this is null
        tx = null
        try { g?.disconnect(); g?.close() } catch (_: SecurityException) {}
    }

    private fun cancelTimeout() {
        timeout?.let { main.removeCallbacks(it) }
        timeout = null
    }

    @SuppressLint("MissingPermission")
    private fun write(command: String): Boolean {
        val g = gatt ?: return false
        val c = tx ?: return false
        val bytes = (command + "\r").toByteArray(Charsets.US_ASCII)
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, bytes, type) == BluetoothStatusCodes.SUCCESS
            } else {
                c.writeType = type
                @Suppress("DEPRECATION") c.value = bytes
                @Suppress("DEPRECATION") g.writeCharacteristic(c)
            }
        } catch (_: SecurityException) {
            false
        }
    }

    // ------------------------------------------------------ init sequence (main thread)

    private fun startInit() {
        pending.clear()
        pending.addAll(listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "0100", "012F", "015E", "01A6"))
        ecuAnswered = false
        fuelLevelSupported = false
        fuelRateSupported = false
        odometerSupported = false
        odometerKm = null
        rx.setLength(0)
        sendNext()
    }

    private fun sendNext() {
        cancelTimeout()
        if (pending.isEmpty()) { finishInit(); return }
        val cmd = pending.removeFirst()
        current = cmd
        rx.setLength(0)
        if (!write(cmd)) {
            current = null
            onState(ObdState.ERROR, "Could not write '$cmd' to the adapter.")
            return
        }
        val limit = if (cmd == "0100") 15_000L else 5_000L
        val r = Runnable { onTimeout(cmd) }
        timeout = r
        main.postDelayed(r, limit)
    }

    private fun onTimeout(cmd: String) {
        if (current != cmd) return
        current = null
        when (cmd) {
            "ATZ" -> {
                pending.clear()
                onState(ObdState.ERROR, "Connected over BLE, but the device did not answer like an ELM327 (no reply to ATZ).")
            }
            else -> sendNext()
        }
    }

    private fun onData(text: String) {
        rx.append(text)
        val end = rx.indexOf(">")
        if (end < 0) return
        val reply = rx.substring(0, end)
        rx.delete(0, end + 1)
        val cmd = current ?: return
        current = null
        val clean = reply.replace('\r', ' ').replace('\n', ' ').trim()
        val compact = clean.replace(" ", "").uppercase()
        when (cmd) {
            "0100" -> ecuAnswered = compact.contains("4100")
            "012F" -> fuelLevelSupported = compact.contains("412F")
            "015E" -> fuelRateSupported = compact.contains("415E")
            "01A6" -> {
                val payload = compact.substringAfter("41A6", "").take(8)
                if (payload.length == 8) {
                    val raw = payload.toLongOrNull(16)
                    if (raw != null) {
                        odometerKm = raw / 10.0
                        odometerSupported = true
                    }
                }
            }
        }
        sendNext()
    }

    private fun finishInit() {
        current = null
        if (!ecuAnswered) {
            onState(
                ObdState.ADAPTER_READY,
                "Bluetooth adapter connected, but the bike ECU did not answer OBD-II request 0100. " +
                    "Odometer and fuel readings are unavailable. Use manual odometer/fuel entry or a bike-compatible diagnostic adapter."
            )
            return
        }

        val findings = buildList {
            add("ECU responded")
            add(if (odometerSupported) "Odometer: ${String.format("%.1f", odometerKm)} km" else "Odometer PID 01A6: unsupported/no response")
            add(if (fuelLevelSupported) "Fuel level PID 012F: supported" else "Fuel level PID 012F: unsupported/no response")
            add(if (fuelRateSupported) "Fuel rate PID 015E: supported" else "Fuel rate PID 015E: unsupported/no response")
        }
        val anyLiveData = odometerSupported || fuelLevelSupported || fuelRateSupported
        onState(
            ObdState.ECU_READY,
            if (anyLiveData) "${connectedDeviceName}: ${findings.joinToString(" • ")}. Unsupported values will not be fabricated."
            else "${connectedDeviceName}: ECU responded, but no supported odometer/fuel PIDs were returned. Manual entry remains available."
        )
    }

    // --------------------------------------------------------------- GATT callback

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g !== gatt) { try { g.close() } catch (_: Exception) {}; return }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                report(ObdState.ERROR, "BLE connection error (status $status).")
                teardown()
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                report(ObdState.DISCOVERING, "Connected; discovering adapter services…")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                teardown()
                report(ObdState.DISCONNECTED, "Disconnected")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                report(ObdState.ERROR, "Service discovery failed (status $status).")
                return
            }
            var write: BluetoothGattCharacteristic? = null
            var notify: BluetoothGattCharacteristic? = null
            for (i in knownServices.indices) {
                val s = g.getService(knownServices[i]) ?: continue
                val w = s.getCharacteristic(knownTx[i])
                val n = s.getCharacteristic(knownRx[i])
                if (w != null && n != null) { write = w; notify = n; break }
            }
            if (write == null || notify == null) {
                val writeMask = BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                val notifyMask = BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE
                for (s in g.services) {
                    val w = s.characteristics.firstOrNull { it.properties and writeMask != 0 }
                    val n = s.characteristics.firstOrNull { it.properties and notifyMask != 0 }
                    if (w != null && n != null) { write = w; notify = n; break }
                }
            }
            if (write == null || notify == null) {
                report(ObdState.ERROR, "Connected, but no serial (UART) characteristic pair was found on this device.")
                return
            }
            tx = write
            g.setCharacteristicNotification(notify, true)
            val descriptor = notify.getDescriptor(cccd)
            if (descriptor == null) {
                main.post { startInit() }
                return
            }
            val enable = if (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(descriptor, enable)
            } else {
                @Suppress("DEPRECATION") descriptor.value = enable
                @Suppress("DEPRECATION") g.writeDescriptor(descriptor)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (g !== gatt || descriptor.uuid != cccd) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                report(ObdState.ERROR, "Could not enable adapter notifications (status $status).")
                return
            }
            main.post { startInit() }
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") val v = c.value ?: return
            val text = String(v, Charsets.US_ASCII)
            main.post { onData(text) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            val text = String(value, Charsets.US_ASCII)
            main.post { onData(text) }
        }
    }
}