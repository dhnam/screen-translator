package dev.dhnam.screentranslator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

/** PackageInstaller 세션 결과: 사용자 확인이 필요하면 확인 창을 띄우고, 실패하면 이유를 알린다. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm == null) {
                    UpdateFlow.dispatch(UpdateEvent.InstallFinished)
                    return
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                UpdateFlow.setConfirmIntent(confirm)
                context.startActivity(confirm)
            }
            else -> {
                // 성공·실패·사용자 취소 모두 "설치 확인 대기"를 끝낸다.
                UpdateFlow.dispatch(UpdateEvent.InstallFinished)
                if (status != PackageInstaller.STATUS_SUCCESS && status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "?"
                    Toast.makeText(context, context.getString(R.string.install_failed, msg), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
