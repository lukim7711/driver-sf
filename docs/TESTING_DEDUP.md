# Testing Plan — Hybrid Dedup v3

Dokumen ini berisi skenario testing untuk fitur **DB-level dedup** yang diperkenalkan di [PR #13](https://github.com/lukim7711/driver-sf/pull/13).

## Prerequisites

- Install build terbaru dari branch `main`
- Buka **Logcat** filter: `ScreenReaderService`
- Pastikan accessibility service aktif
- **Clear data app** dulu agar mulai dari DB kosong (fresh v4 migration)

---

## Skenario 1 — Baseline: Capture Normal

**Tujuan**: Pastikan capture tetap berjalan untuk halaman unik.

| Step | Aksi | Expected |
|---|---|---|
| 1 | Buka ShopeeFood Driver | Halaman Beranda ter-capture |
| 2 | Buka menu Riwayat | Halaman Riwayat ter-capture |
| 3 | Tap salah satu pesanan (misal #456) | Rincian Pesanan ter-capture |
| 4 | Back ke Riwayat | ⚠️ Riwayat **TIDAK** ter-capture (sudah ada di DB) |
| 5 | Buka pesanan **berbeda** (#789) | Rincian Pesanan **baru** ter-capture (konten beda) |

**Logcat yang diharapkan**:

```
Step 1: Captured #1 [WINDOW_STATE_CHANGED]: Beranda... (hash=12345)
Step 2: Captured #2 [WINDOW_STATE_CHANGED]: Riwayat... (hash=67890)
Step 3: Captured #3 [WINDOW_STATE_CHANGED]: Rincian Pesanan #456... (hash=11111)
Step 4: Dedup L1 (ring buffer): skip hash=67890        ← L1 tangkap!
Step 5: Captured #4 [WINDOW_STATE_CHANGED]: Rincian Pesanan #789... (hash=22222)
```

**✅ Pass jika**: Total capture = 4 (bukan 5)

---

## Skenario 2 — Cross-Session Dedup

**Tujuan**: Pastikan duplikat lintas sesi tertangkap oleh Layer 2 (DB).

| Step | Aksi | Expected |
|---|---|---|
| 1 | Buka ShopeeFood → Beranda | Ter-capture (hash=A) |
| 2 | Buka Riwayat | Ter-capture (hash=B) |
| 3 | Buka Rincian #456 | Ter-capture (hash=C) |
| 4 | **Matikan accessibility service** dari Settings | Service destroyed, ring buffer cleared |
| 5 | **Nyalakan kembali** accessibility service | Service created fresh, ring buffer kosong |
| 6 | Buka ShopeeFood → Beranda | **SKIP** — L2 (DB) tangkap hash=A |
| 7 | Buka Riwayat | **SKIP** — L2 (DB) tangkap hash=B |
| 8 | Buka Rincian #456 | **SKIP** — L2 (DB) tangkap hash=C |

**Logcat yang diharapkan**:

```
Step 6: Dedup L2 (database): skip hash=A               ← DB tangkap!
Step 7: Dedup L2 (database): skip hash=B               ← DB tangkap!
Step 8: Dedup L2 (database): skip hash=C               ← DB tangkap!
```

**✅ Pass jika**: Tidak ada capture baru di sesi 2, total tetap 3

---

## Skenario 3 — Konten Berubah = Bukan Duplikat

**Tujuan**: Pastikan halaman yang **judulnya sama tapi isinya berubah** tetap ter-capture.

| Step | Aksi | Expected |
|---|---|---|
| 1 | Buka Beranda (ada 2 pesanan aktif) | Ter-capture (hash=X) |
| 2 | Selesaikan 1 pesanan | — |
| 3 | Buka Beranda lagi (sekarang 1 pesanan aktif) | **Ter-capture** (hash=Y, beda dari X) |

**✅ Pass jika**: Capture baru masuk karena hash berbeda (konten layar berubah)

---

## Skenario 4 — Klik Event Tidak Terpengaruh Dedup

**Tujuan**: Pastikan klik tetap direkam meskipun di elemen yang sama.

| Step | Aksi | Expected |
|---|---|---|
| 1 | Tap tombol "Terima Pesanan" | VIEW_CLICKED ter-capture |
| 2 | Back, lalu tap "Terima Pesanan" lagi | VIEW_CLICKED ter-capture **lagi** |
| 3 | Ulangi 3x | Semua 3 klik terekam |

**✅ Pass jika**: Semua klik terekam, tidak ada yang di-skip

---

## Skenario 5 — Migration v3 → v4 (Existing Data)

**Tujuan**: Pastikan data lama (sebelum v4) tidak rusak dan tidak false-match.

| Step | Aksi | Expected |
|---|---|---|
| 1 | Install build **lama** (v3, sebelum PR #13) | — |
| 2 | Capture beberapa halaman | Records tersimpan tanpa `content_hash` |
| 3 | Install build **baru** (v4, setelah PR #13) di atas | Migration v3→v4 berjalan |
| 4 | Buka app SF Screen Reader | App tidak crash |
| 5 | Cek data lama di list | Semua records lama tetap ada, `content_hash=0` |
| 6 | Buka halaman yang **sama** seperti step 2 | **Ter-capture** (karena records lama hash=0, bukan match) |
| 7 | Buka halaman yang sama **lagi** | **SKIP** (sekarang ada record baru dengan hash valid) |

**✅ Pass jika**: No crash, data lama utuh, dedup mulai aktif setelah record baru masuk

---

## Skenario 6 — Ring Buffer Eviction + DB Fallback

**Tujuan**: Pastikan DB menangkap duplikat setelah ring buffer penuh (>10).

| Step | Aksi | Expected |
|---|---|---|
| 1 | Buka 12 halaman **berbeda** berturut-turut | 12 capture masuk DB |
| 2 | Kembali ke halaman **pertama** | Ring buffer sudah evict hash #1 |
| 3 | — | L1 miss → L2 (DB) **HIT** → SKIP |

**Logcat yang diharapkan**:

```
Step 2: Dedup L2 (database): skip hash=<hash halaman pertama>
```

**✅ Pass jika**: Halaman pertama tidak terekam ulang meskipun sudah di-evict dari ring buffer

---

## Skenario 7 — Rapid-Fire Stress Test

**Tujuan**: Pastikan performa tetap baik dengan hybrid dedup.

| Step | Aksi | Expected |
|---|---|---|
| 1 | Navigasi cepat bolak-balik 5 halaman selama 2 menit | — |
| 2 | Cek jumlah capture | Hanya 5 unique captures (bukan 50+) |
| 3 | Cek app responsiveness | Tidak lag, tidak ANR |
| 4 | Cek memory (Android Profiler) | Tidak ada memory leak |

**✅ Pass jika**: ≤5 captures, no ANR, no memory spike

---

## Skenario 8 — Delete All + Fresh Start

**Tujuan**: Setelah user clear semua data, dedup reset.

| Step | Aksi | Expected |
|---|---|---|
| 1 | Capture halaman Beranda | Masuk DB (hash=A) |
| 2 | Buka lagi | SKIP (dedup) |
| 3 | Tekan **Delete All** di app | DB kosong |
| 4 | Buka halaman Beranda **lagi** | **Ter-capture** (hash=A sudah tidak ada di DB) |

**✅ Pass jika**: Setelah delete all, halaman bisa ter-capture ulang

---

## Checklist Ringkas

| # | Skenario | Tes Apa | Kriteria Pass | Result |
|---|---|---|---|---|
| 1 | Baseline | Capture normal + L1 dedup | Halaman unik masuk, duplikat skip | ⬜ |
| 2 | Cross-session | L2 (DB) dedup | Duplikat lintas sesi skip | ⬜ |
| 3 | Konten berubah | Hash berbeda = bukan duplikat | Halaman sama tapi isi beda tetap masuk | ⬜ |
| 4 | Klik event | Tidak terpengaruh dedup | Semua klik terekam | ⬜ |
| 5 | Migration | v3→v4 upgrade | No crash, data lama utuh | ⬜ |
| 6 | Buffer eviction | L1 miss → L2 catch | DB tangkap setelah ring buffer penuh | ⬜ |
| 7 | Stress test | Performa | No ANR, memory stabil | ⬜ |
| 8 | Delete all | Reset dedup | Capture ulang setelah clear | ⬜ |

> Isi kolom **Result** dengan ✅ (pass) atau ❌ (fail) + catatan saat testing.
