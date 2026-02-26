package com.driversfpoc.screenreader.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.driversfpoc.screenreader.data.model.CaptureRecord
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Instrumented tests untuk fitur DB-level dedup.
 *
 * Menggunakan in-memory Room database agar:
 * - Cepat (tidak ada disk I/O)
 * - Isolated (setiap test mulai dari DB kosong)
 * - Tidak perlu migration (langsung create latest schema)
 *
 * Test coverage:
 * - Skenario 2: Cross-session dedup (hash match di DB)
 * - Skenario 3: Konten berubah = bukan duplikat (hash berbeda)
 * - Skenario 5: Legacy records (hash=0) tidak false-match
 * - Skenario 6: Ring buffer eviction + DB fallback (hash tetap di DB)
 * - Skenario 8: Delete all resets dedup
 */
@RunWith(AndroidJUnit4::class)
class DedupDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CaptureDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()  // OK for tests
            .build()
        dao = db.captureDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ──────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────

    private fun createRecord(
        plainText: String = "test text",
        eventType: String = "WINDOW_STATE_CHANGED",
        contentHash: Int = plainText.hashCode()
    ): CaptureRecord {
        return CaptureRecord(
            timestamp = Instant.now().toString(),
            plainText = plainText,
            nodeTreeJson = "",
            eventType = eventType,
            textLength = plainText.length,
            contentHash = contentHash
        )
    }

    // ──────────────────────────────────────────────
    // Skenario 2: Cross-Session Dedup
    // ──────────────────────────────────────────────

    @Test
    fun sameHash_isDetectedAsDuplicate() {
        // Sesi 1: insert record dengan hash tertentu
        val record = createRecord(plainText = "Beranda ShopeeFood")
        dao.insert(record)

        // Sesi 2: cek apakah hash yang sama terdeteksi
        val count = dao.countByHash("Beranda ShopeeFood".hashCode())
        assertTrue("Hash yang sama harus terdeteksi sebagai duplikat", count > 0)
    }

    @Test
    fun multipleRecords_sameHash_countCorrectly() {
        // Ini seharusnya tidak terjadi di production (dedup mencegah),
        // tapi test bahwa query counting benar
        val hash = "Halaman Test".hashCode()
        dao.insert(createRecord(plainText = "Halaman Test", contentHash = hash))
        dao.insert(createRecord(plainText = "Halaman Test v2", contentHash = hash))

        val count = dao.countByHash(hash)
        assertEquals("Harus menghitung semua records dengan hash yang sama", 2, count)
    }

    // ──────────────────────────────────────────────
    // Skenario 3: Konten Berubah = Bukan Duplikat
    // ──────────────────────────────────────────────

    @Test
    fun differentHash_isNotDuplicate() {
        // Insert "Beranda dengan 2 pesanan"
        dao.insert(createRecord(
            plainText = "Beranda - 2 pesanan aktif",
            contentHash = "Beranda - 2 pesanan aktif".hashCode()
        ))

        // Cek "Beranda dengan 1 pesanan" — hash berbeda
        val differentHash = "Beranda - 1 pesanan aktif".hashCode()
        val count = dao.countByHash(differentHash)
        assertEquals("Hash berbeda harus return 0 (bukan duplikat)", 0, count)
    }

    @Test
    fun samePageTitle_differentContent_differentHash() {
        val page1 = "Riwayat Pesanan\n#456 DIMSUM\n#457 Ayam"
        val page2 = "Riwayat Pesanan\n#458 Burger\n#459 Pizza"

        // Halaman sama (Riwayat) tapi konten beda → hash beda
        assertNotEquals(
            "Konten berbeda harus menghasilkan hash berbeda",
            page1.hashCode(),
            page2.hashCode()
        )

        dao.insert(createRecord(plainText = page1, contentHash = page1.hashCode()))

        // page2 seharusnya BUKAN duplikat
        val count = dao.countByHash(page2.hashCode())
        assertEquals("Konten berbeda bukan duplikat", 0, count)
    }

    // ──────────────────────────────────────────────
    // Skenario 4: Klik Event Excluded dari Dedup
    // ──────────────────────────────────────────────

    @Test
    fun clickEvents_excludedFromDedupQuery() {
        // Insert click event dengan hash tertentu
        val hash = "TEXT: Terima Pesanan".hashCode()
        dao.insert(createRecord(
            plainText = "TEXT: Terima Pesanan",
            eventType = "VIEW_CLICKED",
            contentHash = hash
        ))

        // countByHash hanya cek snapshot events, bukan clicks
        val count = dao.countByHash(hash)
        assertEquals("Click events harus di-exclude dari dedup query", 0, count)
    }

    @Test
    fun selectedEvents_excludedFromDedupQuery() {
        val hash = "Item Selected".hashCode()
        dao.insert(createRecord(
            plainText = "Item Selected",
            eventType = "VIEW_SELECTED",
            contentHash = hash
        ))

        val count = dao.countByHash(hash)
        assertEquals("Selected events harus di-exclude dari dedup query", 0, count)
    }

    @Test
    fun snapshotEvents_includedInDedupQuery() {
        val hash = "Snapshot Page".hashCode()

        // STATE_CHANGED harus masuk
        dao.insert(createRecord(
            plainText = "Snapshot Page",
            eventType = "WINDOW_STATE_CHANGED",
            contentHash = hash
        ))
        assertTrue("STATE_CHANGED harus termasuk dalam dedup", dao.countByHash(hash) > 0)

        // CONTENT_CHANGED juga harus masuk
        val hash2 = "Content Update".hashCode()
        dao.insert(createRecord(
            plainText = "Content Update",
            eventType = "WINDOW_CONTENT_CHANGED",
            contentHash = hash2
        ))
        assertTrue("CONTENT_CHANGED harus termasuk dalam dedup", dao.countByHash(hash2) > 0)
    }

    // ──────────────────────────────────────────────
    // Skenario 5: Legacy Records (Migration v3→v4)
    // ──────────────────────────────────────────────

    @Test
    fun legacyRecord_hashZero_noFalseMatch() {
        // Record lama sebelum v4 punya content_hash = 0
        dao.insert(createRecord(
            plainText = "Legacy data",
            contentHash = 0  // default untuk record pre-v4
        ))

        // Query untuk hash=0 harus return 0 (filtered in DAO query)
        val count = dao.countByHash(0)
        assertEquals("Legacy records (hash=0) tidak boleh false-match", 0, count)
    }

    @Test
    fun newRecordAfterMigration_dedupWorks() {
        // Legacy record (hash=0) — tidak akan match
        dao.insert(createRecord(plainText = "Beranda", contentHash = 0))

        // New record dengan hash valid
        val validHash = "Beranda".hashCode()
        dao.insert(createRecord(plainText = "Beranda", contentHash = validHash))

        // Sekarang dedup harus bekerja untuk hash valid
        val count = dao.countByHash(validHash)
        assertTrue("Record baru dengan hash valid harus terdeteksi", count > 0)
    }

    // ──────────────────────────────────────────────
    // Skenario 6: Banyak Records, Hash Tetap di DB
    // ──────────────────────────────────────────────

    @Test
    fun manyRecords_firstHashStillDetectable() {
        // Simulasi: insert 15 record berbeda (melebihi ring buffer size=10)
        val firstHash = "Halaman Pertama".hashCode()
        dao.insert(createRecord(plainText = "Halaman Pertama", contentHash = firstHash))

        for (i in 2..15) {
            dao.insert(createRecord(
                plainText = "Halaman $i",
                contentHash = "Halaman $i".hashCode()
            ))
        }

        // Hash pertama masih ada di DB meskipun ring buffer sudah evict
        val count = dao.countByHash(firstHash)
        assertTrue("Hash pertama harus tetap terdeteksi di DB", count > 0)
    }

    @Test
    fun fiftyRecords_allHashesDetectable() {
        // Insert 50 record unik
        val hashes = mutableListOf<Int>()
        for (i in 1..50) {
            val text = "Unique Page Number $i"
            val hash = text.hashCode()
            hashes.add(hash)
            dao.insert(createRecord(plainText = text, contentHash = hash))
        }

        // Semua hash harus bisa dideteksi
        for (hash in hashes) {
            assertTrue(
                "Semua hash harus terdeteksi di DB, gagal untuk hash=$hash",
                dao.countByHash(hash) > 0
            )
        }
    }

    // ──────────────────────────────────────────────
    // Skenario 8: Delete All Resets Dedup
    // ──────────────────────────────────────────────

    @Test
    fun deleteAll_clearsAllHashes() {
        val hash = "Beranda".hashCode()
        dao.insert(createRecord(plainText = "Beranda", contentHash = hash))

        // Verify ada
        assertTrue("Hash harus ada sebelum delete", dao.countByHash(hash) > 0)

        // Delete all
        dao.deleteAll()

        // Verify hilang
        val count = dao.countByHash(hash)
        assertEquals("Setelah deleteAll, hash harus hilang", 0, count)
    }

    @Test
    fun deleteAll_allowsRecaptureOfSamePage() {
        val hash = "Rincian Pesanan #456".hashCode()

        // Insert → ada
        dao.insert(createRecord(plainText = "Rincian Pesanan #456", contentHash = hash))
        assertTrue(dao.countByHash(hash) > 0)

        // Delete all → hilang
        dao.deleteAll()
        assertEquals(0, dao.countByHash(hash))

        // Insert ulang → berhasil (bukan duplikat)
        dao.insert(createRecord(plainText = "Rincian Pesanan #456", contentHash = hash))
        assertTrue("Setelah deleteAll, record yang sama harus bisa masuk lagi", dao.countByHash(hash) > 0)
    }

    // ──────────────────────────────────────────────
    // Edge Cases
    // ──────────────────────────────────────────────

    @Test
    fun emptyDatabase_returnsZero() {
        val count = dao.countByHash(12345)
        assertEquals("DB kosong harus return 0", 0, count)
    }

    @Test
    fun negativeHash_worksCorrectly() {
        // hashCode() bisa menghasilkan nilai negatif
        val negativeHash = -987654321
        dao.insert(createRecord(plainText = "Negative", contentHash = negativeHash))

        val count = dao.countByHash(negativeHash)
        assertTrue("Hash negatif harus bekerja", count > 0)
    }

    @Test
    fun deleteOlderThan_partialCleanup() {
        // Insert 2 records: 1 lama, 1 baru
        val oldTimestamp = "2026-01-01T00:00:00Z"
        val newTimestamp = "2026-02-27T00:00:00Z"
        val hash1 = "Old Page".hashCode()
        val hash2 = "New Page".hashCode()

        dao.insert(CaptureRecord(
            timestamp = oldTimestamp,
            plainText = "Old Page",
            nodeTreeJson = "",
            eventType = "WINDOW_STATE_CHANGED",
            textLength = 8,
            contentHash = hash1
        ))
        dao.insert(CaptureRecord(
            timestamp = newTimestamp,
            plainText = "New Page",
            nodeTreeJson = "",
            eventType = "WINDOW_STATE_CHANGED",
            textLength = 8,
            contentHash = hash2
        ))

        // Delete records older than Feb 1
        dao.deleteOlderThan("2026-02-01T00:00:00Z")

        // Old hash hilang, new hash tetap
        assertEquals("Hash lama harus hilang setelah cleanup", 0, dao.countByHash(hash1))
        assertTrue("Hash baru harus tetap ada", dao.countByHash(hash2) > 0)
    }
}
