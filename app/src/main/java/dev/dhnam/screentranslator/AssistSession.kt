package dev.dhnam.screentranslator

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * 홈버튼 길게 누르기 → 스크린샷 수신 → 캐시에 저장 → 번역 앱으로 공유 → 닫기.
 * 보이는 UI는 그리지 않는다. 안내는 토스트로 띄운다.
 * (One UI는 알림 권한이 없는 앱의 토스트를 막으므로, 알림 권한은 SettingsActivity에서 요청한다.)
 */
class AssistSession(context: Context) : VoiceInteractionSession(context) {
    private val main = Handler(Looper.getMainLooper())
    private val store = ShotStore(File(context.cacheDir, "shots"))
    private var handled = false

    private val timeout = Runnable { finishWith(R.string.no_screenshot) }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        handled = false
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, SCREENSHOT_TIMEOUT_MS)
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        main.removeCallbacks(timeout)
        if (handled) return
        handled = true
        if (screenshot == null) {
            finishWith(R.string.no_screenshot)
            return
        }
        Thread {
            try {
                store.purgeOlderThan(ShotStore.MAX_AGE_MS)
                val file = store.save { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
                main.post {
                    val resolver = TargetResolver(context.packageManager)
                    context.startActivity(resolver.finalIntent(resolver.buildIntent(Prefs(context).target, uri)))
                    hide()
                }
            } catch (e: IOException) {
                main.post { finishWith(R.string.save_failed) }
            }
        }.start()
    }

    override fun onHide() {
        main.removeCallbacks(timeout)
        super.onHide()
    }

    private fun finishWith(@StringRes message: Int) {
        handled = true
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        hide()
    }

    private companion object {
        const val SCREENSHOT_TIMEOUT_MS = 2000L
        const val AUTHORITY = "dev.dhnam.screentranslator.files"
    }
}
