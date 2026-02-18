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
 * 1. Mendengarkan event WINDOW_STATE_CHANGED dan WINDOW_CONTENT_CHANGED
 *    dari package com.shopee.foody.driver.id
 * 2. Saat event diterima, traverse seluruh node tree secara rekursif
 * 3. Mengambil getText() dan getContentDescription() dari setiap node
 * 4. Menyimpan plain text (urut by bounds) + node tree JSON ke database
 */
class ScreenReaderService : AccessibilityService() {

    companion object {
        private const val TAG = "ScreenReaderService"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val TARGET_PACKAGE = "com.shopee.foody.driver.id"
        private const val DEBOUNCE_MS = 300L

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

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        repository = CaptureRepository.getInstance(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected")
        isRunning = true

        // Start foreground notification FIRST (sebelum operasi lain)
        try {
            startForegroundNotification()
            Log.d(TAG, "Foreground notification started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground notification", e)
        }

        // Load today count di BACKGROUND thread (Room tidak boleh di main thread)
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

        // Hanya proses event dari ShopeeFood Driver
        val packageName = event.packageName?.toString() ?: return
        if (packageName != TARGET_PACKAGE) return

        // Debounce: abaikan event yang terlalu cepat
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < DEBOUNCE_MS) return

        // Ambil root node
        val rootNode = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get rootInActiveWindow", e)
            null
        } ?: return

        try {
            // Traverse node tree
            val nodeDataList = mutableListOf<NodeData>()
            traverseNode(rootNode, nodeDataList, 0)

            // Extract plain text, sorted by bounds (top-to-bottom, left-to-right)
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

            // Skip jika teks kosong
            if (plainText.isBlank()) return

            // Skip jika teks sama persis dengan tangkapan sebelumnya (deduplicate)
            val currentHash = plainText.hashCode()
            if (currentHash == lastCaptureHash) return

            // Build node tree JSON
            val nodeTreeJson = gson.toJson(nodeDataList)

            // Determine event type
            val eventType = when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
                else -> "OTHER"
            }

            // Simpan ke database (di background thread via repository)
            val record = CaptureRecord(
                timestamp = Instant.now().toString(),
                plainText = plainText,
                nodeTreeJson = nodeTreeJson,
                eventType = eventType,
                textLength = plainText.length
            )

            repository.insert(record)

            // Update state
            lastCaptureTime = now
            lastCaptureHash = currentHash
            captureCountToday++

            Log.d(TAG, "Captured #$captureCountToday: ${plainText.take(50)}...")

            // Update notification di background
            try {
                updateNotification()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update notification", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
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
    // Node Tree Traversal
    // ──────────────────────────────────────────────

    /**
     * Traverse node tree secara rekursif.
     * Setiap node diambil: className, text, contentDescription,
     * resourceId, bounds, dan childCount.
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

            // Recursive: traverse semua child nodes
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
            // Android 14+ requires foreground service type
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

/**
 * Data class untuk menyimpan informasi setiap node dalam tree.
 * Digunakan untuk plain text extraction dan JSON serialization.
 */
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
