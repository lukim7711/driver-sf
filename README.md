# Driver SF — PoC Screen Reader

Proof of Concept: Membaca layar ShopeeFood Driver (`com.shopee.foody.driver.id`) menggunakan Android Accessibility Service.

## Status

✅ **v0.1.0 Released** — PoC Ready for Testing

## Tujuan

Membuktikan apakah aplikasi Android bisa membaca teks yang tampil di layar ShopeeFood Driver menggunakan Accessibility Service.

## Fitur

- **Screen Reader Service** — Membaca semua teks di layar ShopeeFood Driver secara otomatis
- **Halaman 1: Daftar Tangkapan** — List semua tangkapan dengan preview, waktu, dan jumlah karakter
- **Halaman 2: Detail Tangkapan** — Full text + node tree JSON untuk developer
- **Debounce 300ms** — Mencegah log spam saat scroll
- **Deduplikasi** — Skip tangkapan jika teks identik dengan sebelumnya
- **Foreground Notification** — Counter tangkapan harian di status bar
- **Copy to Clipboard** — Salin teks tangkapan
- **Room Database** — Penyimpanan lokal (zero internet)

## Tech Stack

- **Language**: Kotlin
- **Min SDK**: 24 (Android 7.0+)
- **Target SDK**: 34
- **Database**: Room (SQLite)
- **UI**: Android Views (XML Layout) + ViewBinding
- **Build**: Gradle 8.5 + Kotlin DSL
- **CI**: GitHub Actions

## Setup

1. Clone repo
2. Buka di Android Studio
3. Build & install ke device
4. Buka app → tap "Aktifkan Screen Reader"
5. Aktifkan service di Settings → Accessibility
6. Buka ShopeeFood Driver → app PoC otomatis membaca layar
7. Buka app PoC → lihat daftar tangkapan

## Project Structure

```
app/src/main/java/com/driversfpoc/screenreader/
├── ScreenReaderApp.kt              # Application class (notification channel)
├── service/
│   └── ScreenReaderService.kt       # Core: Accessibility Service + node traversal
├── data/
│   ├── model/CaptureRecord.kt       # Room entity
│   ├── CaptureDao.kt                # Data access object
│   ├── AppDatabase.kt               # Room database singleton
│   └── CaptureRepository.kt         # Repository pattern
└── ui/
    ├── MainActivity.kt              # Halaman 1: Daftar Tangkapan
    ├── CaptureDetailActivity.kt     # Halaman 2: Detail Tangkapan
    └── CaptureAdapter.kt            # RecyclerView adapter
```

## Git Workflow

- `main` — stable branch
- `feat/*` — fitur baru
- `fix/*` — bug fix
- `docs/*` — dokumentasi
- Semua perubahan via Pull Request (squash merge)
- Commit convention: `feat:`, `fix:`, `docs:`
- Release tag: `vX.Y.Z`

## Dokumentasi

- [PoC-001: Screen Reader Specification](docs/PoC-001-screen-reader.md)
