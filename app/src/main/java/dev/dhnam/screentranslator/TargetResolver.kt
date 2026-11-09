package dev.dhnam.screentranslator

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** 설정값과 캡처 URI로 번역 앱 공유 인텐트를 만든다. */
class TargetResolver(private val pm: PackageManager) {

    fun buildIntent(target: Target, uri: Uri): Intent {
        val intent = baseIntent(uri)
        when (target) {
            Target.PAPAGO -> intent.setPackage(target.pkg)
            Target.GOOGLE -> {
                val lens = pickLens(shareActivities(target.pkg))
                if (lens != null) intent.component = lens else intent.setPackage(target.pkg)
            }
        }
        return intent
    }

    /** 대상이 받을 수 없으면 일반 공유 메뉴로 감싼다. 감싼 인텐트에도 URI 읽기 권한을 유지한다. */
    fun finalIntent(intent: Intent): Intent {
        if (pm.resolveActivity(intent, 0) != null) return intent
        val inner = Intent(intent).apply {
            setPackage(null)
            component = null
        }
        return Intent.createChooser(inner, null).apply {
            clipData = intent.clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun shareActivities(pkg: String): List<ComponentName> {
        val query = Intent(Intent.ACTION_SEND).setType(TYPE).setPackage(pkg)
        return pm.queryIntentActivities(query, 0).map {
            ComponentName(it.activityInfo.packageName, it.activityInfo.name)
        }
    }

    private fun baseIntent(uri: Uri) = Intent(Intent.ACTION_SEND).apply {
        type = TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        private const val TYPE = "image/png"

        /** 클래스 이름에 "lens"가 들어간 첫 Activity(대소문자 무시). */
        fun pickLens(candidates: List<ComponentName>): ComponentName? =
            candidates.firstOrNull { it.className.contains("lens", ignoreCase = true) }
    }
}
