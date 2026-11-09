package dev.dhnam.screentranslator

import android.content.ActivityNotFoundException
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** 캡처 저장과 번역 앱 실행에서 실패를 놓치거나 앱이 죽지 않도록 감싼다. */
object ShotSharer {
    private const val TAG = "ShotSharer"

    /**
     * 오래된 캡처를 정리하고 새 캡처를 저장한 뒤 공유 URI를 만든다.
     * `compress`가 false를 돌려주거나(예: 저장 공간 부족) 어떤 예외가 나도 파일을 남기지 않고 null을 돌려준다.
     */
    fun <T> prepare(store: ShotStore, compress: (OutputStream) -> Boolean, toUri: (File) -> T): T? {
        var file: File? = null
        return try {
            store.purgeOlderThan(ShotStore.MAX_AGE_MS)
            file = store.save { if (!compress(it)) throw IOException("compress failed") }
            toUri(file)
        } catch (e: Exception) {
            safeLog("prepare failed", e)
            file?.delete()
            null
        }
    }

    /** 번역 앱 실행. 대상이 사라졌거나 실행이 거부되면 false. */
    fun tryStart(start: () -> Unit): Boolean = try {
        start()
        true
    } catch (e: ActivityNotFoundException) {
        safeLog("start failed", e)
        false
    } catch (e: SecurityException) {
        safeLog("start failed", e)
        false
    }

    private fun safeLog(msg: String, e: Exception) {
        try { Log.w(TAG, msg, e) } catch (_: RuntimeException) { }
    }
}
