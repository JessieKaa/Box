# TVBox (Box) - Android TV Media Streaming App

Fork of the open-source TVBox project for Android TV / set-top boxes. Aggregates video content from pluggable "spider" sources (JAR, JS, Python), supports live TV, VOD playback, cloud drive browsing.

## Build & Run

```bash
./gradlew assembleRelease            # Build all release variants
./gradlew assembleArm64GenericNormalRelease  # Specific variant
```

**Build variants** — three flavor dimensions:

| Dimension | Values | Notes |
|-----------|--------|-------|
| `abi` | `armeabi`, `arm64` | armeabi-v7a / arm64-v8a |
| `brand` | `generic`, `hisense` | Hisense has different applicationId |
| `mode` | `normal`, `python` | `python` adds Chaquopy runtime |

Output naming: `TVBox_{buildType}-{flavorNames}.apk`

**CI**: `.github/workflows/test.yml` — manual dispatch only (`workflow_dispatch`), runs `assembleRelease`.

## Project Structure

```
Box/
├── app/                    # Main application module
├── quickjs/                # QuickJS JavaScript engine wrapper (JNI .so)
├── pyramid/                # Python spider runtime via Chaquopy (Python 3.8)
├── xwalk/                  # CrossWalk WebView (local JAR/AAR)
└── docs/                   # Documentation
```

### App Module — Key Packages

All source under `app/src/main/java/`:

| Package | Purpose |
|---------|---------|
| `com.github.tvbox.osc.base` | `App.java` (Application), `BaseActivity` |
| `com.github.tvbox.osc.api` | `ApiConfig` — config parsing, spider loading, source management |
| `com.github.tvbox.osc.ui.activity` | All 16 Activities |
| `com.github.tvbox.osc.ui.fragment` | `GridFragment`, `PlayFragment`, `UserFragment`, `ModelSettingFragment` |
| `com.github.tvbox.osc.ui.adapter` | RecyclerView adapters |
| `com.github.tvbox.osc.ui.dialog` | 23 dialog classes |
| `com.github.tvbox.osc.viewmodel` | `SourceViewModel`, `SubtitleViewModel`, drive ViewModels |
| `com.github.tvbox.osc.bean` | Data models (`Movie`, `VodInfo`, `SourceBean`, etc.) |
| `com.github.tvbox.osc.data` | Room database (`AppDataBase`), `AppDataManager` |
| `com.github.tvbox.osc.cache` | Room entities and DAOs |
| `com.github.tvbox.osc.player` | Video player impls (IJK, ExoPlayer), controllers, danmaku |
| `com.github.tvbox.osc.karaoke` | Karaoke MV player (local file scanning, playlist, playback) — **under development** |
| `com.github.tvbox.osc.server` | Embedded HTTP server (NanoHTTPD port 9978, AndServer port 12345) |
| `com.github.tvbox.osc.util.js` | JS spider runtime (`JsSpider`, `Connect`, `Crypto`, etc.) |
| `com.github.tvbox.osc.subtitle` | Subtitle parsing (SRT, ASS) |
| `com.github.tvbox.osc.util` | Utilities (live TV parsing, thunder/magnet, URL HTTP) |
| `com.github.catvod.crawler` | Spider framework (`Spider` base, `JarLoader`, `JsLoader`) |

## Architecture

- **MVVM-like** with LiveData + ViewModel, no Repository layer — ViewModels talk directly to `ApiConfig` singleton or Room DAOs
- **EventBus** (`org.greenrobot.eventbus`) for cross-component events (`RefreshEvent`, `ServerEvent`, `HistoryStateEvent`, etc.)
- **No DI framework** — manual singletons (`ApiConfig`, `AppDataManager`, `ControlManager`)
- **Primarily Java**, minimal Kotlin (`WebController.kt`, `JavaUtil.kt`)
- **DataBinding** enabled

## Spider/Crawler System

Core content aggregation via pluggable spiders:

| Type | Loader | Mechanism |
|------|--------|-----------|
| JAR | `JarLoader` | `DexClassLoader` loads plugin JARs |
| JS | `JsLoader` | QuickJS engine via `JsSpider` |
| Python | `IPyLoader` | Chaquopy Python runtime via `pyramid` module |

`Spider` abstract class defines: `homeContent`, `categoryContent`, `detailContent`, `searchContent`, `playerContent`, `liveContent`.

Config JSON loaded from URL: `spider` (plugin URL), `sites` (sources), `lives` (live TV), `parses` (video parse rules).

## Key Activities

