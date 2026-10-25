# AGENTS.md — Spectra Android Project AI Guide

This document is a comprehensive developer and AI agent handbook for **Spectra**, an Android client for Xray/V2Ray networks built with modern Android development practices.

---

## 1. Project Overview

**Spectra** is a feature-rich Xray/V2Ray VPN client for Android (Phone and Android TV).
- **Package**: `com.oklookat.spectra`
- **Min SDK**: `33` (Android 13) | **Target/Compile SDK**: `37` (Release)
- **Language**: Kotlin 2.1+ / JVM 21
- **UI Architecture**: Single Activity (`MainActivity`) with Jetpack Compose and Material 3
- **Dependency Injection**: Hilt (`@HiltViewModel`, `@AndroidEntryPoint`)
- **Persistence**: Room Database (`AppDatabase`) & DataStore Preferences
- **Core Engine**: `libv2ray.aar` (`AndroidLibXrayLite`) wrapping Xray-core

---

## 2. Project Structure & Layering

```
app/src/main/java/com/oklookat/spectra/
├── data/                       # Data layer: Repositories, DAOs, Database
│   ├── repository/             # Profile, Resource, Settings, AppFilter, WifiExclusion
│   ├── AppDatabase.kt          # Room Database declaration
│   ├── ProfileDao.kt / ResourceDao.kt
│   └── DatabaseModule.kt       # Hilt DI module for DB
├── domain/                     # Domain layer: Clean Architecture Use Cases
│   └── usecase/                # Profile, Resource, Settings, VPN Use Cases
├── model/                      # Models & Domain entities
│   ├── AppFilter.kt            # App routing presets and models
│   ├── CustomRules.kt          # Custom DNS & Routing models
│   ├── P2PPayload.kt           # P2P Sharing payload models
│   ├── Profile.kt / Group.kt   # Room Database Entities
│   ├── Resource.kt             # GeoIP/GeoSite resource entity
│   ├── ShareLinks.kt           # Parsers for VLESS, VMess, Trojan, SS, Hysteria2, Socks
│   └── XrayConfigBuild.kt      # Core JSON Builder & Converter for Xray-core
├── service/                    # Background Services
│   └── XrayVpnService.kt       # VpnService handling VPN lifecycle & Xray-core process
├── ui/                         # Presentation Layer (Compose)
│   ├── components/             # Reusable UI components (AppListItem, AppButton, TopBars, FABs)
│   ├── screens/                # MainScreen, ProfilesScreen, SettingsScreen, AppFilterScreen, etc.
│   ├── theme/                  # M3 Color schemes, Type, Theme.kt
│   ├── viewmodel/              # HomeViewModel, ProfilesViewModel, SettingsViewModel, etc.
│   └── SpectraApp.kt           # Main Scaffold & Navigation Host
└── util/                       # Utilities & Background Workers
    ├── AppUpdateWorker.kt      # WorkManager worker for app update checks
    ├── ProfileUpdateWorker.kt  # WorkManager worker for subscription updates
    ├── ResourceUpdateWorker.kt # WorkManager worker for GeoIP/GeoSite updates
    ├── DeepLinkHandler.kt      # Handles `spectra.local` deep links
    ├── LogManager.kt           # In-memory log buffer for LogsScreen
    ├── P2PClient.kt / P2PServer.kt # Local HTTP P2P sharing server
    └── TvUtils.kt              # Android TV detection utilities
```

---

## 3. Core Features & Architectural Workflows

### 3.1 VPN Service (`XrayVpnService.kt`)
- **Lifecycle**: Started via `ContextCompat.startForegroundService`. Shows an ongoing foreground notification (`XrayVpnServiceChannel`).
- **Process Isolation**: The VPN process runs in the main app process. To prevent VPN routing loops, the app's own package (`packageName`) is added to `addDisallowedApplication(packageName)`.
- **Environment Initialization**: On service startup, `copyAssetsToFilesDir()` extracts `geoip.dat` and `geosite.dat` to `filesDir`, and initializes `Libv2ray.initCoreEnv(filesDir.absolutePath, random32ByteKey)`.
- **TUN Interface**: Established via `android.net.VpnService.Builder`. The file descriptor (`pfd.fd`) is passed to the Go core using environment variables (`xray.tun.fd` / `XRAY_TUN_FD`).
- **Network State Observer**: Listens to system network changes (`ConnectivityManager.NetworkCallback`).
  - Ensures active physical networks have `NET_CAPABILITY_INTERNET` and `NET_CAPABILITY_NOT_VPN`.
  - Updates underlying networks with `setUnderlyingNetworks(arrayOf(physicalNetwork))` or `setUnderlyingNetworks(null)`.

