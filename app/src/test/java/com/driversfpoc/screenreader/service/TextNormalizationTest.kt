package com.driversfpoc.screenreader.service

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests untuk text normalization logic.
 *
 * Ini adalah pure JVM test (tidak butuh emulator) yang memverifikasi
 * bahwa normalizeText() menghasilkan output konsisten untuk input
 * yang secara visual identik.
 *
 * Karena normalizeText() adalah private method di ScreenReaderService,
 * kita test logika yang sama secara independen.
 */
class TextNormalizationTest {

    /**
     * Replika dari ScreenReaderService.normalizeText().
     * Dijaga sync dengan method asli.
     */
    private val whitespaceRegex = Regex("\\s+")

    private fun normalizeText(text: String): String {
        return text
            .trim()
            .replace('\u00A0', ' ')          // non-breaking space
            .replace('\u200B', ' ')          // zero-width space
            .replace('\u200C', ' ')          // zero-width non-joiner
            .replace('\u200D', ' ')          // zero-width joiner
            .replace('\uFEFF', ' ')          // BOM
            .replace(whitespaceRegex, " ")
    }

    @Test
    fun sameText_sameHash() {
        val a = normalizeText("Total: Rp 45.000")
        val b = normalizeText("Total: Rp 45.000")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun extraSpaces_sameHash() {
        val a = normalizeText("Total: Rp 45.000")
        val b = normalizeText("Total: Rp  45.000")  // double space
        assertEquals("Extra spasi harus di-collapse", a.hashCode(), b.hashCode())
    }

    @Test
    fun trailingWhitespace_sameHash() {
        val a = normalizeText("Beranda")
        val b = normalizeText("Beranda   ")
        val c = normalizeText("  Beranda")
        val d = normalizeText("  Beranda   ")
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(a.hashCode(), c.hashCode())
        assertEquals(a.hashCode(), d.hashCode())
    }

    @Test
    fun nonBreakingSpace_sameHash() {
        val a = normalizeText("Rp 45.000")
        val b = normalizeText("Rp\u00A045.000")  // non-breaking space
        assertEquals("Non-breaking space harus jadi regular space", a.hashCode(), b.hashCode())
    }

    @Test
    fun zeroWidthChars_sameHash() {
        val a = normalizeText("Hello World")
        val b = normalizeText("Hello\u200BWorld")   // zero-width space
        val c = normalizeText("Hello\u200CWorld")   // zero-width non-joiner
        val d = normalizeText("Hello\u200DWorld")   // zero-width joiner
        val e = normalizeText("Hello\uFEFFWorld")   // BOM
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(a.hashCode(), c.hashCode())
        assertEquals(a.hashCode(), d.hashCode())
        assertEquals(a.hashCode(), e.hashCode())
    }

    @Test
    fun newlines_collapsedToSpace() {
        val a = normalizeText("Line1 Line2")
        val b = normalizeText("Line1\nLine2")
        val c = normalizeText("Line1\r\nLine2")
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(a.hashCode(), c.hashCode())
    }

    @Test
    fun tabs_collapsedToSpace() {
        val a = normalizeText("Col1 Col2")
        val b = normalizeText("Col1\tCol2")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun differentContent_differentHash() {
        val a = normalizeText("Riwayat Pesanan - 3 item")
        val b = normalizeText("Riwayat Pesanan - 5 item")
        assertNotEquals("Konten berbeda harus hash berbeda", a.hashCode(), b.hashCode())
    }

    @Test
    fun emptyString_handled() {
        val result = normalizeText("")
        assertEquals("", result)
    }

    @Test
    fun onlyWhitespace_becomesEmpty() {
        val result = normalizeText("   \n\t  ")
        assertEquals("", result)
    }

    @Test
    fun complexRealWorldText_normalized() {
        // Simulasi teks nyata dari ShopeeFood
        val raw1 = "Rincian Pesanan\n#456\nDIMSUM MENTU 777 &\nJAGUNG CHEESE 777\n"
        val raw2 = "Rincian Pesanan\n#456\nDIMSUM MENTU 777 &\n JAGUNG CHEESE 777 "
        assertEquals(
            "Teks real-world yang visual-identical harus hash sama",
            normalizeText(raw1).hashCode(),
            normalizeText(raw2).hashCode()
        )
    }
}
