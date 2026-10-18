# Master Product Requirements Document: ApexTerminal Android

**Target Environment:** GitHub Actions / OpenCode Runner  
**Target Platform:** Android 13+ (`minSdk = 33`, `targetSdk = 35`)  
**Language & Framework:** 100% Kotlin + Jetpack Compose (Material 3)  
**Chart Engine:** Latest TradingView Lightweight Charts v5.2.1 (Standalone Asset Bundle)  
**Target Device Profile:** Xiaomi Redmi Note 10 Pro Max (OLED, Snapdragon 732G, 60Hz/120Hz)  
**Target Network:** High-latency / restricted mobile data (Bangladesh ISP failover mirrors)

---

## 1. System Architecture & Component Model

The application follows Clean Architecture with Unidirectional Data Flow (UDF).
- The outer application (TopBar, HUD, Timeframe Strip, Search, BottomSheets, Drawer, Background Service) is 100% native Android Kotlin + Jetpack Compose Material 3.
- The chart viewport runs the official TradingView Lightweight Charts v5.2.1 engine locally inside a hardware-accelerated Android WebView via `assets/chart.html`.
- Communication between Kotlin and the chart is handled over a bidirectional JavascriptInterface (`ChartBridge.kt`).

```
┌────────────────────────────────────────────────────────┐
│             JETPACK COMPOSE MATERIAL 3 SHELL           │
│  TopAppBar • HUD Bar • TimeframeStrip • BottomSheets   │
└───────────────────────────┬────────────────────────────┘
                            │ Bidirectional Flow / JS Interface
                            ▼
┌────────────────────────────────────────────────────────┐
│     CHART VIEWPORT: TRADINGVIEW LIGHTWEIGHT CHARTS v5   │
│  assets/chart.html ⟵ Local WebGL/Canvas (Zero Latency) │
└───────────────────────────▲────────────────────────────┘
                            │
┌───────────────────────────┴────────────────────────────┐
│                    KOTLIN REPOSITORY                   │
│  State Reconciliation • Indicator Math • StateFlow     │
└─────────────▲────────────────────────────▲─────────────┘
              │                            │
┌─────────────┴─────────────┐  ┌───────────┴─────────────┐
│  OKHTTP STREAMING CLIENT  │  │  ROOM DATABASE (SQLITE) │
│ Binance WS + REST Engine  │  │ WAL Mode (Zero-Lock)    │
│ Fallback: binance.vision  │  │ Composite Unique Upsert │
└───────────────────────────┘  └─────────────────────────┘
```

---

## 2. Latest Dependencies & Gradle Configuration

The agent must create `app/build.gradle.kts` targeting the latest Android 15 toolchain (`targetSdk = 35`):

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "com.apex.terminal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.apex.terminal"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api::class",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi::class"
        )
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    // Jetpack Compose BOM & Material 3
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // AndroidX & Lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Networking (OkHttp & Retrofit)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Local Persistence (Room + SQLite WAL)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Preferences & Image Loading
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
```

---

## 3. Data Layer: Persistence & Network Failover

### 3.1 Room Database Schema (Zero Duplicate Keys)

```kotlin
// data/local/entity/CandleEntity.kt
@Entity(
    tableName = "candles",
    indices = [Index(value = ["symbol", "interval", "timestamp"], unique = true)]
)
data class CandleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val interval: String,
    val timestamp: Long, // Epoch ms (Exchange open time)
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)
```

```kotlin
// data/local/CandleDao.kt
@Dao
interface CandleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCandles(candles: List<CandleEntity>)

    @Query("SELECT * FROM candles WHERE symbol = :symbol AND interval = :interval ORDER BY timestamp ASC")
    fun getCandles(symbol: String, interval: String): Flow<List<CandleEntity>>

    @Query("SELECT MAX(timestamp) FROM candles WHERE symbol = :symbol AND interval = :interval")
    suspend fun getLatestTimestamp(symbol: String, interval: String): Long?
}
```

Database must enable WAL mode:
```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "apex_terminal.db")
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
    .fallbackToDestructiveMigration()
    .build()
```

### 3.2 Bangladesh ISP Network Failover Engine

If `api.binance.com` or `stream.binance.com` times out due to regional DNS filtering, automatically route requests to the official mirror:
- **Primary REST:** `https://api.binance.com`
- **Fallback Mirror REST:** `https://data-api.binance.vision`
- **Primary WebSocket:** `wss://stream.binance.com:9443/ws/`
- **Fallback Mirror WebSocket:** `wss://data-stream.binance.vision/ws/`

Stream subscription format: `<symbol>@kline_<interval>` (e.g., `btcusdt@kline_1m`).

