package com.example.bluechat.Logic

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.UUID
import kotlin.concurrent.thread

class ClientServer(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val deviceAddress: String,
    private val onMessageReceived: (String) -> Unit
) {
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null

    fun connect() {
        thread {
            try {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.e("ClientServer", "BLUETOOTH_CONNECT permission not granted")
                        return@thread
                    }
                }

                val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)
                socket = device.createRfcommSocketToServiceRecord(uuid)

                bluetoothAdapter.cancelDiscovery()
                socket?.connect()
                Log.d("ClientServer", "Connected to server")

                val input = socket?.inputStream
                val buffer = ByteArray(1024)

                while (true) {
                    val bytes = input?.read(buffer) ?: break
                    val msg = String(buffer, 0, bytes)
                    Log.d("ClientServer", "Received: $msg")
                    onMessageReceived(msg)
                }

            } catch (e: Exception) {
                Log.e("ClientServer", "Error: ${e.message}")
            }
        }
    }

    fun sendMessage(message: String) {
        try {
            val output = socket?.outputStream ?: return
            output.write(message.toByteArray())
            output.flush()
            Log.d("ClientServer", "Sent: $message")
        } catch (e: Exception) {
            Log.e("ClientServer", "Send error: ${e.message}")
        }
    }
}
