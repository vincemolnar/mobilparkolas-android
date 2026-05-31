package hu.mobilparkolas.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.mobilparkolas.MobilParkolasApp
import hu.mobilparkolas.domain.model.Car
import hu.mobilparkolas.ui.theme.MobilParkolasTheme

class WidgetConfigActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Default result: if the user backs out, the widget is not added.
        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        val locator = (application as MobilParkolasApp).locator

        setContent {
            MobilParkolasTheme {
                val cars by locator.carRepository.cars.collectAsStateWithLifecycle(initialValue = emptyList())
                var selectedCarId by remember { mutableStateOf<Long?>(null) }

                Scaffold(topBar = { TopAppBar(title = { Text("Widget — melyik autó?") }) }) { pad ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "A widget egy koppintással elindítja a parkolást ezzel az autóval.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Column(Modifier.selectableGroup().fillMaxWidth()) {
                            CarChoiceRow("Alapértelmezett autó", selectedCarId == null) { selectedCarId = null }
                            cars.forEach { car ->
                                CarChoiceRow(
                                    label = "${car.plate}" + if (car.name.isNotBlank()) " — ${car.name}" else "",
                                    selected = selectedCarId == car.id,
                                ) { selectedCarId = car.id }
                            }
                        }
                        Button(
                            onClick = { confirm(appWidgetId, selectedCarId) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Mentés") }
                    }
                }
            }
        }
    }

    private fun confirm(appWidgetId: Int, carId: Long?) {
        WidgetPrefs.setCar(this, appWidgetId, carId)
        ParkingWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), appWidgetId)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}

@Composable
private fun CarChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}