#### Continuity Guarantee:
- Server time rules: A candle is only closed when `k.x == true` arrives from the exchange stream.
- Invariant: `New Candle Open == Previous Candle Close`.
- On reconnect: Query `CandleDao.getLatestTimestamp()`. If a gap exists, issue a REST backfill call:
  `GET /api/v3/klines?symbol={symbol}&interval={interval}&startTime={latestTimestamp + 1}&limit=1000`
  Upsert the response into Room before resuming the live WebSocket.

---

## 4. Latest TradingView Lightweight Charts v5 Integration

### 4.1 Asset Download
During project scaffolding, the agent must execute:
```bash
mkdir -p app/src/main/assets
curl -sLo app/src/main/assets/lightweight-charts.standalone.production.js https://unpkg.com/lightweight-charts@5.2.1/dist/lightweight-charts.standalone.production.js
```

### 4.2 `app/src/main/assets/chart.html` (Using Lightweight Charts v5 API)

```html
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <script src="lightweight-charts.standalone.production.js"></script>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body { width: 100%; height: 100%; background: #000000; overflow: hidden; }
    #container { width: 100%; height: 100%; }
  </style>
</head>
<body>
  <div id="container"></div>
  <script>
    let chart, candleSeries, volumeSeries, ema20Series, ema50Series;

    function initChart() {
      const container = document.getElementById('container');
      chart = LightweightCharts.createChart(container, {
        width: container.clientWidth,
        height: container.clientHeight,
        layout: {
          background: { type: 'solid', color: '#000000' },
          textColor: '#848E9C',
          fontSize: 10,
          fontFamily: 'Roboto Mono, monospace'
        },
        grid: {
          vertLines: { color: '#0D0E12' },
          horzLines: { color: '#0D0E12' }
        },
        crosshair: {
          mode: LightweightCharts.CrosshairMode.Normal,
          vertLine: { color: '#787B86', width: 1, style: 3, labelBackgroundColor: '#1E222D' },
          horzLine: { color: '#787B86', width: 1, style: 3, labelBackgroundColor: '#1E222D' }
        },
        rightPriceScale: {
          borderColor: '#191C24',
          scaleMargins: { top: 0.1, bottom: 0.25 },
          autoScale: true
        },
        timeScale: {
          borderColor: '#191C24',
          timeVisible: true,
          secondsVisible: false,
          shiftVisibleRangeOnNewBar: true
        }
      });

      // V5 SYNTAX: chart.addSeries(LightweightCharts.CandlestickSeries, options)
      candleSeries = chart.addSeries(LightweightCharts.CandlestickSeries, {
        upColor: '#2563EB',
        downColor: '#1E222D',
        borderUpColor: '#2563EB',
        borderDownColor: '#2563EB',
        wickUpColor: '#2563EB',
        wickDownColor: '#2563EB'
      });

      // V5 SYNTAX: Histogram for Volume
      volumeSeries = chart.addSeries(LightweightCharts.HistogramSeries, {
        priceFormat: { type: 'volume' },
        priceScaleId: '',
        scaleMargins: { top: 0.8, bottom: 0 }
      });

      // V5 SYNTAX: LineSeries for EMA overlays
      ema20Series = chart.addSeries(LightweightCharts.LineSeries, { color: '#2962FF', lineWidth: 1, crosshairMarkerVisible: false });
      ema50Series = chart.addSeries(LightweightCharts.LineSeries, { color: '#FF9800', lineWidth: 1, crosshairMarkerVisible: false });

      // Crosshair tracking
      chart.subscribeCrosshairMove(param => {
        if (!param.time || !param.seriesData.get(candleSeries)) {
          AndroidBridge.onCrosshairReset();
          return;
        }
        const data = param.seriesData.get(candleSeries);
        AndroidBridge.onCrosshairUpdate(data.open, data.high, data.low, data.close, param.time);
      });

      window.addEventListener('resize', () => {
        chart.resize(container.clientWidth, container.clientHeight);
      });
    }

    function setInitialData(candleJson, volumeJson) {
      candleSeries.setData(JSON.parse(candleJson));
      volumeSeries.setData(JSON.parse(volumeJson));
    }

    function updateLiveTick(candleObjJson) {
      const c = JSON.parse(candleObjJson);
      candleSeries.update(c);
      volumeSeries.update({
        time: c.time,
        value: c.volume,
        color: c.close >= c.open ? '#2563EB44' : '#1E222D44'
      });
    }

    function setEmaData(ema20Json, ema50Json) {
      if (ema20Json) ema20Series.setData(JSON.parse(ema20Json));
      if (ema50Json) ema50Series.setData(JSON.parse(ema50Json));
    }

    window.onload = initChart;
  </script>
</body>
</html>
```

### 4.3 JavaScript Bridge (`ChartBridge.kt`)
```kotlin
class ChartBridge(
    private val onCrosshair: (Double, Double, Double, Double, Long) -> Unit,
    private val onReset: () -> Unit
) {
    @JavascriptInterface
    fun onCrosshairUpdate(open: Double, high: Double, low: Double, close: Double, time: Long) {
        onCrosshair(open, high, low, close, time)
    }

    @JavascriptInterface
    fun onCrosshairReset() {
        onReset()
    }
}
```

