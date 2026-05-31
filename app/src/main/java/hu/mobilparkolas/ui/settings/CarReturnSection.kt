package hu.mobilparkolas.ui.settings

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import hu.mobilparkolas.detect.CarDevice
import hu.mobilparkolas.di.ServiceLocator

@Composable
fun CarReturnSection(locator: ServiceLocator) {
    val context = LocalContext.current
    val store = locator.carDeviceStore

    var aaEnabled by remember { mutableStateOf(store.androidAutoEnabled) }
    var devices by remember { mutableStateOf(store.devices()) }
    var bonded by remember { mutableStateOf<List<CarDevice>>(emptyList()) }
    var showPicker by remember { mutableStateOf(false) }

    fun loadBonded() {
        bonded = try {
            val mgr = context.getSystemService(BluetoothManager::class.java)
            mgr?.adapter?.bondedDevices?.map { CarDevice(it.address, it.name ?: it.address) }
                ?.sortedBy { it.name.lowercase() } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) { loadBonded(); showPicker = true }
    }

    fun openPicker() {
        val needsRuntime = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val granted = !needsRuntime || ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) { loadBonded(); showPicker = true } else permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    Text("Visszatérés-értesítés", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "Parkolás közben értesítünk, amikor visszatérsz a járműhöz, hogy egy koppintással leállíthasd.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Android Auto észlelése")
            Text(
                "Az indítás után 15 perccel észlelt Android Auto-kapcsolat is jelez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = aaEnabled, onCheckedChange = { aaEnabled = it; store.androidAutoEnabled = it })
    }

    Text("Autós Bluetooth-eszközök", modifier = Modifier.padding(top = 8.dp))
    if (devices.isEmpty()) {
        Text(
            "Nincs mentett eszköz.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    devices.forEach { d ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(d.name)
                    Text(d.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { store.removeDevice(d.address); devices = store.devices() }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Törlés")
                }
            }
        }
    }
    OutlinedButton(onClick = { openPicker() }, modifier = Modifier.fillMaxWidth()) {
        Text("Autós Bluetooth-eszköz hozzáadása")
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Párosított eszközök") },
            text = {
                if (bonded.isEmpty()) {
                    Text("Nincs párosított Bluetooth-eszköz, vagy nincs engedély.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        itemsIndexed(bonded) { index, d ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        store.addDevice(d); devices = store.devices(); showPicker = false
                                    }
                                    .padding(vertical = 14.dp),
                            ) {
                                Text(d.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    d.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (index < bonded.lastIndex) HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Mégse") } },
        )
    }
}
