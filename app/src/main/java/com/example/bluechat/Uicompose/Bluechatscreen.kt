package com.example.bluechat.Uicompose

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bluechat.BluetoothConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val SCREEN_TAG = "ChatScreen" 

data class ChatMessage(val message: String, val isSentByUser: Boolean, val timestamp: Long = System.currentTimeMillis())

@SuppressLint("MissingPermission") 
@Composable
fun ChatScreen(
    bluetoothAdapter: BluetoothAdapter,
    isServer: Boolean,
    serverDeviceAddress: String? = null, 
    onDisconnect: () -> Unit, 
) {
    var messageToSend by remember { mutableStateOf("") }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    var connectionStatus by remember { mutableStateOf("Initializing...") }
    var bluetoothSocket by remember { mutableStateOf<BluetoothSocket?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var connectionJob by remember { mutableStateOf<Job?>(null) }
    var listenerJob by remember { mutableStateOf<Job?>(null) }

    fun updateStatus(status: String) {
        Log.d(BluetoothConstants.TAG, "$SCREEN_TAG: Status - $status")
        connectionStatus = status
    }

    fun addMessage(message: String, isSent: Boolean) {
        chatMessages.add(ChatMessage(message, isSent))
    }

    fun cleanupConnection() {
        Log.d(BluetoothConstants.TAG, "$SCREEN_TAG: Cleaning up connection.")
        listenerJob?.cancel() 
        connectionJob?.cancel() 
        try {
            bluetoothSocket?.close() 
        } catch (e: IOException) {
            Log.e(BluetoothConstants.TAG, "$SCREEN_TAG: Error closing socket on cleanup", e)
        }
        bluetoothSocket = null 

    }


    DisposableEffect(key1 = isServer, key2 = serverDeviceAddress) {

        cleanupConnection() 
        updateStatus(if (isServer) "Starting server..." else "Connecting to ${serverDeviceAddress ?: "server"}...")

        connectionJob = coroutineScope.launch(Dispatchers.IO) {
            var tempSocket: BluetoothSocket? = null 
            try {
                if (isServer) {
                    var serverSocket: BluetoothServerSocket? = null
                    try {
                        serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                            BluetoothConstants.SERVICE_NAME,
                            BluetoothConstants.MY_UUID
                        )
                        Log.d(BluetoothConstants.TAG, "$SCREEN_TAG: Server socket opened. Waiting for client...")
                        withContext(Dispatchers.Main) { updateStatus("Waiting for client...") }
                        tempSocket = serverSocket.accept() 
                        Log.d(BluetoothConstants.TAG, "$SCREEN_TAG: Client connected: ${tempSocket?.remoteDevice?.address}")
                    } catch (e: IOException) {
                        if (isActive) { 
                            Log.e(BluetoothConstants.TAG, "$SCREEN_TAG: Server: Socket listen/accept failed or closed.", e)
                            withContext(Dispatchers.Main) { updateStatus("Server failed: ${e.message}") }
                        }
                        return@launch 
                    } finally {
                        try {
                            serverSocket?.close()
                        } catch (e: IOException) {
                            Log.e(BluetoothConstants.TAG, "$SCREEN_TAG: Server: Could not close server socket", e)
                        }
                    }
                } else { 
                    if (serverDeviceAddress.isNullOrBlank()) {
                        Log.e(BluetoothConstants.TAG, "$SCREEN_TAG: Client: Server MAC address is missing.")
                        withContext(Dispatchers.Main) { updateStatus("Error: Server MAC address missing.") }
                        return@launch
                    }
                    val device: BluetoothDevice? = bluetoothAdapter.getRemoteDevice(serverDeviceAddress)
                    if (device == null) {
                        Log.e(BluetoothConstants.TAG, "$SCREEN_TAG: Client: Could not get remote device: $serverDeviceAddress")
                        withContext(Dispatchers.Main) { updateStatus("Error: Could not find device.") }
                        return@launch
                    }

                    try {
                        Log.d(BluetoothConstants.TAG, "$SCREEN_TAG: Client: Creating RFCOMM socket to ${device.name} (${device.address})")
                        tempSocket = device.createRfcommSocketToServiceRecord(BluetoothConstants.MY_UUID)
                        Log.d(BluetoothConstants.TAG, "$SCREEN_TAG: Client: Connecting...")
                        withContext(Dispatchers.Main) { updateStatus("Connecting to ${device.name ?: device.address}...") }
                        tempSocket.connect() 
                        Log.d(BluetoothConstants.TAG, "$SCREEN_TAG: Client: Connected to server!")
                    } catch (e: IOException) {
                        if (isActive) {
                            Log.e(BluetoothConstants.TAG, "$SCREEN_TAG: Client: Connection failed.", e)
                            withContext(Dispatchers.Main) { updateStatus("Client connection failed: ${e.message}") }
                        }
                        try { tempSocket?.close() } catch (ex: IOException) {
                            Log.e(BluetoothConstants.TAG, "$SCREEN_TAG: Client: Failed to close temp socket after connection error", ex)
                        }
                        return@launch
                    }
                }


                if (tempSocket != null && isActive) {
                    withContext(Dispatchers.Main) {
                        bluetoothSocket = tempSocket 
                        updateStatus("Connected to ${tempSocket.remoteDevice.name ?: tempSocket.remoteDevice.address}")
                    }


                    listenerJob = launch(Dispatchers.IO) { 
                        Log.d(BluetoothConstants.TAG, "$SCREEN_TAG: Listener job started for socket.")
                        val currentSocket = tempSocket 
                        val inputStream: InputStream = currentSocket.inputStream
                        val buffer = ByteArray(1024)
                        var bytes: Int

                        while (isActive) {
                            try {
                                bytes = inputStream.read(buffer)
                                if (bytes > 0) {
                                    val incomingMessage = String(buffer, 0, bytes)
                                    Log.d(BluetoothConstants.TAG, "$SCREEN_TAG: Received: $incomingMessage")
                                    withContext(Dispatchers.Main) {
                                        addMessage("Them: $incomingMessage", false)
                                    }
                                } else {
                                    Log.d(BluetoothConstants.TAG, "$SCREEN_TAG: Input stream read -1, connection closed by remote.")
                                    break 
                                }
                            } catch (e: IOException) {
                                if (isActive) {
                                    Log.e(BluetoothConstants.TAG, "$SCREEN_TAG: Input stream was disconnected.", e)
                                }
                                break
                            }
                        }

                        if (isActive) {
                            withContext(Dispatchers.Main) {
                                updateStatus("Connection lost.")
                                cleanupConnection()
                                onDisconnect()
                            }
                        }
                    }
                } else if (isActive && tempSocket == null) {
                    withContext(Dispatchers.Main) { updateStatus("Failed to establish connection (tempSocket is null).") }
                }

            } catch (e: SecurityException) {
                if (isActive) {
                    Log.e(BluetoothConstants.TAG, "$SCREEN_TAG: Bluetooth permission missing.", e)
                    withContext(Dispatchers.Main) { updateStatus("Permission Error: ${e.message}") }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(BluetoothConstants.TAG, "$SCREEN_TAG: Connection establishment error", e)
                    withContext(Dispatchers.Main) { updateStatus("Error: ${e.message}") }
                }
            } finally {

                if (bluetoothSocket == null && tempSocket != null) {
                    try {
                        tempSocket.close()
                        Log.d(BluetoothConstants.TAG, "$SCREEN_TAG: Temp socket (from connectionJob) closed in finally block.")
                    } catch (e: IOException) {
                        Log.e(BluetoothConstants.TAG, "$SCREEN_TAG: Error closing temp socket in connectionJob finally block", e)
                    }
                }


            }
        }


        onDispose {
            Log.d(BluetoothConstants.TAG, "$SCREEN_TAG: Disposing ChatScreen Composable. Cleaning up connection.")
            cleanupConnection()
        }
    }

    fun sendMessage() {
        val socket = bluetoothSocket 
        val message = messageToSend
        if (socket == null || !socket.isConnected || message.isBlank()) {
            if (socket == null || !socket.isConnected) {
                updateStatus("Not connected. Cannot send message.")
                Log.w(BluetoothConstants.TAG, "$SCREEN_TAG: Attempted to send message but not connected.")
            }
            if (message.isBlank()) {
                Log.w(BluetoothConstants.TAG, "$SCREEN_TAG: Attempted to send empty message.")
            }
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val outputStream: OutputStream = socket.outputStream
                outputStream.write(message.toByteArray())
                outputStream.flush() 
                Log.d(BluetoothConstants.TAG, "$SCREEN_TAG: Sent: $message")
                withContext(Dispatchers.Main) {
                    addMessage("Me: $message", true)
                    messageToSend = "" 
                }
            } catch (e: IOException) {
                Log.e(BluetoothConstants.TAG, "$SCREEN_TAG: Error sending message", e)
                withContext(Dispatchers.Main) {
                    updateStatus("Error sending: ${e.message}")
                    
                    
                    
                }
            }
        }
    }

    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Status: $connectionStatus")
        Text(if (isServer) "Mode: Server" else "Mode: Client (to ${serverDeviceAddress ?: "N/A"})")
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(), 
            reverseLayout = true 
        ) {
            items(chatMessages.reversed()) { chat -> 
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp), 
                    horizontalArrangement = if (chat.isSentByUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface( 
                        shape = MaterialTheme.shapes.medium,
                        color = if (chat.isSentByUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = 1.dp
                    ) {
                        Text(
                            text = chat.message,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = messageToSend,
                onValueChange = { messageToSend = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Enter message") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { sendMessage() },
                enabled = bluetoothSocket?.isConnected == true && messageToSend.isNotBlank()
            ) {
                Text("Send")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                Log.d(BluetoothConstants.TAG, "$SCREEN_TAG: Manual disconnect initiated by user.")
                
                onDisconnect() 
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disconnect and Go Back")
        }
    }
}
