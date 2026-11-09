package dev.dhnam.screentranslator

/** 점으로 나눈 숫자 버전. 빠진 자리는 0으로 비교한다(0.2 == 0.2.0). */
data class Version(val parts: List<Int>) : Comparable<Version> {

    override fun compareTo(other: Version): Int {
        val n = maxOf(parts.size, other.parts.size)
        for (i in 0 until n) {
            val c = parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is Version && compareTo(other) == 0

    override fun hashCode(): Int = parts.dropLastWhile { it == 0 }.hashCode()

    companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L

        /** "v0.2.0", "0.2" 등을 해석한다. 숫자가 아닌 부분이 있으면 null. */
        fun parse(s: String?): Version? {
            val t = s?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
            if (t.isEmpty()) return null
            val parts = t.split('.').map { it.toIntOrNull() ?: return null }
            return Version(parts)
        }

        /** `latest`가 `current`보다 새 버전인지. 둘 중 하나라도 해석할 수 없으면 false. */
        fun isNewer(latest: String?, current: String?): Boolean {
            val l = parse(latest) ?: return false
            val c = parse(current) ?: return false
            return l > c
        }

        /** 자동 확인할 때인지: 처음이거나, 간격이 지났거나, 시계가 거꾸로 갔으면 true. */
        fun shouldAutoCheck(now: Long, lastCheckedAt: Long, intervalMs: Long = DAY_MS): Boolean =
            lastCheckedAt == 0L || now < lastCheckedAt || now - lastCheckedAt >= intervalMs
    }
}
