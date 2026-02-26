package com.driversfpoc.screenreader.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.driversfpoc.screenreader.R
import com.driversfpoc.screenreader.ScreenReaderApp
import com.driversfpoc.screenreader.data.CaptureRepository
import com.driversfpoc.screenreader.data.model.CaptureRecord
import com.driversfpoc.screenreader.data.model.NodeData
import com.driversfpoc.screenreader.ui.MainActivity
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * AccessibilityService yang membaca semua teks di layar ShopeeFood Driver.
 *
 * Cara kerja:
 * 1. Mendengarkan event WINDOW_STATE_CHANGED, WINDOW_CONTENT_CHANGED,
 *    VIEW_CLICKED, dan VIEW_SELECTED dari package com.shopee.foody.driver.id
 * 2. Untuk klik: merekam detail elemen yang diklik (tanpa debounce)
 * 3. Untuk window/content change: traverse seluruh node tree secara rekursif
 * 4. Menyimpan plain text (urut by bounds) + node tree JSON ke database
 *
 * Deduplication strategy (v2 — ring buffer):
 * - STATE_CHANGED (PAGE): selalu direkam, reset suppress window
 * - CONTENT_CHANGED (UPDATE): di-suppress 1.5 detik setelah STATE_CHANGED,
 *   di-debounce 800ms, dan di-cek via ring buffer hash (10 capture terakhir)
 * - Klik: selalu direkam tanpa debounce
 *
 * Resource management:
 * - Semua AccessibilityNodeInfo di-recycle() pada Android < 14 (API 34)
 *   untuk mencegah memory leak. Pada API 34+ recycle() sudah deprecated
 *   karena system mengelola lifecycle secara otomatis.
 * - Regex di-compile sekali sebagai companion object constant
 */
class ScreenReaderService : AccessibilityService() {

    companion object {
        private const val TAG = "ScreenReaderService"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val TARGET_PACKAGE = "com.shopee.foody.driver.id"

        /**
         * Debounce untuk CONTENT_CHANGED events.
         * Dinaikkan dari 300ms ke 800ms untuk mengurangi noise dari
         * minor UI updates (animasi, loading indicator, dll).
         */
        private const val DEBOUNCE_CONTENT_MS = 800L

        /**
         * Suppress window: setelah STATE_CHANGED (page transition),
         * semua CONTENT_CHANGED events di-suppress selama periode ini.
         *
         * Alasan: Android fire kedua event hampir bersamaan saat buka
         * halaman baru, menghasilkan duplikat UPDATE + PAGE.
         */
        private const val STATE_CHANGE_SUPPRESS_MS = 1500L

        /**
         * Jumlah hash yang disimpan dalam ring buffer untuk dedup.
         * Menangkap duplikat dari navigasi bolak-balik antar halaman.
         * Misal: Halaman A → B → Peta → B (kembali)
         * Dengan ring buffer size 10, hash B masih tersimpan sehingga
         * duplikat terdeteksi meskipun ada halaman lain di antaranya.
         */
        private const val DEDUP_HISTORY_SIZE = 10

        /**
         * Pre-compiled regex untuk normalizeText().
         * Di-compile sekali saat class di-load, bukan setiap pemanggilan.
         * Menghindari overhead compile + alokasi Regex object berulang
         * pada service yang bisa dipanggil ratusan kali per sesi.
         */
        private val WHITESPACE_REGEX = Regex("\\s+")

        var isRunning = false
            private set

        /** Thread-safe counter menggunakan AtomicInteger */
        private val _captureCountToday = AtomicInteger(0)
        val captureCountToday: Int get() = _captureCountToday.get()
    }

    private lateinit var repository: CaptureRepository
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val executor = Executors.newSingleThreadExecutor()
    private var lastCaptureTime = 0L
    private var lastStateChangeTime = 0L

