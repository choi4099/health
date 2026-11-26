#!/usr/bin/env bash
# 헬스 앱을 연결된 휴대폰에서 삭제
adb uninstall com.health.posecoach && echo "삭제 완료" || echo "삭제 실패: 앱이 없거나 adb 연결을 확인하세요"
