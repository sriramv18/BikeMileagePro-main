package com.sriram.bikemileagepro

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import java.util.UUID

/** Basic BLE GATT transport for common Nordic UART and HM-10 style ELM BLE adapters. */
class ElmBleConnection(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onLine: (String) -> Unit
) {
    private var gatt: BluetoothGatt? = null
    private var tx: BluetoothGattCharacteristic? = null
    private val input = StringBuilder()
    private val serviceIds = listOf(
        UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"),
        UUID.fromString("FFE0-0000-1000-8000-00805F9B34FB")
    )
    private val txIds = listOf(
        UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E"),
        UUID.fromString("FFE1-0000-1000-8000-00805F9B34FB")
    )
    private val rxIds = listOf(
        UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"),
        UUID.fromString("FFE1-0000-1000-8000-00805F9B34FB")
    )
    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        disconnect()
        onStatus("Connecting to ${device.name ?: device.address}…")
        gatt = if (Build.VERSION.SDK_INT >= 23)
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        else device.connectGatt(context, false, callback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try { gatt?.disconnect(); gatt?.close() } catch (_: SecurityException) {}
        gatt = null
        tx = null
        onStatus("Disconnected")
    }

    @SuppressLint("MissingPermission")
    fun send(command: String) {
        val current = gatt ?: return onStatus("Not connected")
        val characteristic = tx ?: return onStatus("No supported UART write characteristic")
        val bytes = (command.trim() + "\r").toByteArray(Charsets.US_ASCII)
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (Build.VERSION.SDK_INT >= 33) {
            val result = current.writeCharacteristic(characteristic, bytes, characteristic.writeType)
            if (result != BluetoothStatusCodes.SUCCESS) onStatus("Write failed: $result")
        } else {
            @Suppress("DEPRECATION") characteristic.value = bytes
            @Suppress("DEPRECATION")
            if (!current.writeCharacteristic(characteristic)) onStatus("Write request failed")
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onStatus("BLE connection error: $status")
                g.close()
                if (gatt == g) gatt = null
            } else if (state == BluetoothProfile.STATE_CONNECTED) {
                onStatus("Connected; discovering adapter services…")
                g.discoverServices()
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                onStatus("Disconnected")
                g.close()
                if (gatt == g) gatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return onStatus("Service discovery failed: $status")
            for (i in serviceIds.indices) {
                val service = g.getService(serviceIds[i]) ?: continue
                val write = service.getCharacteristic(txIds[i])
                val notify = service.getCharacteristic(rxIds[i])
                if (write != null && notify != null) {
                    tx = write
                    g.setCharacteristicNotification(notify, true)
                    val descriptor = notify.getDescriptor(cccd)
                    if (descriptor != null) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION") g.writeDescriptor(descriptor)
                        }
                    }
                    onStatus("ELM BLE connected — UART ready")
                    send("ATZ")
                    return
                }
            }
            onStatus("Connected, but adapter UART UUID layout is unsupported.")
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            c.value?.let(::consume)
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            consume(value)
        }
        private fun consume(bytes: ByteArray) {
            synchronized(input) {
                input.append(bytes.toString(Charsets.US_ASCII))
                while (true) {
                    val end = (0 until input.length).firstOrNull {
                        input[it] == '>' || input[it] == '\r' || input[it] == '\n'
                    } ?: break
                    val line = input.substring(0, end).trim()
                    input.delete(0, end + 1)
                    if (line.isNotEmpty()) onLine(line)
                }
            }
        }
    }
}
