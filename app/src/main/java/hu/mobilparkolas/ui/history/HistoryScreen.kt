package hu.mobilparkolas.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mobilparkolas.di.ServiceLocator
import hu.mobilparkolas.domain.model.ParkingSession
import hu.mobilparkolas.domain.model.SessionStatus
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(locator: ServiceLocator, onBack: () -> Unit, onOpenActive: () -> Unit) {
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(locator.parkingRepository))
    val sessions by vm.history.collectAsStateWithLifecycle(initialValue = emptyList())
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Összes előzmény törlése") },
            text = { Text("Biztosan törlöd az összes parkolási előzményt?") },
            confirmButton = {
                TextButton(onClick = { vm.clearAll(); showClearConfirm = false }) { Text("Törlés") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Mégse") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parkolási előzmények") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
                    }
                },
                actions = {
                    if (sessions.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Összes törlése")
                        }
                    }
                },
            )
        },
    ) { pad ->
        if (sessions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(pad).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Még nincs parkolási előzmény.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sessions, key = { it.id }) { session ->
                    HistoryRow(
                        session = session,
                        onClick = if (session.status == SessionStatus.ACTIVE) onOpenActive else null,
                        onDelete = { vm.delete(session) },
                    )
                }
            }
        }
    }
}

private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
private fun HistoryRow(session: ParkingSession, onClick: (() -> Unit)?, onDelete: () -> Unit) {
    val now = LocalDateTime.now()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(session.city.ifBlank { "Ismeretlen város" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Zóna ${session.zoneCode} • ${session.plate}", style = MaterialTheme.typography.bodyMedium)
                StatusLine(session, now)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Törlés")
            }
        }
    }
}

@Composable
private fun StatusLine(s: ParkingSession, now: LocalDateTime) {
    when {
        s.status == SessionStatus.ACTIVE && s.isPending(now) -> Text(
            "Ütemezve • kezdés: ${s.scheduledStart?.format(FMT)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        s.status == SessionStatus.ACTIVE -> Text(
            "Folyamatban • ${formatDuration(s.effectiveStart, now)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        else -> Text(
            "${s.startedAt.format(FMT)} – ${s.stoppedAt?.format(FMT) ?: "?"}" +
                (s.stoppedAt?.let { "  (${formatDuration(s.effectiveStart, it)})" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDuration(start: LocalDateTime, end: LocalDateTime): String {
    val mins = Duration.between(start, end).toMinutes().coerceAtLeast(0)
    val h = mins / 60
    val m = mins % 60
    return if (h > 0) "$h ó $m p" else "$m p"
}
