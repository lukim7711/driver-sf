# PoC-001: Screen Reader — Buktikan App Bisa Membaca Layar Shopee Driver

---

## 1. Tujuan

Membuktikan **satu hal saja**: apakah aplikasi Android bisa membaca teks yang tampil di layar ShopeeFood Driver (`com.shopee.foody.driver.id`) menggunakan Accessibility Service.

**Bukan** untuk:
- ❌ Mengolah data secara otomatis
- ❌ Mengenali tipe halaman
- ❌ Menyimpan data ke database yang rapi
- ❌ Menampilkan dashboard atau ringkasan

**Hanya** untuk:
- ✅ Menangkap semua teks di layar saat driver buka ShopeeFood Driver
- ✅ Menyimpan hasil tangkapan sebagai log
- ✅ Menampilkan log supaya bisa dicek — apakah datanya sesuai kenyataan?

---

## 2. Apa yang App Ini Lakukan

Secara sederhana:

```
Driver buka ShopeeFood Driver
        ↓
App PoC diam-diam membaca semua teks di layar
        ↓
Teks disimpan sebagai log (dengan waktu)
        ↓
Driver buka app PoC → lihat log → cek apakah data benar
```

App ini seperti "perekam teks layar" — merekam semua tulisan yang muncul di ShopeeFood Driver, tanpa mengubah atau mengolah apapun.

---

## 3. Alur Penggunaan

### Setup (1x saja)

1. Driver install app PoC
2. Buka app → muncul 1 tombol: **"Aktifkan Screen Reader"**
3. Driver tap → diarahkan ke Settings Android → Accessibility
4. Aktifkan service app PoC
5. Kembali ke app → muncul status: **"✅ Screen Reader Aktif"**

### Penggunaan (tanpa interaksi)

1. Driver buka ShopeeFood Driver seperti biasa
2. App PoC berjalan di belakang layar — **driver tidak perlu melakukan apa-apa**
3. Setiap kali layar ShopeeFood Driver berubah (buka halaman, scroll, pindah screen), app PoC menangkap semua teks
4. Notifikasi tetap di status bar: **"Screen Reader aktif — [jumlah] tangkapan hari ini"**

### Review (kapan saja)

1. Driver buka app PoC
2. Muncul daftar semua tangkapan, diurutkan dari yang terbaru
3. Driver tap salah satu tangkapan → lihat semua teks yang tertangkap
4. Driver bandingkan: **apakah teks ini sesuai dengan apa yang saya lihat di ShopeeFood Driver?**

---

## 4. Tampilan App

App hanya punya **2 halaman**:

### Halaman 1 — Daftar Tangkapan

```
Screen Reader — ShopeeFood Driver
Status: ✅ Aktif
Hari ini: 47 tangkapan

─────────────────────────────

[16:42:03] Tangkapan #47
"Trip #6VUS — Order Details — Komplek Taman..."
Teks: 285 karakter

[16:41:55] Tangkapan #46
"Daftar Pesanan — 3 pesanan — SPX Instant..."
Teks: 142 karakter

[16:41:30] Tangkapan #45
"Rp15.200 — Jarak 3.2 km — 00:12..."
Teks: 89 karakter

[16:39:12] Tangkapan #44
"Riwayat Pesanan — 10 Feb 2026..."
Teks: 520 karakter

... (scroll ke bawah untuk tangkapan sebelumnya)

─────────────────────────────

[Tombol] Hapus Semua Log
[Tombol] Nonaktifkan Screen Reader
```

Setiap item menampilkan:
- Waktu tangkapan (jam:menit:detik)
- Nomor urut
- Preview teks (50 karakter pertama)
- Jumlah total karakter yang tertangkap

### Halaman 2 — Detail Tangkapan (tap salah satu item)

```
Tangkapan #47
Waktu: 16:42:03 — 18 Feb 2026
Karakter: 285
App: com.shopee.foody.driver.id

──── TEKS YANG TERTANGKAP ────

Trip #6VUS
Order Details
Pickup
Komplek Taman Duta Mas, Jl. Kusuma IV C
Blok D6 No. 14, Grogol Petamburan,
Jakarta Barat
Delivery
Jl. Kampung Norogtok, RT.8/RW.5,
Nerogtog, Kota Tangerang, Banten
Parcel
1.2 kg, 30x25x4 cm
Payment
Cash (COD)
No. Pesanan
260210BVSAY2V2

──── NODE TREE (untuk developer) ────

[Tombol] Tampilkan/Sembunyikan Node Tree

(jika ditampilkan, muncul struktur teknis
elemen layar — ini untuk developer, driver
tidak perlu membaca ini)

──────────────────────────────

[Tombol] Salin Teks
[Tombol] Kembali
```

---

## 5. Aturan Penting

