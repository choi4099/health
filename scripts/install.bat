@echo off
chcp 65001 > nul
REM 헬스 앱 빌드 후 USB로 연결된 휴대폰에 설치
cd /d "%~dp0.."
echo [1/2] APK 빌드 중...
call gradlew.bat assembleDebug || (echo 빌드 실패 & pause & exit /b 1)
echo [2/2] 휴대폰에 설치 중...
adb install -r "app\build\outputs\apk\debug\app-debug.apk" || (echo 설치 실패: USB 디버깅과 adb 연결을 확인하세요 & pause & exit /b 1)
adb shell monkey -p com.health.posecoach -c android.intent.category.LAUNCHER 1 > nul
echo 설치 완료! 휴대폰에서 "헬스" 앱이 실행됩니다.
pause
