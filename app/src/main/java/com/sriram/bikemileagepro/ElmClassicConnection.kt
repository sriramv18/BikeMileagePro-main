package com.sriram.bikemileagepro

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Shared reactive holder for live fuel consumption readings pulled over Bluetooth OBD.
 */
object ObdFuelStore {
    var latestFuelReading by mutableStateOf("")
}

/**
 * Classic Bluetooth (SPP / RFCOMM) transport for standard ELM327 OBD adapters
 * connected via Android's native Bluetooth settings.
 * Automatically queries fuel level/consumption upon connection and registers the vehicle.
 */
class ElmClassicConnection(
    private val context: Context,
    private val db: TripDatabase? = null,
    private val onState: (ObdState, String, String?) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false

    private fun report(state: ObdState, message: String, fuelConsumed: String? = null) {
        main.post { onState(state, message, fuelConsumed) }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        disconnect()
        val deviceName = try { device.name } catch (_: SecurityException) { null } ?: "OBD Device"
        report(ObdState.CONNECTING, "Connecting via Bluetooth to $deviceName...")

        executor.execute {
            try {
                // Standard SPP UUID for ELM327 / OBD-II adapters
                val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                val tmpSocket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket = tmpSocket

                tmpSocket.connect()
                inputStream = tmpSocket.inputStream
                outputStream = tmpSocket.outputStream
                isConnected = true

                report(ObdState.DISCOVERING, "Connected. Running ELM327 initialization...")

                if (runInitSequence(deviceName)) {
                    checkAndAutoAddVehicle(deviceName)
                }
            } catch (e: Exception) {
                disconnect()
                report(ObdState.ERROR, "Connection failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun disconnect() {
        isConnected = false
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
        report(ObdState.DISCONNECTED, "Disconnected", null)
    }

    private fun writeCommand(cmd: String): String {
        val out = outputStream ?: return ""
        val inp = inputStream ?: return ""
        try {
            out.write((cmd + "\r").toByteArray(Charsets.US_ASCII))
            out.flush()

            val sb = StringBuilder()
            val buffer = ByteArray(1024)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < 5000) {
                if (inp.available() > 0) {
                    val bytesRead = inp.read(buffer)
                    if (bytesRead > 0) {
                        val text = String(buffer, 0, bytesRead, Charsets.US_ASCII)
                        sb.append(text)
                        if (sb.contains(">")) break
                    }
                }
                Thread.sleep(50)
            }
            return sb.toString().trim()
        } catch (_: Exception) {
            return ""
        }
    }

    private fun runInitSequence(deviceName: String): Boolean {
        val cmds = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "0100")
        var ecuAnswered = false

        for (cmd in cmds) {
            if (!isConnected) return false
            val reply = writeCommand(cmd)
            if (cmd == "ATZ" && reply.isBlank()) {
                report(ObdState.ERROR, "Device connected, but did not answer ELM327 ATZ command.")
                return false
            }
            if (cmd == "0100") {
                ecuAnswered = reply.replace(" ", "").contains("4100")
            }
        }

        // Read fuel consumption/level right after initialization succeeds
        val fuelReading = if (ecuAnswered) readFuelConsumption() else null
        if (fuelReading != null) {
            ObdFuelStore.latestFuelReading = fuelReading
        }

        if (ecuAnswered) {
            report(
                ObdState.ECU_READY,
                "Connected to $deviceName — ECU ready & fuel data synced.",
                fuelReading
            )
        } else {
            report(
                ObdState.ADAPTER_READY,
                "Connected to $deviceName — Adapter ready (GPS trip tracking enabled).",
                fuelReading
            )
        }
        return true
    }

    private fun readFuelConsumption(): String? {
        // Query OBD-II PID 012F (Fuel Tank Level Input %)
        val levelReply = writeCommand("012F").replace(" ", "")
        if (levelReply.contains("412F")) {
            val hexVal = levelReply.substringAfter("412F").take(2)
            val raw = hexVal.toIntOrNull(16)
            if (raw != null) {
                val pct = (raw * 100.0) / 255.0
                return String.format("%.1f", pct)
            }
        }

        // Fallback: Query OBD-II PID 015E (Engine Fuel Rate in L/h)
        val rateReply = writeCommand("015E").replace(" ", "")
        if (rateReply.contains("415E")) {
            val hexVal = rateReply.substringAfter("415E").take(4)
            val raw = hexVal.toIntOrNull(16)
            if (raw != null) {
                val litersPerHour = raw / 20.0
                return String.format("%.2f", litersPerHour)
            }
        }

        return null
    }

    private fun checkAndAutoAddVehicle(deviceName: String) {
        val database = db ?: return
        try {
            val vehicles = database.vehicles()
            val match = vehicles.any { it.name.equals(deviceName, ignoreCase = true) }
            if (!match && deviceName.isNotBlank()) {
                database.addVehicle(
                    name = deviceName,
                    year = "2026",
                    reg = "",
                    variant = "Bluetooth OBD",
                    odo = "0"
                )
            }
        } catch (_: Exception) {}
    }
}