### Yang harus dilakukan app:
- **Hanya membaca** dari ShopeeFood Driver (`com.shopee.foody.driver.id`) — abaikan app lain
- **Menangkap setiap perubahan layar** — bukan hanya saat halaman baru terbuka, tapi juga saat scroll (karena beberapa halaman ShopeeFood panjang dan perlu di-scroll)
- **Menyimpan 2 versi data per tangkapan**:
  - Teks biasa (semua tulisan yang tampil, urut dari atas ke bawah) — ini yang driver baca
  - Node tree / struktur elemen (JSON) — ini untuk developer analisa nanti
- **Berjalan di belakang layar** tanpa mengganggu ShopeeFood Driver
- **Tetap hidup** meskipun driver pindah-pindah app (gunakan Foreground Service dengan notifikasi tetap)

### Yang TIDAK boleh dilakukan app:
- ❌ Mengolah atau mengubah teks yang tertangkap
- ❌ Mengirim data ke internet (semuanya lokal di HP)
- ❌ Membaca app selain ShopeeFood Driver
- ❌ Melambatkan performa HP atau ShopeeFood Driver

---

## 6. Checklist Pengujian

Pengujian dibagi 2 tahap supaya bisa dibuktikan secepat mungkin.

### TAHAP 1 — Uji Sekarang (tanpa on-bid)

Halaman-halaman ini bisa dibuka kapan saja di ShopeeFood Driver, tanpa perlu sedang menerima order.

| # | Halaman | Cara membukanya | Data yang harus tertangkap | Hasil |
|---|---------|----------------|---------------------------|-------|
| 1 | **Riwayat Pesanan** (list trip) | Menu Riwayat di app | Waktu trip, tipe layanan, pendapatan per trip, label "Pesanan Gabungan" | ☐ Ada / ☐ Tidak |
| 2 | **Rincian Pesanan** (detail 1 trip) | Tap salah satu trip di Riwayat | Biaya pengantaran, total pendapatan, bonus poin, per-pesanan: kode pesanan, tagih tunai, penyesuaian, timeline (Diterima/Tiba/Diambil/Selesai + jam) | ☐ Ada / ☐ Tidak |
| 3 | **Halaman Utama / Home** | Buka app | Apapun yang tampil di home — tombol Mulai, status, dll | ☐ Ada / ☐ Tidak |
| 4 | **Profil / Pengaturan** | Tap menu Profil | Nama, rating, info akun | ☐ Ada / ☐ Tidak |
| 5 | **Scroll halaman panjang** | Buka Rincian Pesanan yang punya banyak pesanan, scroll ke bawah | Teks yang baru muncul setelah scroll juga tertangkap | ☐ Ada / ☐ Tidak |

**Keputusan setelah Tahap 1:**
- ✅ **Lanjut ke Tahap 2** jika: minimal 3 dari 5 halaman di atas terbaca lengkap dan benar
- ❌ **Stop, evaluasi ulang** jika: hampir semua halaman kosong atau teksnya tidak bisa dibaca

### TAHAP 2 — Uji Saat Kerja (on-bid, ada order masuk)

Halaman-halaman ini hanya muncul saat driver sedang aktif bekerja dan menerima order.

| # | Halaman | Kapan munculnya | Data yang harus tertangkap | Hasil |
|---|---------|----------------|---------------------------|-------|
| 6 | **Pop-up order masuk** (autobid) | Saat order masuk & autobid aktif | Angka argo, tombol OK | ☐ Ada / ☐ Tidak |
| 7 | **Pop-up order masuk** (manual) | Saat order masuk & autobid mati | Angka argo, jarak km, countdown 12 detik | ☐ Ada / ☐ Tidak |
| 8 | **Daftar Pesanan** (list dalam trip) | Setelah terima order | Jumlah pesanan, nama seller per pesanan | ☐ Ada / ☐ Tidak |
| 9 | **Order Details** (detail pesanan) | Tap pesanan di Daftar Pesanan | Trip code, alamat pickup lengkap, alamat delivery lengkap, berat, dimensi, COD/non-COD, kode pesanan (Order SN) | ☐ Ada / ☐ Tidak |
| 10 | **Halaman Pickup** | Sampai di lokasi pickup | Nama seller, alamat, tombol konfirmasi | ☐ Ada / ☐ Tidak |
| 11 | **Halaman Delivery** | Sampai di lokasi antar | Nama customer, alamat, tombol selesai | ☐ Ada / ☐ Tidak |

### Catatan Per Halaman

Untuk setiap halaman yang diuji (Tahap 1 maupun 2), catat:
- **Lengkap atau tidak?** — apakah semua teks yang kamu lihat di layar juga muncul di log?
- **Urut atau acak?** — apakah urutan teks di log masuk akal (atas ke bawah)?
- **Ada yang hilang?** — apakah ada teks yang kamu lihat di layar tapi TIDAK muncul di log?
- **Ada yang aneh?** — apakah ada teks asing / kode aneh yang muncul di log tapi tidak kamu lihat di layar?

### Situasi Khusus (uji saat Tahap 2)

