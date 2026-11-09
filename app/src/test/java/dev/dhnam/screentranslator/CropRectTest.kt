package dev.dhnam.screentranslator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropRectTest {
    @Test fun cropRect_noInsets_isNull() = assertNull(CropRect.of(1080, 2400, 1080, 2400, 0, 0, 0, 0))

    @Test fun cropRect_portrait_topAndBottom() =
        assertEquals(CropRect(0, 80, 1080, 2200), CropRect.of(1080, 2400, 1080, 2400, 0, 80, 0, 120))

    @Test fun cropRect_landscapeSideBar() =
        assertEquals(CropRect(0, 80, 2280, 1000), CropRect.of(2400, 1080, 2400, 1080, 0, 80, 120, 0))

    @Test fun cropRect_scaledBitmap() =
        assertEquals(CropRect(0, 40, 540, 1100), CropRect.of(540, 1200, 1080, 2400, 0, 80, 0, 120))

    @Test fun cropRect_aspectMismatch_isNull() = assertNull(CropRect.of(1080, 2000, 1080, 2400, 0, 80, 0, 120))

    @Test fun cropRect_badInsets_isNull() = assertNull(CropRect.of(1080, 2400, 1080, 2400, 0, 2000, 0, 2000))

    @Test fun cropRect_negativeInsets_treatedAsZero() =
        assertEquals(CropRect(0, 80, 1080, 2320), CropRect.of(1080, 2400, 1080, 2400, -5, 80, -5, -10))

    @Test fun cropRect_zeroDisplay_isNull() = assertNull(CropRect.of(1080, 2400, 0, 0, 0, 80, 0, 120))
}
