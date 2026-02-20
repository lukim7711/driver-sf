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
import com.driversfpoc.screenreader.ui.MainActivity
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors

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
 * Deduplication strategy:
 * - STATE_CHANGED (PAGE): selalu direkam, reset suppress window
 * - CONTENT_CHANGED (UPDATE): di-suppress 1.5 detik setelah STATE_CHANGED,
 *   di-debounce 800ms, dan di-cek prefix similarity (100 char)
 * - Klik: selalu direkam tanpa debounce
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
         * Panjang prefix yang digunakan untuk similarity check.
         * Jika 100 karakter pertama sama, konten dianggap duplikat
         * meskipun total karakter sedikit berbeda.
         */
        private const val SIMILARITY_PREFIX_LENGTH = 100

        var isRunning = false
            private set

        var captureCountToday = 0
            private set
    }

    private lateinit var repository: CaptureRepository
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val executor = Executors.newSingleThreadExecutor()
    private var lastCaptureTime = 0L
    private var lastCaptureHash = 0
    private var lastCapturePrefix = ""
    private var lastStateChangeTime = 0L

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
                Log.d(TAG, "Today count loaded: $captureCountToday")
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
                captureFullSnapshot(event, "WINDOW_STATE_CHANGED")
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

                captureFullSnapshot(event, "WINDOW_CONTENT_CHANGED")
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
        captureCountToday = 0
    }

    // ──────────────────────────────────────────────
    // Click Event Capture
    // ──────────────────────────────────────────────

    /**
     * Merekam detail elemen yang diklik oleh user.
     * Tidak menggunakan debounce karena setiap klik adalah aksi penting.
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
            captureCountToday++

            Log.d(TAG, "Click captured: ${source.text} [${source.viewIdResourceName}]")

            try {
                updateNotification()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update notification", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing click event", e)
        }
    }

    // ──────────────────────────────────────────────
    // Full Snapshot Capture
    // ──────────────────────────────────────────────

    /**
     * Menangkap snapshot seluruh teks di layar.
     * Digunakan untuk WINDOW_STATE_CHANGED dan WINDOW_CONTENT_CHANGED.
     *
     * Deduplication:
     * 1. Hash check: exact match → skip
     * 2. Prefix check: 100 char pertama sama → skip
     *    (menangkap kasus dimana total chars beda tipis, misal 522 vs 540)
     */
    private fun captureFullSnapshot(event: AccessibilityEvent, eventType: String) {
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

            // Dedup 1: Exact hash match
            val currentHash = plainText.hashCode()
            if (currentHash == lastCaptureHash) {
                Log.d(TAG, "Skipped: exact hash match")
                return
            }

            // Dedup 2: Prefix similarity check
            // Menangkap kasus dimana Android merender sedikit beda
            // (misal loading indicator muncul/hilang) tapi halaman sama
            val currentPrefix = plainText.take(SIMILARITY_PREFIX_LENGTH)
            if (currentPrefix == lastCapturePrefix && currentPrefix.isNotBlank()) {
                Log.d(TAG, "Skipped: prefix match (first ${SIMILARITY_PREFIX_LENGTH} chars identical)")
                // Update hash supaya future exact match juga bisa catch
                lastCaptureHash = currentHash
                return
            }

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
            lastCaptureHash = currentHash
            lastCapturePrefix = currentPrefix
            captureCountToday++

            Log.d(TAG, "Captured #$captureCountToday [$eventType]: ${plainText.take(50)}...")

            try {
                updateNotification()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update notification", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    // ──────────────────────────────────────────────
    // Node Tree Traversal
    // ──────────────────────────────────────────────

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
                traverseNode(child, result, depth + 1)
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
            .setContentText(getString(R.string.notification_text, captureCountToday))
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

        captureCountToday = repository.getCountSince(todayStart)
    }
}

data class NodeData(
    val className: String,
    val text: String,
    val contentDescription: String,
    val resourceId: String,
    val boundsLeft: Int,
    val boundsTop: Int,
    val boundsRight: Int,
    val boundsBottom: Int,
    val childCount: Int,
    val depth: Int
)
