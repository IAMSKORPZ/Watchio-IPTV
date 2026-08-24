package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.data.xtream.XtreamUrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamUrlNormalizerTest {
    @Test
    fun normalizesCommonUserInputs() {
        assertEquals("http://example.invalid", XtreamUrlNormalizer.normalize("example.invalid"))
        assertEquals("http://example.invalid:8080", XtreamUrlNormalizer.normalize("example.invalid:8080/"))
        assertEquals("https://example.invalid", XtreamUrlNormalizer.normalize("https://example.invalid/"))
        assertEquals("http://example.invalid/path", XtreamUrlNormalizer.normalize("http://example.invalid/path?x=1"))
    }

    @Test
    fun rejectsMalformedValues() {
        assertNull(XtreamUrlNormalizer.normalize(""))
        assertNull(XtreamUrlNormalizer.normalize("bad host"))
        assertNull(XtreamUrlNormalizer.normalize("http://example.invalid/player_api.php"))
    }
}
