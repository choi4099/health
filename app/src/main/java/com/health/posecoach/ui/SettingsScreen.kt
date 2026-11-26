package com.health.posecoach.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.posecoach.BuildConfig
import com.health.posecoach.install.AppInstaller

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var confirmUninstall by remember { mutableStateOf(false) }
    var pendingApk by remember { mutableStateOf<Uri?>(null) }

    val pickApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingApk = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Section("앱 정보") {
                Text("이름: 헬스")
                Text("버전: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text("패키지: ${ctx.packageName}", fontSize = 13.sp, color = Color(0xFF666666))
            }

            Section("설치 / 업데이트") {
                Text("새 버전의 APK 파일을 골라 이 휴대폰에 바로 설치(덮어쓰기)합니다.", fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Button(onClick = {
                    if (!AppInstaller.canInstall(ctx)) {
                        AppInstaller.toast(ctx, "‘이 출처의 앱 설치 허용’을 켠 뒤 다시 눌러 주세요.")
                        AppInstaller.openInstallPermissionSettings(ctx)
                    } else {
                        pickApk.launch(arrayOf("application/vnd.android.package-archive"))
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.InstallMobile, null); Spacer(Modifier.width(8.dp)); Text("APK 파일 선택해서 설치")
                }
                Text("PC에서 설치: 프로젝트의 scripts/install.bat (또는 install.sh)을 실행하세요.", fontSize = 12.sp, color = Color(0xFF666666))
            }

            Section("삭제 (언인스톨)") {
                Text("헬스 앱과 앱 데이터를 이 휴대폰에서 삭제합니다.", fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { confirmUninstall = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Bad),
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Filled.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text("앱 삭제") }
                Text("PC에서 삭제: scripts/uninstall.bat (또는 uninstall.sh)", fontSize = 12.sp, color = Color(0xFF666666))
            }
        }
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text("헬스 앱을 삭제할까요?") },
            text = { Text("앱과 저장된 데이터가 모두 지워지고 되돌릴 수 없어요. 다음 화면의 시스템 확인 창에서 한 번 더 확인합니다.") },
            confirmButton = { TextButton(onClick = { confirmUninstall = false; AppInstaller.uninstallSelf(ctx) }) { Text("삭제", color = Bad) } },
            dismissButton = { TextButton(onClick = { confirmUninstall = false }) { Text("취소") } }
        )
    }
    pendingApk?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingApk = null },
            title = { Text("이 APK를 설치할까요?") },
            text = { Text("선택한 파일: ${uri.lastPathSegment ?: uri}\n같은 앱이면 데이터는 유지된 채 업데이트됩니다.") },
            confirmButton = { TextButton(onClick = { pendingApk = null; AppInstaller.installApk(ctx, uri) }) { Text("설치") } },
            dismissButton = { TextButton(onClick = { pendingApk = null }) { Text("취소") } }
        )
    }
}
