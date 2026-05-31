package hu.mobilparkolas.ui.main

import android.Manifest
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.mobilparkolas.data.prefs.AppSettings
import hu.mobilparkolas.domain.geo.Geometry
import hu.mobilparkolas.domain.model.Car
import hu.mobilparkolas.domain.model.ParkingStatus
import hu.mobilparkolas.domain.model.ParkingZone
import hu.mobilparkolas.domain.sms.SmsComposer
import hu.mobilparkolas.ui.sms.SmsLauncher
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vm: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onParkingStarted: () -> Unit,
) {
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val center by vm.center.collectAsStateWithLifecycle()
    val zones by vm.zones.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val isReady by vm.isReady.collectAsStateWithLifecycle()
    val cars by vm.cars.collectAsStateWithLifecycle()
    val appSettings by vm.appSettings.collectAsStateWithLifecycle()
    var startDialogZone by remember { mutableStateOf<ParkingZone?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Expanded)
    )

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
        }
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        vm.onPermissionResult(granted)
    }
    LaunchedEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    LaunchedEffect(center) {
        center?.let { mapView.controller.animateTo(GeoPoint(it.lat, it.lng)) }
    }
    LaunchedEffect(zones, selectedId) {
        mapView.overlays.clear()
        mapView.overlays.add(
            MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply { enableMyLocation() }
        )
        zones.forEach { zone ->
            mapView.overlays.add(buildPolygon(zone, zone.id == selectedId) { vm.select(zone) })
        }
        mapView.invalidate()
    }

    fun launchStart(start: PreparedStart) {
        SmsLauncher.send(context, start.plan)
        vm.recordStart(start)
        onParkingStarted()
    }
    fun quickPark(zone: ParkingZone) {
        scope.launch {
            when (val outcome = vm.prepareQuickPark(zone)) {
                is QuickParkOutcome.Ready -> launchStart(outcome.start)
                QuickParkOutcome.NoDefaultCar -> {
                    val r = snackbar.showSnackbar("Nincs alapértelmezett autó.", actionLabel = "Beállítások")
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) onOpenSettings()
                }
                is QuickParkOutcome.Failed -> snackbar.showSnackbar("Hiba: ${outcome.message}")
            }
        }
    }

    fun sendStart(zone: ParkingZone, car: Car) {
        val plan = SmsComposer.startSms(appSettings.smsMode, zone.zoneCode, car.plate, appSettings.provider)
        val where = center ?: Geometry.centroid(zone.polygon)
        val status = (sheet as? SheetState.Zone)?.takeIf { it.zone.id == zone.id }?.status
        val scheduledStart = (status as? ParkingStatus.FreeNow)?.nextStartsAt
        launchStart(PreparedStart(zone, car, plan, where, scheduledStart))
    }

    // Widget-triggered quick park: launch the SMS as soon as the VM emits it.
    LaunchedEffect(Unit) {
        vm.autoStart.collect { launchStart(it) }
    }

    startDialogZone?.let { z ->
        val status = (sheet as? SheetState.Zone)?.takeIf { it.zone.id == z.id }?.status ?: ParkingStatus.Unknown
        StartParkingDialog(
            zone = z,
            status = status,
            cars = cars,
            settings = appSettings,
            onDismiss = { startDialogZone = null },
            onConfirm = { car -> startDialogZone = null; sendStart(z, car) },
            onOpenSettings = { startDialogZone = null; onOpenSettings() },
        )
    }

    // Full-screen branded loader (hourglass + status) until we have a first result.
    if (center == null && sheet is SheetState.Locating) {
        FullScreenLoading(text = if (isReady) "Helymeghatározás…" else "Zónák letöltése…")
        return
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 150.dp,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Parkolás") },
                actions = {
                    IconButton(onClick = { vm.locate() }) {
                        Icon(Icons.Filled.LocationSearching, contentDescription = "Helyzet frissítése")
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "Előzmények")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Beállítások")
                    }
                },
            )
        },
        sheetContent = {
            SheetBody(
                sheet = sheet,
                onSelectNearby = { vm.select(it) },
                onStart = { startDialogZone = it },
                onGrantPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                },
                onRetry = { vm.locate() },
            )
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
            )

            // Quick park at the top of the map: only with GPS + chargeable now + a default car.
            val zoneSel = sheet as? SheetState.Zone
            val defaultCar = cars.firstOrNull { it.isDefault }
            if (zoneSel != null && zoneSel.status is ParkingStatus.ChargeableNow && center != null && defaultCar != null) {
                ElevatedButton(
                    onClick = { quickPark(zoneSel.zone) },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null)
                    Text("  Gyors parkolás — ${defaultCar.plate}")
                }
            }
        }
    }
}