| Activity | Purpose |
|----------|---------|
| `HomeActivity` | Launcher — ViewPager with source tabs |
| `DetailActivity` | VOD detail with episode selection (PiP) |
| `PlayActivity` | VOD playback (PiP) |
| `LivePlayActivity` | Live TV channel playback (PiP) |
| `SearchActivity` / `FastSearchActivity` | Search across sources |
| `DriveActivity` | Cloud/local drive browser (WebDAV, Alist, SMB) |
| `HistoryActivity` / `CollectActivity` | Watch history / favorites |
| `PushActivity` | Remote push (cast URL) |

All activities are **landscape-only**, TV-optimized with D-pad navigation.

## Data Layer (Room)

Database: `AppDataBase` version 5, `allowMainThreadQueries()` enabled.

Key tables: `Cache`, `VodRecord`, `VodCollect`, `StorageDrive`, `SearchHistory`, `HomeFolderShortcut`, `HomeFolderIndexEntry`.

## Player System

- **IJK** (`IjkmPlayer`) — software/hardware decoding
- **ExoPlayer/Media3** (`EXOmPlayer`) — DASH, HLS, RTSP, RTMP
- **Aliyun Player** — Alibaba Cloud SDK
- **System** — default Android MediaPlayer
- **External** — MXPlayer, Kodi, ReexPlayer (launch intent)
- Player setting: `HawkConfig.PLAY_TYPE` (0=System, 1=IJK, 2=Exo, 3=MX, 4=Reex, 5=Kodi)

## Embedded Servers

- **NanoHTTPD** (port 9978): `RemoteServer` — local proxy, DNS, file browser, web remote control
- **AndServer** (port 12345): `WebController` — REST API for URL push (`/api/updateUrl`)

## Key Libraries

OkHttp3, OkGo, Gson, Glide, Room, EventBus, Hawk, Media3/ExoPlayer, IJK Player, Aliyun Player, DKPlayer, NanoHTTPD, AndServer, DanmakuFlameMaster, TVRecyclerView, JSoup, XStream, ZXing, sardine-android (WebDAV), jcifs (SMB), Conscrypt, Chaquopy.

## Conventions

- Design dimensions: 1280x720 dp (landscape TV)
- `compileSdk 34`, `targetSdkVersion 28`, `minSdkVersion 21`, Java 1.8
- Proguard enabled for release builds (`-repackageclasses androidx.base`)
- Config stored in `Hawk` (encrypted key-value store)
- Version name: `"1.0." + buildTimestamp`

## Debug Or Test Or Automation

Use agent-device only for app/device automation tasks. Before planning commands, run `agent-device --version` and read `agent-device help workflow`. For exploratory QA, read `agent-device help dogfood`. For logs, network, traces, or runtime failures, read `agent-device help debugging`. For React Native component trees, props/state/hooks, slow renders, or rerenders, read `agent-device help react-devtools`. For React Native apps, overlays, Metro/Fast Refresh blockers, and routing to React DevTools or debugging evidence, read `agent-device help react-native`.

Use MCP tools or the CLI in the integrated terminal. If `agent-device` is not on PATH but the user installed it globally in another shell, resolve the command the same way the user would from a normal terminal session and run that absolute path instead. This may require inspecting shell startup behavior or package-manager/global bin locations; do not assume the agent process `PATH` is the user's `PATH`. Do not silently fall back to `npx -y agent-device@latest`; ask or use an exact version. MCP exposes structured tools backed by the agent-device client; it does not expose generic shell execution. Prefer `open -> snapshot -i -> act -> re-snapshot -> verify -> close`. Use current refs such as `@e3` for exploration and selectors for durable replay. Keep mutating commands against one session serial. Capture screenshots, logs, network, perf, traces, recordings, and `.ad` replay scripts only when they add evidence


## agent-device

Use agent-device only for app/device automation tasks.
Before planning device work, run `agent-device --version` and read `agent-device help workflow`.
For exploratory QA, read `agent-device help dogfood`.
For logs, network, traces, or runtime failures, read `agent-device help debugging`.
For React Native component trees, props/state/hooks, slow renders, or rerenders, read `agent-device help react-devtools`.
For React Native apps, overlays, Metro/Fast Refresh blockers, and routing to React DevTools or debugging evidence, read `agent-device help react-native`.

Use the CLI in the integrated terminal.
If `agent-device` is not on PATH but the user installed it globally in another shell, resolve the absolute binary path instead of using `npx -y agent-device@latest`.
Prefer `open -> snapshot -i -> act -> re-snapshot -> verify -> close`.
Keep mutating commands against one session serial.