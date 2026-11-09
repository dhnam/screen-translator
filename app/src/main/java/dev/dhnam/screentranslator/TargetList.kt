package dev.dhnam.screentranslator

/**
 * 번역 앱 목록과 기본 앱. 각 항목은 `ComponentName.flattenToString()` 문자열이다.
 * 목록이 비어 있지 않으면 기본 앱은 항상 목록 안에 있다.
 */
data class TargetList(val items: List<String>, val default: String?) {

    fun add(c: String): TargetList {
        if (c in items) return this
        return TargetList(items + c, default ?: c)
    }

    fun remove(c: String): TargetList {
        val rest = items - c
        val newDefault = if (default == c) rest.firstOrNull() else default
        return TargetList(rest, newDefault)
    }

    fun setDefault(c: String): TargetList = if (c in items) copy(default = c) else this

    fun encodeItems(): String = items.joinToString("\n")

    companion object {
        const val PAPAGO = "com.naver.labs.translator/com.naver.labs.translator.ui.main.DeepLinkActivity"
        const val LENS =
            "com.google.android.googlequicksearchbox/com.google.android.apps.search.lens.LensShareEntryPointActivity"

        val INITIAL = TargetList(listOf(PAPAGO, LENS), PAPAGO)

        /** 저장된 값을 읽는다. 빈 줄과 중복은 버리고, 목록에 없는 기본 앱은 첫 번째로 고친다. */
        fun decode(items: String?, default: String?): TargetList {
            val list = items.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val d = if (default != null && default in list) default else list.firstOrNull()
            return TargetList(list, d)
        }

        /** v0.1.0의 `target` 값(PAPAGO / GOOGLE)을 새 목록으로 옮긴다. */
        fun migrate(oldTarget: String?): TargetList =
            if (oldTarget == "GOOGLE") INITIAL.setDefault(LENS) else INITIAL
    }
}