| # | Situasi | Yang dicek |
|---|---------|-----------|
| 1 | Pindah cepat antar halaman (tap, back, tap lagi) | Apakah semua halaman tertangkap atau ada yang terlewat? |
| 2 | ShopeeFood Driver di-minimize lalu dibuka lagi | Apakah app PoC masih aktif membaca? |
| 3 | HP terkunci lalu dibuka, lanjut kerja | Apakah app PoC masih aktif? |
| 4 | Order masuk saat sedang di app lain | Apakah pop-up order tertangkap? |

---

## 7. Kriteria Keberhasilan

### Tahap 1 (uji sekarang):
- ✅ **Lanjut** = minimal 3 dari 5 halaman terbaca lengkap
- ❌ **Stop** = hampir semua kosong atau tidak bisa dibaca

### Tahap 2 (uji saat kerja):
- ✅ **PoC Berhasil** = minimal 4 dari 6 halaman terbaca lengkap + app tidak bikin ShopeeFood lemot
- ⚠️ **Sebagian Berhasil** = beberapa halaman terbaca, beberapa tidak — perlu analisa lebih lanjut
- ❌ **PoC Gagal** = hampir semua halaman on-bid kosong atau crash

### Keseluruhan:
- ✅ **Total Berhasil** = Tahap 1 lulus + Tahap 2 lulus → **lanjut tulis spec berdasarkan data nyata**
- ⚠️ **Sebagian** = ada halaman tertentu yang gagal → **sesuaikan scope fitur, cari solusi per halaman**
- ❌ **Gagal Total** = Accessibility Service tidak bisa membaca ShopeeFood Driver → **evaluasi pendekatan lain**

---

## 8. Info Teknis (untuk Builder)

### Teknologi
- **Android Accessibility Service** — API resmi Android untuk membaca elemen layar app lain
- Wajib deklarasi di `AndroidManifest.xml` sebagai `AccessibilityService`
- Perlu file konfigurasi `accessibility_service_config.xml`

### Konfigurasi Accessibility Service
- `android:accessibilityEventTypes`: `typeWindowStateChanged|typeWindowContentChanged`
- `android:accessibilityFeedbackType`: `feedbackGeneric`
- `android:notificationTimeout`: `100` (ms)
- `android:canRetrieveWindowContent`: `true`
- `android:packageNames`: `com.shopee.foody.driver.id`

### Cara menangkap teks
1. Override `onAccessibilityEvent(event)`
2. Ambil root node: `event.getSource()` atau `getRootInActiveWindow()`
3. Traverse seluruh node tree secara rekursif
4. Untuk setiap node: ambil `node.getText()` dan `node.getContentDescription()`
5. Simpan:
   - Teks biasa: gabungan semua getText() urut berdasarkan posisi (bounds) dari atas ke bawah, kiri ke kanan
   - Node tree JSON: setiap node disimpan dengan properti: `className`, `text`, `contentDescription`, `resourceId` (jika ada), `bounds`, `childCount`

### Penyimpanan
- Cukup pakai **SQLite sederhana** atau bahkan **file JSON** lokal
- 1 record per tangkapan:
  - `id` (auto increment)
  - `timestamp` (waktu tangkapan, ISO 8601)
  - `plain_text` (teks biasa, gabungan semua teks di layar)
  - `node_tree_json` (struktur lengkap untuk analisa developer)
  - `event_type` (tipe event: WINDOW_STATE_CHANGED atau WINDOW_CONTENT_CHANGED)
  - `text_length` (jumlah karakter plain_text)

### Foreground Service
- Wajib agar Android tidak matikan service di belakang layar
- Persistent notification menampilkan: "Screen Reader aktif — [jumlah] tangkapan hari ini"

### Batasan
- Tidak butuh koneksi internet
- Tidak butuh permission tambahan selain Accessibility Service
- Target Android: API 24+ (Android 7.0+) — sesuai dengan range HP mid-range yang umum

### Estimasi ukuran app
- Sangat kecil — di bawah 5 MB
- Tidak ada library besar yang dibutuhkan

---

## 9. Setelah PoC Selesai

Hasil PoC akan menentukan langkah selanjutnya:

| Hasil | Langkah Berikutnya |
|-------|-------------------|
| **✅ Berhasil** | Tulis ulang spec F001/F002/F003 berdasarkan **data nyata** dari log PoC — kita tahu persis halaman apa saja yang bisa dibaca dan field apa saja yang tersedia |
| **⚠️ Sebagian** | Analisa halaman mana yang gagal dan kenapa — cari solusi khusus untuk halaman tersebut, atau sesuaikan scope fitur |
| **❌ Gagal** | Accessibility Service tidak cocok untuk ShopeeFood Driver — evaluasi pendekatan lain (screenshot + OCR, atau pendekatan hybrid) |

Apapun hasilnya, PoC ini memberikan **jawaban pasti berbasis fakta** — bukan asumsi.
