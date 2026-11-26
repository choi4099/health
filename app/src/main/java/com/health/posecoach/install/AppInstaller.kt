package com.health.posecoach.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.IntentCompat
import kotlin.concurrent.thread

/**
 * 앱 안에서 하는 설치/업데이트/삭제.
 * - 설치: 사용자가 고른 APK 파일을 PackageInstaller 로 설치 (시스템 확인 창이 뜸)
 * - 삭제: 시스템 삭제 확인 창을 띄움
 */
object AppInstaller {

    /** '출처를 알 수 없는 앱 설치' 허용 여부 */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun installApk(ctx: Context, apk: Uri) {
        val app = ctx.applicationContext
        thread(name = "apk-install") {
            try {
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    app.contentResolver.openInputStream(apk).use { input ->
                        requireNotNull(input) { "APK 파일을 열 수 없어요." }
                        session.openWrite("health.apk", 0, -1).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val pending = PendingIntent.getBroadcast(
                        app, sessionId, Intent(app, InstallResultReceiver::class.java), flags
                    )
                    session.commit(pending.intentSender)
                }
            } catch (e: Exception) {
                toast(app, "설치 실패: ${e.message}")
            }
        }
    }

    /** 이 앱(헬스) 삭제 */
    fun uninstallSelf(ctx: Context) {
        ctx.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun toast(ctx: Context, msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> AppInstaller.toast(context.applicationContext, "설치가 끝났어요.")
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "오류 코드 $status"
                AppInstaller.toast(context.applicationContext, "설치 실패: $msg")
            }
        }
    }
}
