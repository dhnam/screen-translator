package dev.dhnam.screentranslator

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.service.voice.VoiceInteractionSession
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

// 시험용 최소 동작 (Task 4에서 다듬음)
class AssistSession(context: Context) : VoiceInteractionSession(context) {
    override fun onHandleScreenshot(screenshot: Bitmap?) {
        if (screenshot == null) {
            Toast.makeText(context, "스크린샷 없음", Toast.LENGTH_SHORT).show()
            hide(); return
        }
        val dir = File(context.cacheDir, "shots").apply { mkdirs() }
        val file = File(dir, "shot-${System.currentTimeMillis()}.png")
        file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "dev.dhnam.screentranslator.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.naver.labs.translator")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        hide()
    }
}
