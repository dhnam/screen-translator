package dev.dhnam.screentranslator

import kotlin.math.abs
import kotlin.math.roundToInt

/** 캡처에서 남길 영역(비트맵 좌표). Android `Rect`는 JVM 테스트에서 동작하지 않아 따로 둔다. */
data class CropRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    companion object {
        /**
         * 시스템 바 인셋(화면 좌표)을 비트맵 크기로 환산해 잘라낼 영역을 구한다.
         * 인셋이 없거나, 화면과 비트맵의 가로세로 비율이 1% 넘게 다르거나, 남는 영역이 절반보다 작으면 null(자르지 않음).
         */
        fun of(
            bitmapW: Int, bitmapH: Int, displayW: Int, displayH: Int,
            left: Int, top: Int, right: Int, bottom: Int,
        ): CropRect? {
            if (bitmapW <= 0 || bitmapH <= 0 || displayW <= 0 || displayH <= 0) return null
            val bitmapRatio = bitmapW.toDouble() / bitmapH
            val displayRatio = displayW.toDouble() / displayH
            if (abs(bitmapRatio - displayRatio) / displayRatio > 0.01) return null
            val sx = bitmapW.toDouble() / displayW
            val sy = bitmapH.toDouble() / displayH
            val l = (left.coerceAtLeast(0) * sx).roundToInt()
            val r = (right.coerceAtLeast(0) * sx).roundToInt()
            val t = (top.coerceAtLeast(0) * sy).roundToInt()
            val b = (bottom.coerceAtLeast(0) * sy).roundToInt()
            if (l == 0 && r == 0 && t == 0 && b == 0) return null
            val w = bitmapW - l - r
            val h = bitmapH - t - b
            if (w < bitmapW / 2 || h < bitmapH / 2) return null
            return CropRect(l, t, w, h)
        }
    }
}
