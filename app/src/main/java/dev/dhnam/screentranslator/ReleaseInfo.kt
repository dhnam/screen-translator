package dev.dhnam.screentranslator

import org.json.JSONException
import org.json.JSONObject

/** GitHub 최신 릴리스 중 업데이트에 필요한 값. `sha256`은 소문자 hex이고 없을 수 있다. */
data class ReleaseInfo(val tag: String, val url: String, val size: Long, val sha256: String?) {
    companion object {
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
                    )
                }
        } catch (e: JSONException) {
            null
        }
    }
}
