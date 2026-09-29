#!/bin/zsh
# 一键在电脑上试用「爷爷的音乐盒」：启动安卓模拟器（带手机窗口）、
# 安装发布版 APK 并打开应用。再跑一次会复用同一台虚拟手机。
set -e
cd "$(dirname "$0")/.."

# 本机构建环境（JDK/SDK/模拟器都装在 ~/tools）
if [[ -f "$HOME/tools/android-env.sh" ]]; then
  source "$HOME/tools/android-env.sh"
fi

if [[ ! -d "$ANDROID_HOME" ]]; then
  echo "未找到 Android SDK（$ANDROID_HOME），请先在本项目目录跑一次构建。"
  exit 1
fi

echo "启动模拟器（会弹出一个手机窗口，请稍候 1~2 分钟）..."
"$ANDROID_HOME/emulator/emulator" -avd test -no-boot-anim -gpu swiftshader_indirect &
EMU_PID=$!

adb wait-for-device
until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
  sleep 2
done
echo "模拟器已开机，安装应用..."
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell pm grant com.greenhome.musicbox android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell am start -n com.greenhome.musicbox/.MainActivity
echo ""
echo "✔ 现在可以用鼠标当手指点这个手机窗口了。"
echo "  想加歌：把电脑里的 mp3/wav 直接拖进模拟器窗口，"
echo "  然后在应用里点「＋ 添加歌曲」，从 Download 文件夹里选。"
echo "  停止：关掉窗口，或执行 adb emu kill"
wait $EMU_PID
