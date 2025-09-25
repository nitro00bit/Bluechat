package com.example.bluechat 

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bluechat.Uicompose.ChatScreen


sealed class Screen {
    object RoleSelection : Screen()
    object DeviceList : Screen()
    data class Chat(val isServer: Boolean, val deviceAddress: String? = null) : Screen()
}

class MainActivity : ComponentActivity() {

    private val bluetoothManager: BluetoothManager by lazy {
        getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }

    private var currentScreen by mutableStateOf<Screen>(Screen.RoleSelection)

    
    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d(BluetoothConstants.TAG, "Bluetooth enabled by user.")
                
                
                
                
                
            } else {
                Log.w(BluetoothConstants.TAG, "Bluetooth not enabled by user.")
                showToast("Bluetooth is required to use the chat features.")
                
                if (currentScreen !is Screen.RoleSelection) {
                    currentScreen = Screen.RoleSelection
                }
            }
        }

    
    private val requestBluetoothPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.entries.all { it.value }
            if (allPermissionsGranted) {
                Log.d(BluetoothConstants.TAG, "All Bluetooth permissions granted.")
                
                
                
            } else {
                Log.w(BluetoothConstants.TAG, "Not all Bluetooth permissions were granted.")
                showToast("Bluetooth permissions are required for chat.")
                
                if (currentScreen !is Screen.RoleSelection) {
                    currentScreen = Screen.RoleSelection
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (bluetoothAdapter == null) {
            showToast("Bluetooth is not supported on this device.")
            
            setContent {
                MaterialTheme { Surface { Text("Bluetooth Not Supported", style = MaterialTheme.typography.headlineMedium) } }
            }
            return
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppNavigation()
                }
            }
        }
    }

    private fun checkAndRequestBluetoothPermissions(onPermissionsGranted: () -> Unit) {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                
                
                
            )
        } else {
            
            
            
            
            
            emptyArray()
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            Log.d(BluetoothConstants.TAG, "All required Bluetooth permissions already granted.")
            onPermissionsGranted()
        } else {
            Log.d(BluetoothConstants.TAG, "Requesting missing Bluetooth permissions: ${missingPermissions.joinToString()}")
            requestBluetoothPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }


    private fun ensureBluetoothEnabled(onEnabled: () -> Unit) {
        if (bluetoothAdapter?.isEnabled == false) {
            Log.d(BluetoothConstants.TAG, "Bluetooth is disabled. Requesting user to enable.")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
            
            
            
        } else if (bluetoothAdapter?.isEnabled == true) {
            Log.d(BluetoothConstants.TAG, "Bluetooth is already enabled.")
            onEnabled()
        } else {
            Log.e(BluetoothConstants.TAG, "Bluetooth adapter is null or in an unknown state.")
            showToast("Cannot access Bluetooth adapter.")
        }
    }

    @Composable
    fun MainAppNavigation() {
        when (val screen = currentScreen) {
            is Screen.RoleSelection -> RoleSelectionScreen(
                onStartServer = {
                    ensureBluetoothEnabled {
                        checkAndRequestBluetoothPermissions {
                            currentScreen = Screen.Chat(isServer = true, deviceAddress = null)
                        }
                    }
                },
                onStartClient = {
                    ensureBluetoothEnabled {
                        checkAndRequestBluetoothPermissions {
                            
                            
                            
                            currentScreen = Screen.DeviceList
                        }
                    }
                }
            )
            is Screen.DeviceList -> {
                if (bluetoothAdapter != null) {
                    DeviceListScreen(
                        bluetoothAdapter = bluetoothAdapter!!, 
                        onDeviceSelected = { device ->
                            currentScreen = Screen.Chat(isServer = false, deviceAddress = device.address)
                        },
                        onNavigateBack = { currentScreen = Screen.RoleSelection }
                    )
                } else {
                    ErrorScreen("Bluetooth Adapter not available.") {
                        currentScreen = Screen.RoleSelection
                    }
                }
            }
            is Screen.Chat -> {
                if (bluetoothAdapter != null) {
                    ChatScreen(
                        bluetoothAdapter = bluetoothAdapter!!, 
                        isServer = screen.isServer,
                        serverDeviceAddress = screen.deviceAddress,
                        onDisconnect = {
                            
                            currentScreen = Screen.RoleSelection
                            showToast("Disconnected from chat.")
                        }
                    )
                } else {
                    ErrorScreen("Bluetooth Adapter became unavailable.") {
                        currentScreen = Screen.RoleSelection
                    }
                }
            }
        }
    }

    @Composable
    fun RoleSelectionScreen(onStartServer: () -> Unit, onStartClient: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = onStartServer) { Text("Start as Server") }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onStartClient) { Text("Start as Client (Choose Device)") }
        }
    }

    @SuppressLint("MissingPermission") 
    @Composable
    fun DeviceListScreen(
        bluetoothAdapter: BluetoothAdapter,
        onDeviceSelected: (BluetoothDevice) -> Unit,
        onNavigateBack: () -> Unit,
    ) {
        val pairedDevices = try {
            bluetoothAdapter.bondedDevices.toList()
        } catch (e: SecurityException) {
            Log.e(BluetoothConstants.TAG, "SecurityException getting bonded devices: ${e.message}")
            showToast("Bluetooth permission (CONNECT) might be missing for bonded devices.")
            emptyList<BluetoothDevice>()
        }

        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) {
            Text("Select a Paired Device:", style = MaterialTheme.typography.headlineSmall)
            if (pairedDevices.isEmpty()) {
                Text("No paired devices found. Please pair devices in system Bluetooth settings.")
            } else {
                LazyColumn {
                    items(pairedDevices) { device ->
                        ListItem(
                            headlineContent = { Text(device.name ?: "Unknown Device") },
                            supportingContent = { Text(device.address) },
                            modifier = Modifier.clickable { onDeviceSelected(device) }
                        )
                        Divider()
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onNavigateBack) { Text("Back") }
        }
    }

    @Composable
    fun ErrorScreen(message: String, onDismiss: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss) { Text("OK") }
            }
        }
    }


    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
}
