#!/bin/zsh
# 一键构建发布 APK：先跑单元测试，再打签名 release 包
set -e
cd "$(dirname "$0")/.."
if [[ -f "$HOME/tools/android-env.sh" ]]; then
  source "$HOME/tools/android-env.sh"
fi
./gradlew test assembleRelease
APK="app/build/outputs/apk/release/app-release.apk"
echo ""
echo "=============================================="
echo "发布包已生成: $APK"
ls -lh "$APK"
echo "发送给爷爷手机安装即可（见 安装指南.md）"
echo "=============================================="