### 3.2 Xray Config Generation (`XrayConfigBuild.kt`)
- **`buildFullConfig()`**: Generates a complete `XrayFullConfig` JSON from a parsed proxy `Outbound`.
- **`prepareConfigForAndroid(configJson, isTunModeEnabled, socksPort, logLevel, dnsLog, maskAddress)`**:
  1. Inspects and processes the `"log"` object:
     - Retains original values for `loglevel`, `dnsLog`, `maskAddress` if present in the profile config.
     - Falls back to app log settings (`logLevel` default `"warning"`, `dnsLog` default `false`, `maskAddress` default `""`) for unspecified fields.
     - Strips `access` and `error` file path properties (file logging is disabled on Android; stdout/logcat is used).
  2. Inspects `inbounds`.
  3. If `isTunModeEnabled` is `true`, converts any SOCKS inbound matching `socksPort` (default `10808`) to a `tun` inbound (`protocol: "tun"`, `tag: "tun-in"`).
  4. Updates all references to the old SOCKS tag inside `routing.rules[].inboundTag` (both string arrays and single strings) to `"tun-in"`.
  5. Configures TUN settings cleanly (`name: "tun0"`, `mtu: 9000`).
  6. Configures Sniffing (`enabled: true`, `routeOnly: true`, `destOverride: ["http", "tls", "quic", "fakedns"]`).
  7. If no TUN inbound exists and `isTunModeEnabled` is `true`, injects a default `tun` inbound.

### 3.3 Profiles & Subscription Management
- **Local Profiles**: Raw JSON configurations or share links (VLESS, VMess, Trojan, Shadowsocks, Socks, Hysteria2).
- **Remote Profiles & Groups**: URLs fetched periodically by `ProfileUpdateWorker` (via WorkManager) or manually refreshed by the user.
- **Sorting**: Profiles can be sorted AS IS, by Name (A-Z / Z-A), or by Ping latency.
- **Ping**: Measures TCP handshake latency to the remote endpoint.

### 3.4 App Filtering (Split Tunneling)
- Managed via `AppFilterRepository` and `AppFilterScreen`.
- Supports presets (e.g. *All through VPN*, *Exclude RU apps*, *Custom*).
- Disallowed packages are retrieved via `activeDisallowedPackagesFlow` and passed to `VpnService.Builder.addDisallowedApplication(pkg)`.

### 3.5 Wi-Fi Exclusions
- Managed via `WifiExclusionRepository` and `WifiExclusionsScreen`.
- When enabled, if the device connects to an SSID in `excludedSsids`, `XrayVpnService` dynamically switches the core configuration to a bypass configuration (direct routing for all traffic).

### 3.7 Log Configuration & Logs Screen
- **Log Settings**: Accessible via the Tune icon on `LogsScreen`.
- **Options**: `logLevel` (`debug`, `info`, `warning`, `error`, `none`), `dnsLog` (boolean), `maskAddress` (`""`, `quarter`, `half`, `full`).
- **Apply Logic**:
  - If VPN is running: Button displays *"Apply & Restart VPN"*, saving settings to DataStore and triggering a VPN restart.
  - If VPN is inactive: Button displays *"Save"*, saving settings to DataStore with UI notice that settings will apply on the next VPN start.

---

## 4. CRITICAL Technical Rules & Pitfalls

### Rule 1: Min SDK is 33 (Android 13)
> [!NOTE]
> Do NOT write obsolete API version checks for API < 33 (e.g., `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q`, `S`, or `TIRAMISU`).

### Rule 2: Freedom Outbound (`direct-out`) & System DNS
> [!CRITICAL]
> In `FreedomSettings`, always use `@SerializedName("domainStrategy") val domainStrategy: String = "UseIPv4"`.
> **Never** name this property `targetStrategy`!

