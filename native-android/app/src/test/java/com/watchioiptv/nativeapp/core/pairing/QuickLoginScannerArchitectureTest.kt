package com.watchioiptv.nativeapp.core.pairing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuickLoginScannerArchitectureTest {
    @Test
    fun quickLoginUsesGoogleScannerRawValueWithoutExternalResolution() {
        val source = File("src/main/java/com/watchioiptv/nativeapp/ui/WatchioNativeApp.kt").readText()
        val quickLoginScreen = source.substringAfter("private fun QuickLoginScreen(")
            .substringBefore("private fun Context.isTelevisionDevice()")

        assertTrue(quickLoginScreen.contains("GmsBarcodeScanning.getClient"))
        assertTrue(quickLoginScreen.contains("onScannedCode(barcode.rawValue)"))
        assertFalse(quickLoginScreen.contains("ACTION_VIEW"))
        assertFalse(quickLoginScreen.contains("startActivity"))
    }

    @Test
    fun manifestDoesNotRequestCameraOrQuickLoginIntentFilter() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertFalse(manifest.contains("android.permission.CAMERA"))
        assertFalse(manifest.contains("watchio-pair"))
    }
}
