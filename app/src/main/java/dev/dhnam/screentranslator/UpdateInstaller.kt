package dev.dhnam.screentranslator

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 새 버전 APK를 받으면서 PackageInstaller 세션에 바로 쓴다(파일로 저장하지 않음).
 * 해시를 확인한 뒤 세션을 확정하면, 결과는 InstallResultReceiver로 온다. 백그라운드 스레드에서 호출한다.
 */
class UpdateInstaller(private val context: Context) {

    /** `isCancelled`가 true가 되면 받기를 멈추고 세션을 버린다(IOException(CANCELLED)). */
    /** 확정한 세션 번호를 돌려준다. */
    fun install(info: ReleaseInfo, isCancelled: () -> Boolean = { false }, onProgress: (Int) -> Unit): Int {
        val installer = context.packageManager.packageInstaller
        // 이전에 받다가 멈춰 남은(확정 전) 세션을 정리한다. 확정된 세션은 설치 확인·검사·설치 중일 수 있으므로 건드리지 않는다.
        for (stale in installer.mySessions) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && stale.isCommitted) continue
            try { installer.abandonSession(stale.sessionId) } catch (e: SecurityException) { }
        }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (info.size > 0) setSize(info.size)
        }
        val id = installer.createSession(params)
        val session = installer.openSession(id)
        try {
            val conn = URL(info.url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.setRequestProperty("User-Agent", "screen-translator")
                if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
                val hash = conn.inputStream.use { input ->
                    session.openWrite("base.apk", 0, if (info.size > 0) info.size else -1).use { out ->
                        val h = HashingCopy.copy(input, out) { done ->
                            if (isCancelled()) throw IOException(CANCELLED)
                            if (info.size > 0) onProgress((done * 100 / info.size).toInt().coerceIn(0, 100))
                        }
                        session.fsync(out)
                        h
                    }
                }
                if (!HashingCopy.hashesMatch(info.sha256, hash)) throw IOException(DIGEST_MISMATCH)
            } finally {
                conn.disconnect()
            }
            val result = PendingIntent.getBroadcast(
                context, id,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(result.intentSender)
            session.close()
            return id
        } catch (e: Exception) {
            session.abandon()
            throw e as? IOException ?: IOException(e)
        }
    }

    companion object {
        const val DIGEST_MISMATCH = "digest mismatch"
        const val CANCELLED = "cancelled"
        /** 다시 조회해 보니 새 버전이 없음(이미 최신). 실패로 알리지 않는다. */
        const val NO_UPDATE = "no update"
        private const val TIMEOUT_MS = 15_000
    }
}
