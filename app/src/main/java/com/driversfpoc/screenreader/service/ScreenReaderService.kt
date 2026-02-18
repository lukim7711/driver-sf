package com.driversfpoc.screenreader.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Rect
import android.os.Build
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
import java.time.format.DateTimeFormatter

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
    private var lastCaptureTime = 0L
    private var lastCaptureHash = 0

    override fun onCreate() {
        super.onCreate()
        repository = CaptureRepository.getInstance(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        loadTodayCount()
        startForegroundNotification()
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
        val rootNode = rootInActiveWindow ?: return

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
            if (plainText.isBlank()) {
                rootNode.recycle()
                return
            }

            // Skip jika teks sama persis dengan tangkapan sebelumnya (deduplicate)
            val currentHash = plainText.hashCode()
            if (currentHash == lastCaptureHash) {
                rootNode.recycle()
                return
            }

            // Build node tree JSON
            val nodeTreeJson = gson.toJson(nodeDataList)

            // Determine event type
            val eventType = when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
                else -> "OTHER"
            }

            // Simpan ke database
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

            // Update notification
            updateNotification()

        } finally {
            rootNode.recycle()
        }
    }

    override fun onInterrupt() {
        // Service interrupted — nothing to clean up
    }

    override fun onDestroy() {
        super.onDestroy()
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
            val child = node.getChild(i) ?: continue
            traverseNode(child, result, depth + 1)
            child.recycle()
        }
    }

    // ──────────────────────────────────────────────
    // Foreground Notification
    // ──────────────────────────────────────────────

    private fun startForegroundNotification() {
        val notification = buildNotification()
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
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
