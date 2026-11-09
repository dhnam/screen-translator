package dev.dhnam.screentranslator

import org.json.JSONException
import org.json.JSONObject

/** GitHub 최신 릴리스 중 업데이트에 필요한 값. `sha256`은 소문자 hex이고 없을 수 있다. */
data class ReleaseInfo(
    val tag: String,
    val url: String,
    val size: Long,
    val sha256: String?,
    /** GitHub API 첨부 주소(`assets[].url`). 일반 다운로드 주소가 실패할 때를 대비해 먼저 쓴다. */
    val apiUrl: String? = null,
) {
    companion object {
        /**
         * 받기 직전에 최신 릴리스를 다시 조회한 결과. 저장된 결과가 오래됐어도 가장 새 버전을 받는다.
         * 조회가 실패하면 저장된 것을 쓰고, 더 이상 새 버전이 없으면 null.
         */
        fun refreshed(cached: ReleaseInfo, refresh: () -> ReleaseInfo?): ReleaseInfo? =
            try { refresh() } catch (e: java.io.IOException) { cached }

        /** `releases/latest` 응답을 해석한다. APK 첨부가 없거나 형식이 다르면 null. */
        fun parse(json: String): ReleaseInfo? = try {
            val root = JSONObject(json)
            val tag = root.getString("tag_name")
            val assets = root.getJSONArray("assets")
            (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").let { n -> n.startsWith("screen-translator-") && n.endsWith(".apk") } }
                ?.let { a ->
                    val digest = a.optString("digest", "")
                    ReleaseInfo(
                        tag = tag,
                        url = a.getString("browser_download_url"),
                        size = a.getLong("size"),
                        sha256 = if (digest.startsWith("sha256:")) digest.removePrefix("sha256:").lowercase() else null,
                        apiUrl = a.optString("url", "").takeIf { it.startsWith("https://") },
                    )
                }
        } catch (e: JSONException) {
            null
        }
    }
}
