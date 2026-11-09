package dev.dhnam.screentranslator

/**
 * APK를 받을 주소 하나. GitHub의 일반 다운로드 주소(`browser_download_url`)는 가끔 504를 내는데,
 * 그때도 API 주소(`/releases/assets/{id}`, `Accept: application/octet-stream`)는 정상이었다.
 * 그래서 API 주소를 먼저 쓰고, 서버 오류나 연결 실패면 다음 주소로 넘어간다.
 */
data class DownloadSource(val url: String, val api: Boolean) {
    companion object {
        fun of(info: ReleaseInfo): List<DownloadSource> =
            listOfNotNull(info.apiUrl?.let { DownloadSource(it, api = true) }, DownloadSource(info.url, api = false))

        /**
         * 다음 주소를 시도할지. `code`가 null이면 연결 자체가 실패한 경우.
         * API 주소는 200이 아니면 모두 넘어간다(로그인 없는 API는 시간당 횟수 제한이 있어 403이 날 수 있음).
         * 일반 주소는 5xx와 연결 실패만 넘어간다.
         */
        fun shouldTryNext(source: DownloadSource, code: Int?): Boolean =
            code != 200 && (source.api || code == null || code in 500..599)
    }
}