- **Why**: Misnaming `domainStrategy` causes Gson to serialize `{}` for `FreedomSettings`.
- **Consequence**: Xray defaults `freedom` outbound to `AsIs`. In `AsIs` mode, Xray delegates domain resolution to Android's OS resolver (`getaddrinfo`).
- **Android Bug**: When a VPN is running and the device switches networks (e.g., Wi-Fi turned off -> Cellular), Android's system `getaddrinfo` for bypassed applications (`addDisallowedApplication`) **hangs indefinitely or fails**.
- **Fix**: Setting `domainStrategy = "UseIPv4"` in `FreedomSettings` forces Xray to resolve domains internally using its built-in DNS (DoH 1.1.1.1 over `proxy-out`), completely bypassing Android's broken `getaddrinfo`.

### Rule 3: TUN Settings Schema for Xray-Core
> [!WARNING]
> Xray's `tun` inbound `settings` on Android should contain **only** `name` and `mtu`:
> ```json
> "settings": {
>   "name": "tun0",
>   "mtu": 9000
> }
> ```
> Do **NOT** add sing-box / legacy fields like `autoRoute`, `strictRoute`, `endpoint`, or `stack`.

### Rule 4: FakeDNS in Sniffing
- Always include `"fakedns"` in `sniffing.destOverride` (`["http", "tls", "quic", "fakedns"]`).
- Without `"fakedns"`, Xray fails to restore domain names from FakeDNS IP addresses (e.g., `198.18.x.x`), causing domain-based routing rules (`geosite:ru`, etc.) to fail.

### Rule 5: No `runBlocking` in ViewModels / UI Thread
- Never invoke `runBlocking` inside ViewModels, UI Composables, or main-thread calls.
- Use `viewModelScope.launch` with state updates or callback closures (e.g., `getActiveConfigJson(onResult: (String) -> Unit)`).
- Make repository methods `suspend` when fetching data asynchronously.

### Rule 6: Compose `LocalContext` Resource Resolution
- Do **NOT** call `context.getString(...)` directly inside a `LaunchedEffect` or asynchronous flow if `context` is retrieved via `LocalContext.current`.
- **Fix**: Use `context.applicationContext.getString(...)` to satisfy Compose Lint (`LocalContextGetResourceValueCall`).

### Rule 7: Network Callbacks in `XrayVpnService`
- When evaluating physical networks in `ConnectivityManager.NetworkCallback`, **always** verify `NET_CAPABILITY_INTERNET` and `NET_CAPABILITY_NOT_VPN`.
- Ignore networks that do not have `NET_CAPABILITY_INTERNET` to prevent binding the VPN to uninitialized or dead mobile data interfaces during network switching.

### Rule 8: Localization
- Maintain both `res/values/strings.xml` (English) and `res/values-ru/strings.xml` (Russian).
- Internal non-user-facing strings (e.g., `"Local"`, `"Remote"`) must be marked with `translatable="false"`.

---

## 5. Build, Test & Verification Commands

When executing tasks or verifying changes, use the following Gradle tasks via `gradle_build`:

```powershell
# Compile Kotlin sources
gradle_build(":app:compileDebugKotlin")

# Run Android Lint checks (ensures no LocalContext or translation errors)
gradle_build(":app:lintDebug")

# Build Debug APK
gradle_build(":app:assembleDebug")
```

---

## 6. Quick Troubleshooting Checklist

| Problem | Cause | Solution |
| :--- | :--- | :--- |
| Direct connections hang after switching Wi-Fi -> Mobile Data | `FreedomSettings` missing `domainStrategy` (or misnamed as `targetStrategy`), causing Xray to use system `getaddrinfo`. | Set `@SerializedName("domainStrategy") val domainStrategy: String = "UseIPv4"` in `FreedomSettings`. |
| Domain routing rules fail when FakeDNS is used | `sniffing.destOverride` lacks `"fakedns"`. | Add `"fakedns"` to `destOverride` list. |
| Lint error `LocalContextGetResourceValueCall` | Calling `context.getString()` inside `LaunchedEffect`. | Use `context.applicationContext.getString(...)`. |
| Mobile network icon disappears when switching networks | `XrayVpnService` bound underlying network to a Cellular interface without `NET_CAPABILITY_INTERNET`. | Filter networks using `caps.hasCapability(NET_CAPABILITY_INTERNET)`. |
| Build fails on Lint `MissingTranslation` | New string added to `values/strings.xml` without Russian translation. | Add translation in `values-ru/strings.xml` or mark `translatable="false"`. |
