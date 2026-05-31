# MobilParkolás (Android)

Magyarországi mobilparkolás SMS-sel: GPS alapján felismeri a parkolási zónát az
[NMFR](https://nmzrt.hu/szolgaltatasok/parkolas/parkolas.html) nyilvános
zóna-endpointja alapján, kijelzi a díjat és a fizetési időszakot, majd előre
kitöltött SMS-sel indítja/állítja le a parkolást.

## Tech stack

- **Kotlin**, **Jetpack Compose**, single-Activity + Navigation Compose
- **MVVM** + egyirányú adatfolyam (`StateFlow`)
- **Retrofit + OkHttp + kotlinx.serialization** — zóna-endpoint
- **Room** — autók, parkolási munkamenetek/előzmények
- **DataStore** — beállítások (SMS-mód, szolgáltató)
- **FusedLocationProvider** — GPS
- **osmdroid** — térkép (2. fázis)
- Manuális DI (`ServiceLocator`) — később cserélhető Hiltre

## Csomagszerkezet

```
hu.mobilparkolas
├─ domain/        # tiszta logika, Android-függőség nélkül (unit-tesztelt)
│  ├─ model/      # ParkingZone, Car, ParkingSession, ParkingStatus, LatLng, BoundingBox
│  ├─ geo/        # point-in-polygon, bbox-szűrés, közeli zónák
│  ├─ timetable/  # timetable parser -> ChargeableNow / FreeNow
│  └─ sms/        # SmsComposer (központi + szolgáltató-specifikus mód)
├─ data/
│  ├─ api/        # Retrofit ZoneApi, DTO-k, mapper, NetworkModule
│  ├─ db/         # Room entitások, DAO-k, AppDatabase
│  ├─ prefs/      # DataStore beállítások
│  ├─ location/   # FusedLocation wrapper
│  └─ repo/       # ZoneRepository, CarRepository, ParkingRepository
├─ di/            # ServiceLocator
└─ ui/            # MainActivity, Compose képernyők + ViewModelek, theme, SMS launcher
```

## Az SMS-logika

A Google Play korlátozza a `SEND_SMS` jogosultságot, ezért **nem** küldünk SMS-t
automatikusan: az `ACTION_SENDTO` intenttel megnyitjuk a telefon SMS-appját előre
kitöltött címzettel és szöveggel, a felhasználó nyom küldést.

| Mód | Indítás szám | Leállítás szám | Szöveg |
|---|---|---|---|
| Központi (NMFR) | +36 30 344 4805 | +36 30 344 4806 | `ZÓNA RENDSZÁM` / `STOP RENDSZÁM` |
| Szolgáltató (Yettel/Telekom/One) | `+36 XX 763 <zóna>` | ugyanaz | `RENDSZÁM` / `STOP` |

A One szolgáltatónál nincs leállító SMS (`supportsStop = false`).

## Fejlesztői környezet (VSCode + CLI)

Szükséges: **JDK 17+** (itt JDK 21), **Android SDK** (platform-35, build-tools 35,
platform-tools). A Gradle a wrapperből jön, nem kell külön telepíteni.

A `local.properties` (gitignore-olt) tartalmazza az SDK útvonalat:
```
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

## Build és futtatás

```powershell
# Egységtesztek (JVM, eszköz nélkül)
.\gradlew test

# Debug APK
.\gradlew assembleDebug

# Telepítés csatlakoztatott telefonra (USB-hibakeresés bekapcsolva)
.\gradlew installDebug

# Logok
adb logcat
```

## Állapot / roadmap

- **1. fázis (MVP, folyamatban):** GPS → zónafelismerés → díj/időszak kijelzés →
  autók → indító/STOP SMS → aktív parkolás követése. Térkép nélkül.
- **2. fázis:** osmdroid térkép, kézi zónaválasztás, „Navigálj oda", előzmények.

A jelenlegi váz fordítható; a `Confirm` / `Active` / `Cars` / `Settings` képernyők
még stubok, a domain- és data-réteg viszont kész és tesztelt.
