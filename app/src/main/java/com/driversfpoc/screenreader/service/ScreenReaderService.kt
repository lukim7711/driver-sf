package com.driversfpoc.screenreader.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Stub AccessibilityService — akan diimplementasi penuh di Step 3.
 * Saat ini hanya placeholder agar project bisa compile.
 */
class ScreenReaderService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // TODO: Step 3 — Implementasi penangkapan teks layar
    }

    override fun onInterrupt() {
        // TODO: Step 3 — Handle interrupt
    }
}