@Composable
private fun FullScreenLoading(text: String) {
    val brandBlue = androidx.compose.ui.graphics.Color(0xFF0A5BD0)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brandBlue),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(hu.mobilparkolas.R.drawable.splash_logo),
                contentDescription = null,
                modifier = Modifier.size(200.dp),
            )
            CircularProgressIndicator(
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@Composable
private fun SheetBody(
    sheet: SheetState,
    onSelectNearby: (ParkingZone) -> Unit,
    onStart: (ParkingZone) -> Unit,
    onGrantPermission: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (sheet) {
            SheetState.Locating -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("  Helymeghatározás…", style = MaterialTheme.typography.titleMedium)
            }

            SheetState.NeedPermission -> {
                Text("Helyhozzáférés szükséges", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A parkolási zóna felismeréséhez engedélyezd a helyhozzáférést.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onGrantPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("Engedély megadása")
                }
            }

            is SheetState.NoZone -> {
                Text("Itt nincs fizetős zóna", style = MaterialTheme.typography.titleMedium)
                if (sheet.nearby.isNotEmpty()) {
                    Text("Közeli zónák:", style = MaterialTheme.typography.bodyMedium)
                    sheet.nearby.take(5).forEach { z ->
                        Surface(
                            onClick = { onSelectNearby(z) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${z.city} • ${z.zoneCode} • ${z.feeHuf} Ft/óra",
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }

            is SheetState.Zone -> ZoneSheet(sheet.zone, sheet.status, onStart)

            is SheetState.Error -> {
                Text("Hiba", style = MaterialTheme.typography.titleMedium)
                Text(sheet.message, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Újra") }
            }
        }
    }
}

@Composable
private fun ZoneSheet(zone: ParkingZone, status: ParkingStatus, onStart: (ParkingZone) -> Unit) {
    Text(zone.city, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InfoChip("Zóna", zone.zoneCode)
        InfoChip("Díj", "${zone.feeHuf} Ft/óra")
    }
    Text("Üzemeltető: ${zone.serviceName}", style = MaterialTheme.typography.bodySmall)

    when (status) {
        is ParkingStatus.ChargeableNow ->
            Text(
                "Most fizetős — az időszak vége: ${status.endsAt.format(TIME_FMT)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        is ParkingStatus.FreeNow -> {
            val next = status.nextStartsAt?.format(TIME_FMT) ?: "ismeretlen"
            Text(
                "Most ingyenes. Ha most küldöd az SMS-t, a parkolás a következő fizetős időszakban ($next) indul.",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ParkingStatus.Unknown -> {}
    }

    val label = if (status is ParkingStatus.FreeNow) "Parkolás ütemezése" else "Parkolás indítása"
    Button(
        onClick = { onStart(zone) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(label)
    }
}

@Composable
private fun StartParkingDialog(
    zone: ParkingZone,
    status: ParkingStatus,
    cars: List<Car>,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onConfirm: (Car) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scheduling = status is ParkingStatus.FreeNow
    var selected by remember(cars) { mutableStateOf(cars.firstOrNull { it.isDefault } ?: cars.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (scheduling) "Parkolás ütemezése" else "Parkolás indítása") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${zone.city} • Zóna ${zone.zoneCode} • ${zone.feeHuf} Ft/óra")

                if (scheduling) {
                    val next = (status as ParkingStatus.FreeNow).nextStartsAt?.format(TIME_FMT) ?: "ismeretlen"
                    Text(
                        "Most ingyenes — a parkolás a következő fizetős időszakban ($next) indul.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "Max. várakozás: helyi rendelet szerint ált. 3 óra (néhol 4) — ellenőrizd a zónamatricán!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (cars.isEmpty()) {
                    Text("Nincs autó. Adj hozzá egyet a Beállításokban.")
                } else {
                    Text("Autó:", style = MaterialTheme.typography.labelMedium)
                    cars.forEach { car ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = selected?.id == car.id, onClick = { selected = car })
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected?.id == car.id, onClick = null)
                            Text(
                                "${car.plate}" + if (car.name.isNotBlank()) " — ${car.name}" else "",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    selected?.let { car ->
                        val plan = SmsComposer.startSms(settings.smsMode, zone.zoneCode, car.plate, settings.provider)
                        Text(
                            "SMS: ${plan.number} → „${plan.body}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (cars.isEmpty()) {
                TextButton(onClick = onOpenSettings) { Text("Beállítások") }
            } else {
                TextButton(
                    onClick = { selected?.let(onConfirm) },
                    enabled = selected != null,
                ) { Text(if (scheduling) "SMS küldése (ütemezés)" else "SMS küldése") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun buildPolygon(zone: ParkingZone, isSelected: Boolean, onClick: () -> Unit): Polygon {
    val base = runCatching { AndroidColor.parseColor(zone.colorHex) }.getOrDefault(AndroidColor.GRAY)
    return Polygon().apply {
        points = zone.polygon.map { GeoPoint(it.lat, it.lng) }
        fillPaint.color = AndroidColor.argb(
            if (isSelected) 140 else 70,
            AndroidColor.red(base), AndroidColor.green(base), AndroidColor.blue(base),
        )
        outlinePaint.color = base
        outlinePaint.strokeWidth = if (isSelected) 9f else 4f
        title = "${zone.city} • ${zone.zoneCode}"
        setOnClickListener { _, _, _ -> onClick(); true }
    }
}