    /**
     * Ring buffer menyimpan N hash terakhir dari capture yang berhasil disimpan.
     * Menggunakan LinkedHashSet untuk:
     * - O(1) lookup (contains check)
     * - Menjaga insertion order (FIFO eviction)
     * - Otomatis deduplikasi hash yang sama
     */
    private val recentHashes = LinkedHashSet<Int>()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        repository = CaptureRepository.getInstance(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected")
        isRunning = true

        try {
            startForegroundNotification()
            Log.d(TAG, "Foreground notification started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground notification", e)
        }

        executor.execute {
            try {
                loadTodayCount()
                Log.d(TAG, "Today count loaded: ${_captureCountToday.get()}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load today count", e)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName != TARGET_PACKAGE) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                // Klik/Select: rekam elemen yang diklik — TANPA debounce
                captureClickEvent(event)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Pindah halaman: selalu rekam full snapshot
                val now = System.currentTimeMillis()
                lastStateChangeTime = now
                captureFullSnapshot("WINDOW_STATE_CHANGED")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = System.currentTimeMillis()

                // Guard 1: Suppress setelah page transition
                // STATE_CHANGED sudah capture halaman ini
                if (now - lastStateChangeTime < STATE_CHANGE_SUPPRESS_MS) {
                    Log.d(TAG, "CONTENT_CHANGED suppressed (within ${STATE_CHANGE_SUPPRESS_MS}ms of STATE_CHANGED)")
                    return
                }

                // Guard 2: Debounce rapid-fire content updates
                if (now - lastCaptureTime < DEBOUNCE_CONTENT_MS) {
                    return
                }

                captureFullSnapshot("WINDOW_CONTENT_CHANGED")
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        isRunning = false
        _captureCountToday.set(0)
        recentHashes.clear()
    }

    // ──────────────────────────────────────────────
    // Node Recycle Helper
    // ──────────────────────────────────────────────

    /**
     * Safely recycle AccessibilityNodeInfo.
     *
     * Pada Android 14+ (API 34), recycle() sudah deprecated karena
     * system mengelola lifecycle secara otomatis. Pada versi sebelumnya,
     * recycle() wajib dipanggil untuk mencegah memory leak.
     *
     * Method ini menangani kedua kasus tanpa menghasilkan deprecation warning.
     */
    private fun safeRecycle(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                @Suppress("DEPRECATION")
                node.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recycle node", e)
            }
        }
    }

    // ──────────────────────────────────────────────
    // Text Normalization
    // ──────────────────────────────────────────────

    /**
     * Normalisasi teks sebelum hashing untuk menangkap duplikat
     * yang secara visual identik tapi beda di karakter tak terlihat.
     *
     * Contoh kasus:
     * - "Total: Rp 45.000"  (1 spasi) vs "Total: Rp  45.000" (2 spasi)
     * - Trailing whitespace / newline berbeda
     * - Non-breaking space (\u00A0) vs regular space
     *
     * Menggunakan WHITESPACE_REGEX yang sudah di-compile di companion object.
     */
    private fun normalizeText(text: String): String {
        return text
            .trim()
            .replace('\u00A0', ' ')          // non-breaking space → regular space
            .replace('\u200B', ' ')          // zero-width space → space
            .replace('\u200C', ' ')          // zero-width non-joiner → space
            .replace('\u200D', ' ')          // zero-width joiner → space
            .replace('\uFEFF', ' ')          // BOM → space
            .replace(WHITESPACE_REGEX, " ")  // collapse semua whitespace jadi 1 spasi
    }

    // ──────────────────────────────────────────────
    // Ring Buffer Deduplication
    // ──────────────────────────────────────────────

    /**
     * Cek apakah teks sudah pernah di-capture dalam N capture terakhir.
     *
     * Menggunakan ring buffer (LinkedHashSet) berisi hash dari teks
     * yang sudah dinormalisasi. Jika hash ditemukan di buffer,
     * teks dianggap duplikat.
     *
     * Jika bukan duplikat, hash ditambahkan ke buffer dan entry
     * terlama di-evict jika buffer sudah penuh.
     *
     * @param normalizedText teks yang sudah di-normalize
     * @return true jika duplikat, false jika unik
     */
    private fun isDuplicate(normalizedText: String): Boolean {
        val hash = normalizedText.hashCode()

        if (hash in recentHashes) {
            Log.d(TAG, "Skipped: duplicate found in recent $DEDUP_HISTORY_SIZE captures (hash=$hash)")
            return true
        }

        // Tambah hash baru ke buffer
        recentHashes.add(hash)

        // Evict hash terlama jika buffer penuh (FIFO)
        if (recentHashes.size > DEDUP_HISTORY_SIZE) {
            val oldest = recentHashes.first()
            recentHashes.remove(oldest)
        }

        return false
    }

    // ──────────────────────────────────────────────
    // Click Event Capture
    // ──────────────────────────────────────────────

    /**
     * Merekam detail elemen yang diklik oleh user.
     * Tidak menggunakan debounce karena setiap klik adalah aksi penting.
     *
     * event.source di-recycle setelah data diekstrak untuk mencegah memory leak.
     */
    private fun captureClickEvent(event: AccessibilityEvent) {
        val source = try {
            event.source
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get event source", e)
            null
        } ?: return

        try {
            val bounds = Rect()
            source.getBoundsInScreen(bounds)

            val clickInfo = buildString {
                appendLine("TEXT: ${source.text ?: "(kosong)"}")
                appendLine("CLASS: ${source.className ?: "(null)"}")
                appendLine("ID: ${source.viewIdResourceName ?: "(null)"}")
                appendLine("DESC: ${source.contentDescription ?: "(null)"}")
                appendLine("BOUNDS: [${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]")
                appendLine("CLICKABLE: ${source.isClickable}")
                appendLine("ENABLED: ${source.isEnabled}")
                appendLine("CHECKED: ${source.isChecked}")
            }

            val eventType = when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED"
                AccessibilityEvent.TYPE_VIEW_SELECTED -> "VIEW_SELECTED"
                else -> "OTHER"
            }

            val record = CaptureRecord(
                timestamp = Instant.now().toString(),
                plainText = clickInfo.trim(),
                nodeTreeJson = "",
                eventType = eventType,
                textLength = clickInfo.trim().length
            )

            repository.insert(record)
            _captureCountToday.incrementAndGet()

            Log.d(TAG, "Click captured: ${source.text} [${source.viewIdResourceName}]")

            try {
                updateNotification()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update notification", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing click event", e)
        } finally {
            safeRecycle(source)
        }
    }

    // ──────────────────────────────────────────────
    // Full Snapshot Capture
    // ──────────────────────────────────────────────

    /**
     * Menangkap snapshot seluruh teks di layar.
     * Digunakan untuk WINDOW_STATE_CHANGED dan WINDOW_CONTENT_CHANGED.
     *
     * Deduplication (v2):
     * 1. Normalize teks (collapse whitespace, hapus invisible chars)
     * 2. Hash normalized text
     * 3. Cek hash terhadap ring buffer (10 capture terakhir)
     * 4. Jika ditemukan → skip (duplikat dari navigasi bolak-balik)
     * 5. Jika tidak → simpan dan tambahkan hash ke ring buffer
     *
     * Resource management:
     * - rootInActiveWindow di-recycle setelah traversal selesai (API < 34)
     * - Semua child node di-recycle di dalam traverseNode() (API < 34)
     */
    private fun captureFullSnapshot(eventType: String) {
        val rootNode = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get rootInActiveWindow", e)
            null
        } ?: return

        try {
            val nodeDataList = mutableListOf<NodeData>()
            traverseNode(rootNode, nodeDataList, 0)

            val sortedNodes = nodeDataList
                .filter { it.text.isNotBlank() || it.contentDescription.isNotBlank() }
                .sortedWith(compareBy({ it.boundsTop }, { it.boundsLeft }))

            val plainText = sortedNodes.joinToString("\n") { node ->
                buildString {
                    if (node.text.isNotBlank()) append(node.text)
                    if (node.contentDescription.isNotBlank()) {
                        if (isNotBlank()) append(" ")
                        append("[${node.contentDescription}]")
                    }
                }
            }.trim()

            if (plainText.isBlank()) return

            // Normalize + ring buffer dedup
            val normalizedText = normalizeText(plainText)
            if (isDuplicate(normalizedText)) return

            val nodeTreeJson = gson.toJson(nodeDataList)

            val record = CaptureRecord(
                timestamp = Instant.now().toString(),
                plainText = plainText,
                nodeTreeJson = nodeTreeJson,
                eventType = eventType,
                textLength = plainText.length
            )

            repository.insert(record)

            lastCaptureTime = System.currentTimeMillis()
            val count = _captureCountToday.incrementAndGet()

            Log.d(TAG, "Captured #$count [$eventType]: ${plainText.take(50)}...")

            try {
                updateNotification()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update notification", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        } finally {
            safeRecycle(rootNode)
        }
    }

    // ──────────────────────────────────────────────
    // Node Tree Traversal
    // ──────────────────────────────────────────────

    /**
     * Traverse node tree secara rekursif dan kumpulkan data setiap node.
     *
     * Setiap child node yang didapat dari node.getChild(i)
     * di-recycle via safeRecycle() setelah selesai di-traverse.
     * Pada API < 34 ini mencegah memory leak, pada API 34+ menjadi no-op.
     *
     * Root node TIDAK di-recycle di sini — tanggung jawab pemanggil
     * (captureFullSnapshot) untuk me-recycle root node.
     */
    private fun traverseNode(
        node: AccessibilityNodeInfo,
        result: MutableList<NodeData>,
        depth: Int
    ) {
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val nodeData = NodeData(
                className = node.className?.toString() ?: "",
                text = node.text?.toString() ?: "",
                contentDescription = node.contentDescription?.toString() ?: "",
                resourceId = node.viewIdResourceName ?: "",
                boundsLeft = bounds.left,
                boundsTop = bounds.top,
                boundsRight = bounds.right,
                boundsBottom = bounds.bottom,
                childCount = node.childCount,
                depth = depth
            )
            result.add(nodeData)

            for (i in 0 until node.childCount) {
                val child = try {
                    node.getChild(i)
                } catch (e: Exception) {
                    null
                } ?: continue

                try {
                    traverseNode(child, result, depth + 1)
                } finally {
                    safeRecycle(child)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error traversing node at depth $depth", e)
        }
    }

    // ──────────────────────────────────────────────
    // Foreground Notification
    // ──────────────────────────────────────────────

    private fun startForegroundNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ScreenReaderApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, _captureCountToday.get()))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────

    private fun loadTodayCount() {
        val todayStart = Instant.now()
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toString()

        _captureCountToday.set(repository.getCountSince(todayStart))
    }
}
