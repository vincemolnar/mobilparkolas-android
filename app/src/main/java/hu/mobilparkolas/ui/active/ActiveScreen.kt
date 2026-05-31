package hu.mobilparkolas.ui.active

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mobilparkolas.di.ServiceLocator
import hu.mobilparkolas.domain.model.LatLng
import hu.mobilparkolas.domain.model.ParkingSession
import hu.mobilparkolas.ui.sms.SmsLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveScreen(locator: ServiceLocator, onBack: () -> Unit) {
    val vm: ActiveViewModel = viewModel(
        factory = ActiveViewModel.Factory(locator.parkingRepository, locator.settingsRepository, locator.parkingNotifier, locator.returnDetection)
    )
    val session by vm.active.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showDiscard by remember { mutableStateOf(false) }

    // Ticking clock so the pending→running transition and the elapsed time stay live.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { now = LocalDateTime.now(); delay(1000) }
    }
    val pending = session?.isPending(now) == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (pending) "Ütemezett parkolás" else "Folyamatban lévő parkolás") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val s = session
            if (s == null) {
                Text("Nincs folyamatban lévő parkolás.")
            } else {
                ActiveCard(s, now)
                OutlinedButton(
                    onClick = { SmsLauncher.navigateTo(context, LatLng(s.lat, s.lng)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Navigation, contentDescription = null)
                    Text("  Navigálj oda")
                }
                Button(
                    onClick = {
                        scope.launch {
                            val plan = vm.prepareStop(s)
                            if (plan.supportsStop) {
                                SmsLauncher.send(context, plan)
                            } else {
                                snackbar.showSnackbar("A választott szolgáltatónál nincs STOP SMS — a parkolást helyileg lezárom.")
                            }
                            vm.recordStop(s)
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(if (pending) Icons.Filled.Close else Icons.Filled.Stop, contentDescription = null)
                    Text(if (pending) "  Ütemezés visszavonása" else "  Parkolás leállítása")
                }
                TextButton(
                    onClick = { showDiscard = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Nem indítottam el")
                }

                if (showDiscard) {
                    AlertDialog(
                        onDismissRequest = { showDiscard = false },
                        title = { Text("Nem indítottad el?") },
                        text = {
                            Text(
                                "Az alkalmazás innentől nem követi ezt a parkolást, és nem küld STOP üzenetet. " +
                                    "Ha mégis elindítottad, neked kell leállítanod a STOP üzenettel. Biztos vagy benne?"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showDiscard = false; vm.discard(s); onBack() }) { Text("Igen") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDiscard = false }) { Text("Nem") }
                        },
                    )
                }
            }
        }
    }
}

private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
private fun ActiveCard(s: ParkingSession, now: LocalDateTime) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.city, style = MaterialTheme.typography.titleMedium)
            Text("Zóna: ${s.zoneCode}   Rendszám: ${s.plate}", fontWeight = FontWeight.Bold)

            if (s.isPending(now)) {
                Text("Ütemezve — a parkolás ${s.scheduledStart?.format(FMT)}-kor indul.")
                Text(
                    "Az SMS-t a rendszer a fizetős időszak kezdetén dolgozza fel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Indítva: ${s.effectiveStart.format(FMT)}")
                Text(
                    formatElapsed(Duration.between(s.effectiveStart, now).seconds.coerceAtLeast(0)),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Text(
                "Parkolás helye: ${"%.5f".format(s.lat)}, ${"%.5f".format(s.lng)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatElapsed(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
