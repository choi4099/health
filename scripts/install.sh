#!/usr/bin/env bash
# 헬스 앱 빌드 후 USB로 연결된 휴대폰에 설치 (macOS / Linux)
set -e
cd "$(dirname "$0")/.."
echo "[1/2] APK 빌드 중..."
./gradlew assembleDebug
echo "[2/2] 휴대폰에 설치 중..."
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.health.posecoach -c android.intent.category.LAUNCHER 1 > /dev/null
echo "설치 완료! 휴대폰에서 '헬스' 앱이 실행됩니다."
