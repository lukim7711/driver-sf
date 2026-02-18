# Driver SF — PoC Screen Reader

Proof of Concept: Membaca layar ShopeeFood Driver (`com.shopee.foody.driver.id`) menggunakan Android Accessibility Service.

## Status

🚧 **In Development** — PoC Phase

## Tujuan

Membuktikan apakah aplikasi Android bisa membaca teks yang tampil di layar ShopeeFood Driver menggunakan Accessibility Service.

## Tech Stack

- **Language**: Kotlin
- **Min SDK**: 24 (Android 7.0+)
- **Database**: Room (SQLite)
- **UI**: Android Views (XML Layout)
- **Build**: Gradle with Kotlin DSL

## Git Workflow

- `main` — stable branch
- `feat/*` — fitur baru
- `fix/*` — bug fix
- `docs/*` — dokumentasi
- Semua perubahan via Pull Request (squash merge)
- Commit convention: `feat:`, `fix:`, `docs:`
- Release tag: `vX.Y.Z`
