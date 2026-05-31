package hu.mobilparkolas.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mobilparkolas.di.ServiceLocator
import hu.mobilparkolas.domain.model.Car
import hu.mobilparkolas.domain.sms.SmsMode
import hu.mobilparkolas.domain.sms.SmsProvider
import hu.mobilparkolas.ui.cars.CarsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(locator: ServiceLocator, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(locator.settingsRepository, locator.carrierDetector)
    )
    val carsVm: CarsViewModel = viewModel(factory = CarsViewModel.Factory(locator.carRepository))

    val settings by vm.settings.collectAsStateWithLifecycle()
    val cars by carsVm.cars.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectableProviders = SmsProvider.entries.filter { it.supportsStop }

    var showAddCar by remember { mutableStateOf(false) }
    if (showAddCar) {
        AddCarDialog(
            onDismiss = { showAddCar = false },
            onAdd = { plate, name, makeDefault ->
                carsVm.add(plate, name, makeDefault || cars.isEmpty())
                showAddCar = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beállítások") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- Cars ----
            Text("Autók", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (cars.isEmpty()) {
                Text(
                    "Még nincs autó. Adj hozzá egyet a parkolás indításához.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            cars.forEach { car ->
                CarRow(car, onSetDefault = { carsVm.setDefault(car) }, onDelete = { carsVm.delete(car) })
            }
            OutlinedButton(onClick = { showAddCar = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Új autó hozzáadása")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // ---- Return-to-vehicle detection ----
            CarReturnSection(locator)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // ---- SMS mode ----
            Text("SMS-küldés módja", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            vm.carrier?.let { carrier ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Észlelt SIM-szolgáltató: ${carrier.name}", fontWeight = FontWeight.Bold)
                        if (carrier.provider != null) {
                            OutlinedButton(onClick = { vm.applyDetectedProvider() }, modifier = Modifier.padding(top = 4.dp)) {
                                Text("Beállítás: ${carrier.provider.displayName}")
                            }
                        } else {
                            Text(
                                "Ehhez a hálózathoz nincs használható szolgáltató-specifikus szám — javasolt a központi mód.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Column(Modifier.selectableGroup()) {
                RadioRow(
                    selected = settings.smsMode == SmsMode.CENTRAL,
                    title = "Központi (NMFR)",
                    subtitle = "+36 30 344 4805 — „ZÓNA RENDSZÁM\". Minden SIM-mel működik.",
                    onClick = { vm.setMode(SmsMode.CENTRAL) },
                )
                RadioRow(
                    selected = settings.smsMode == SmsMode.PROVIDER,
                    title = "Szolgáltató-specifikus",
                    subtitle = "A szám utolsó 4 jegye a zónakód, csak a rendszám megy.",
                    onClick = { vm.setMode(SmsMode.PROVIDER) },
                )
            }

            if (settings.smsMode == SmsMode.PROVIDER) {
                Text("Szolgáltató", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Column(Modifier.selectableGroup()) {
                    selectableProviders.forEach { p ->
                        RadioRow(
                            selected = settings.provider == p,
                            title = p.displayName,
                            subtitle = "${p.numberPrefix} + zónakód",
                            onClick = { vm.setProvider(p) },
                        )
                    }
                }
                Text(
                    "A One Magyarország nem támogatja a leállító (STOP) SMS-t, ezért nem választható — One SIM esetén használd a központi módot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ---- Footer ----
            val context = LocalContext.current
            val version = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull().orEmpty()
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(
                "© 2026 Dr. Molnár Vince — Minden jog fenntartva" + if (version.isNotBlank()) "\nv$version" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun AddCarDialog(onDismiss: () -> Unit, onAdd: (String, String, Boolean) -> Unit) {
    var plate by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var makeDefault by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Új autó") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it },
                    label = { Text("Rendszám") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Név (pl. Családi autó)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = makeDefault,
                        onClick = { makeDefault = !makeDefault },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = makeDefault, onCheckedChange = { makeDefault = it })
                    Text("Alapértelmezett autó")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(plate, name, makeDefault) }, enabled = plate.isNotBlank()) {
                Text("Hozzáadás")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Mégse") }
        },
    )
}

@Composable
private fun CarRow(car: Car, onSetDefault: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onSetDefault) {
                Icon(
                    if (car.isDefault) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Alapértelmezett",
                    tint = if (car.isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(car.plate, fontWeight = FontWeight.Bold)
                if (car.name.isNotBlank()) Text(car.name, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Törlés")
            }
        }
    }
}

@Composable
private fun RadioRow(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
