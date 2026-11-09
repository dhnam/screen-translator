package dev.dhnam.screentranslator

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * 홈버튼 길게 누르기 → 스크린샷 수신 → 캐시에 저장 → 번역 앱으로 공유 → 닫기.
 * 흐름은 [SessionMachine]이 정하고, 이 클래스는 그 결과(Effect)를 실행만 한다.
 * 보이는 UI는 그리지 않는다. 안내는 토스트로 띄운다.
 * (One UI는 알림 권한이 없는 앱의 토스트를 막으므로, 알림 권한은 SettingsActivity에서 요청한다.)
 */
class AssistSession(context: Context) : VoiceInteractionSession(context) {
    private val store = ShotStore(File(context.cacheDir, "shots"))
    private var timer: Runnable? = null

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        run(machine.show(), null)
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        run(machine.screenshot(screenshot != null), screenshot)
    }

    override fun onHide() {
        timer?.let(main::removeCallbacks)
        timer = null
        super.onHide()
    }

    private fun run(effect: SessionEffect, screenshot: Bitmap?, uri: Uri? = null) {
        when (effect) {
            is SessionEffect.StartTimer -> {
                timer?.let(main::removeCallbacks)
                val t = Runnable { run(machine.timeout(effect.gen), null) }
                timer = t
                main.postDelayed(t, SCREENSHOT_TIMEOUT_MS)
            }
            is SessionEffect.Save -> {
                timer?.let(main::removeCallbacks)
                val bitmap = screenshot?.let { if (Prefs(context).cropSystemBars) cropSystemBars(it) else it } ?: return
                Thread {
                    val saved = ShotSharer.prepare(
                        store,
                        { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) },
                        { FileProvider.getUriForFile(context, "${context.packageName}.files", it) },
                    )
                    main.post { run(machine.saved(effect.gen, saved != null), null, saved) }
                }.start()
            }
            SessionEffect.Launch -> if (uri != null) launch(uri) else hide()
            is SessionEffect.Message -> {
                timer?.let(main::removeCallbacks)
                Toast.makeText(context, effect.res, Toast.LENGTH_LONG).show()
                hide()
            }
            SessionEffect.None -> Unit
        }
    }

    /**
     * 상태바·내비게이션 바 자리를 잘라낸다. 크기는 세션 창이 아니라 화면 정보에서 읽는다
     * (세션 창 인셋은 아직 없거나 세션 창 기준이다). 알 수 없거나 이상하면 원본을 그대로 쓴다.
     */
    private fun cropSystemBars(src: Bitmap): Bitmap = try {
        val metrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
        val bounds = metrics.bounds
        val i = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        val r = CropRect.of(src.width, src.height, bounds.width(), bounds.height(), i.left, i.top, i.right, i.bottom)
        if (r == null) src else Bitmap.createBitmap(src, r.x, r.y, r.width, r.height)
    } catch (e: RuntimeException) {
        src
    }

    private fun launch(uri: Uri) {
        val prefs = Prefs(context)
        val resolver = TargetResolver(context.packageManager, context.packageName)
        val target = prefs.targetList.default?.let(ComponentName::unflattenFromString)
            ?.let(resolver::resolve) ?: Target.Missing
        val intent = resolver.finalIntent(resolver.buildIntent(target, uri))
        if (ShotSharer.tryStart { context.startActivity(intent) }) {
            val sent = (target as? Target.Exact)?.component
            if (prefs.showOpeningToast && sent != null && intent.component == sent) {
                val name = resolver.label(sent)
                Toast.makeText(context, context.getString(R.string.opening_format, name), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, R.string.open_failed, Toast.LENGTH_LONG).show()
        }
        hide()
    }

    private companion object {
        const val SCREENSHOT_TIMEOUT_MS = 3000L
        val main = Handler(Looper.getMainLooper())

        /**
         * 세션 객체는 누를 때마다 새로 만들어질 수 있으므로 상태 기계는 프로세스에 하나만 둔다.
         * 그래야 앞 세션의 저장 결과가 새 세션을 건드리지 못한다. 메인 스레드에서만 쓴다.
         */
        val machine = SessionMachine()
    }
}