---

## 5. UI/UX Specifications (Pure OLED Black)

### 5.1 Color Tokens
- `BgBlack`: `#000000` (Root surface)
- `SurfaceBase`: `#060709` (TopAppBar, TimeframeStrip, Footer)
- `SurfaceCard`: `#0C0E12` (Modals, NavigationDrawer)
- `SurfaceRaised`: `#12151C` (Active chips, toggles)
- `BorderDark`: `#191C24` (Hairline dividers)
- `BlueMain`: `#2563EB` (Primary bullish candles, active state)
- `RedChg`: `#F23645` (Negative 24h market delta)
- `GreenPing`: `#089981` (Network latency indicator)

### 5.2 Screen Elements
1. **TopAppBar (48dp):** Drawer icon (`Icons.Default.Menu`) + Symbol Selector pill (`BTC/USDT -0.64% ▾`) + 24h High, Low, Volume metrics.
2. **TerminalHudBar (24dp):** Horizontally scrollable row showing live candle OHLC values + active EMA indicator values in `Roboto Mono`.
3. **TimeframeStrip (32dp):** Chips for `1m`, `3m`, `5m`, `15m`, `1h`, `4h`, `1D` + `fx Indicators` button + Settings icon (`⚙`).
4. **DrawingRail (Left, 32dp):** Floating pill toolbar with Crosshair `✛`, Trendline `╱`, Ray `―`, Fibonacci `≡`, and Trash `🗑`.
5. **SymbolSearchBottomSheet:** Implemented with `ModalBottomSheet`, `WindowInsets.ime`, and `Modifier.imePadding()` to guarantee the soft keyboard never hides search results. Coin logos loaded via Coil with circular monogram fallback.
6. **IndicatorsBottomSheet (`fx`):** Standard indicators only: EMA (20, 50, 200), RSI (14), Stochastic (14,3,3). Each toggles via a Material 3 `Switch`.
7. **TerminalFooter (38dp):** Displays live latency badge (`● 187ms`) and background monitor toggle (`● Monitor ON`).

---

## 6. Continuous Background Monitor Service

Designed to monitor strategies during active gaming or video streaming without being killed:
- Service class: `ContinuousMarketMonitorService : Service()`
- Permission declarations in `AndroidManifest.xml`:
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `WAKE_LOCK`.
- Foreground notification channel: `IMPORTANCE_LOW` for ongoing status with two actions:
  - `[PAUSE 1 HOUR]`
  - `[STOP MONITOR]`
- Strategy condition checks run strictly on closed candles (`k.x == true`).
- Fired alert channel: `IMPORTANCE_HIGH` with sound and vibration. Clicking `[OPEN CHART]` opens `MainActivity` via a Deep Link with `EXTRA_SYMBOL` and `EXTRA_TIMEFRAME` without clearing drawings.

---

## 7. Phased Implementation Gates for OpenCode

OpenCode must execute these four milestones sequentially. Each must pass verification before continuing:

- **Milestone 1: Project Scaffolding & Data Layer**
  - Download `lightweight-charts.standalone.production.js` (v5.2.1) into `app/src/main/assets/`.
  - Create Room Database with WAL mode and `CandleDao`.
  - Implement Binance OkHttp WebSocket and REST client with fallback mirror.
  - *Verification Gate:* Run `./gradlew testDebugUnitTest --quiet` $\rightarrow$ Must PASS.

- **Milestone 2: Chart Bridge & WebView**
  - Implement `chart.html` using the v5 `chart.addSeries()` API.
  - Implement `ChartBridge.kt` and the `ChartViewport` Compose wrapper.
  - Wire `ChartViewModel` to stream live ticks into JavaScript.
  - *Verification Gate:* Run `./gradlew assembleDebug` $\rightarrow$ Must compile with exit code 0.

- **Milestone 3: Native Compose UI Shell**
  - Implement `TerminalTopBar`, `TerminalHudBar`, `TimeframeStrip`, and `TerminalFooter`.
  - Implement `SymbolSearchBottomSheet` with `imePadding()` and Coil image caching.
  - Implement `IndicatorsBottomSheet` and `SystemDrawer`.
  - *Verification Gate:* Run `./gradlew assembleDebug` $\rightarrow$ Must compile with exit code 0.

- **Milestone 4: Continuous Alert Service & Release Build**
  - Implement `ContinuousMarketMonitorService` and `NotificationHelper`.
  - Wire closed-candle alert conditions and the heads-up notification deep link.
  - *Verification Gate:* Run `./gradlew assembleDebug --stacktrace` $\rightarrow$ Exit code 0.
  - Output file: `app/build/outputs/apk/debug/app-debug.apk`.