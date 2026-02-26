package com.driversfpoc.screenreader.data.model

/**
 * Data class yang merepresentasikan satu node dalam accessibility tree.
 *
 * Digunakan oleh ScreenReaderService saat traverse node tree untuk
 * mengumpulkan informasi setiap elemen UI di layar target app.
 * Data ini diserialisasi ke JSON dan disimpan di CaptureRecord.nodeTreeJson.
 *
 * @property className Nama class View (misal: android.widget.TextView)
 * @property text Teks yang ditampilkan oleh node
 * @property contentDescription Accessibility content description
 * @property resourceId Resource ID dari view (misal: com.shopee:id/tv_price)
 * @property boundsLeft Koordinat kiri node di layar
 * @property boundsTop Koordinat atas node di layar
 * @property boundsRight Koordinat kanan node di layar
 * @property boundsBottom Koordinat bawah node di layar
 * @property childCount Jumlah child node
 * @property depth Kedalaman node dalam tree (root = 0)
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
