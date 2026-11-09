package dev.dhnam.screentranslator

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** GitHub 최신 릴리스를 조회해 새 버전이 있는지 확인한다. 백그라운드 스레드에서 호출한다. */
class UpdateChecker(private val context: Context) {

    fun currentVersion(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    /** 새 버전이 있으면 그 정보, 없으면 null. 결과와 확인 시각을 저장한다. 네트워크·HTTP 오류는 IOException. */
    fun check(): ReleaseInfo? {
        val conn = URL(API).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "screen-translator")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val newer = ReleaseInfo.parse(body)?.takeIf { Version.isNewer(it.tag, currentVersion()) }
            Prefs(context).apply {
                updateCheckedAt = System.currentTimeMillis()
                latestRelease = newer
            }
            return newer
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val API = "https://api.github.com/repos/dhnam/screen-translator/releases/latest"
        const val TIMEOUT_MS = 10_000
    }
}
