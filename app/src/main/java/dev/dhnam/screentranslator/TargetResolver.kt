package dev.dhnam.screentranslator

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri

/** 이미지를 받는 화면 조회와 번역 앱 공유 인텐트 생성. */
class TargetResolver(private val pm: PackageManager, private val selfPackage: String) {

    /** 이미지를 받을 수 있는 화면 목록. 이 앱 자신(.dev 변형 포함)은 뺀다. */
    fun shareTargets(): List<ComponentName> =
        pm.queryIntentActivities(Intent(Intent.ACTION_SEND).setType(TYPE), 0)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .filterNot { it.packageName.startsWith(selfPackage) }
            .distinct()

    /** 설치돼 있고 이미지를 받을 수 있는 화면인지. */
    fun isAvailable(c: ComponentName): Boolean =
        pm.queryIntentActivities(Intent(Intent.ACTION_SEND).setType(TYPE).setComponent(c), 0).isNotEmpty()

    /** 표시 이름. 화면 이름이 앱 이름과 다르면 "앱 · 화면". 조회에 실패하면 패키지 이름. */
    fun label(c: ComponentName): String = try {
        val app = pm.getApplicationInfo(c.packageName, 0).loadLabel(pm).toString()
        val activity = pm.getActivityInfo(c, 0).loadLabel(pm).toString()
        if (activity.isBlank() || activity == app) app else "$app · $activity"
    } catch (e: PackageManager.NameNotFoundException) {
        c.packageName
    }

    fun icon(c: ComponentName): Drawable? = try {
        pm.getActivityIcon(c)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** 공유 인텐트. `c`가 null이면 대상을 정하지 않는다(→ `finalIntent`에서 공유 메뉴). */
    fun buildIntent(c: ComponentName?, uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        component = c
    }

    /** 대상이 없거나 받을 수 없으면 일반 공유 메뉴로 감싼다. 감싼 인텐트에도 URI 읽기 권한을 유지한다. */
    fun finalIntent(intent: Intent): Intent {
        val explicit = intent.component != null || intent.`package` != null
        if (explicit && pm.resolveActivity(intent, 0) != null) return intent
        val inner = Intent(intent).apply {
            setPackage(null)
            component = null
        }
        return Intent.createChooser(inner, null).apply {
            clipData = intent.clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private companion object {
        const val TYPE = "image/png"
    }
}
