#!/usr/bin/env bash
set -euo pipefail

task_root=$(cd "$(dirname "$0")/.." && pwd)
task_java_home=${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}
task_adb=${ADB:-adb}
task_package=dev.x2c.fixture.consumer
task_component="$task_package/.MainActivity"
task_xml=/sdcard/x2c-showcase-window.xml

if ! "$task_adb" get-state >/dev/null 2>&1; then
    echo "No ready adb device" >&2
    exit 1
fi

JAVA_HOME="$task_java_home" "$task_root/gradlew" -p "$task_root" \
    :fixtures:consumer:assembleDebug --no-configuration-cache --console=plain
"$task_adb" install -r \
    "$task_root/fixtures/consumer/build/outputs/apk/debug/consumer-debug.apk" >/dev/null
# Fixture versionCodes intentionally stay stable during local development. Clear only this demo
# package so the signed-payload anti-replacement check starts from a deterministic state.
"$task_adb" shell pm clear "$task_package" >/dev/null
"$task_adb" logcat -c
"$task_adb" shell input keyevent KEYCODE_WAKEUP
"$task_adb" shell wm dismiss-keyguard
"$task_adb" shell am start -W -n "$task_component" >/dev/null

window_nodes() {
    "$task_adb" shell uiautomator dump "$task_xml" >/dev/null
    "$task_adb" exec-out cat "$task_xml" | sed 's/></>\n</g'
}

tap_text() {
    local task_text=$1
    local task_nodes=
    local task_node=
    local task_bounds=
    local task_viewport=
    local task_viewport_bottom=
    local task_x=
    local task_y=
    local task_try=
    # Keep consecutive dumps overlapping by more than one screen. A slow drag
    # avoids OEM fling physics jumping over short controls such as the 52dp
    # self-test action.
    for task_try in {0..39}; do
        task_nodes=$(window_nodes)
        # sed consumes the complete stream, avoiding an rg/head SIGPIPE under pipefail.
        task_node=$(printf '%s\n' "$task_nodes" \
            | rg -F "text=\"$task_text\"" | sed -n '1p' || true)
        if [[ -n "$task_node" ]]; then
            task_bounds=$(printf '%s\n' "$task_node" | sed -E \
                's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')
            task_viewport=$(printf '%s\n' "$task_nodes" | sed -nE \
                's/.*bounds="\[0,0\]\[([0-9]+),([0-9]+)\]".*/\1 \2/p' \
                | sed -n '1p')
            set -- $task_bounds
            task_x=$((($1 + $3) / 2))
            task_y=$((($2 + $4) / 2))
            task_viewport_bottom=${task_viewport#* }
            if [[ "$task_viewport_bottom" =~ ^[0-9]+$ ]] \
                && ((task_y > 0 && task_y < task_viewport_bottom)); then
                "$task_adb" shell input tap "$task_x" "$task_y"
                sleep 1
                return
            fi
        fi
        "$task_adb" shell input swipe 540 1900 540 1250 900
        sleep 1
    done
    echo "Cannot find clickable text: $task_text" >&2
    exit 1
}

require_text() {
    local task_text=$1
    local task_nodes=
    local task_try=
    for task_try in {0..39}; do
        task_nodes=$(window_nodes)
        if [[ "$task_nodes" == *"$task_text"* ]]; then
            return
        fi
        "$task_adb" shell input swipe 540 1900 540 1250 900
        sleep 1
    done
    echo "Expected UI text not found: $task_text" >&2
    exit 1
}

require_focus() {
    local task_activity=$1
    local task_focus=
    local task_try=
    for task_try in 0 1 2 3 4; do
        task_focus=$("$task_adb" shell dumpsys window \
            | rg 'mCurrentFocus=' | sed -n '1p')
        if [[ "$task_focus" == *"$task_activity"* ]]; then
            return
        fi
        sleep 1
    done
    echo "Unexpected focused Activity; expected=$task_activity actual=$task_focus" >&2
    exit 1
}

go_back() {
    "$task_adb" shell input keyevent KEYCODE_BACK
    sleep 1
}

scroll_up() {
    "$task_adb" shell input swipe 540 650 540 1650 500
    sleep 1
}

require_text "动态载荷已通过完整性校验"

# Demo 1: XML/resources/ViewGroup/custom View/local-source-to-CDN image flows.
echo "[1/6] Layout/resources/image showcase"
tap_text "Demo 1 · Layout / 资源 / 图片"
require_focus 'PluginContainerActivities$Standard0'
# UIAutomator serializes '&' as '&amp;'. Assert an unescaped sibling title so this
# device test validates the hero without depending on XML entity serialization.
require_text "X2C CAPABILITY GALLERY"
tap_text "运行能力自检"
require_text "能力自检=PASS"
tap_text "运行交互矩阵"
require_text "progress="
require_text "本地图片 → CDN 元数据 → 宿主异步加载"
tap_text "失败路径"
require_text "failure=PASS"
tap_text "重试/缓存"
require_text "success=PASS"
tap_text "一键填入演示账号"
# Filling the account reveals a status row below the login button. The action
# itself is located after that button in document order, so bring the button
# back into the viewport before looking for it.
scroll_up
tap_text "安全登录"
require_text "登录成功"
tap_text "检查 BusinessBase 转换"
require_text "点击探针=PASS"
go_back
require_focus "$task_package.MainActivity"

# Demo 2: all launch modes, Activity Result and the other three component protocols.
echo "[2/6] Activity launch modes and result routing"
tap_text "Demo 2 · Activity / 四大组件"
require_focus 'PluginContainerActivities$Standard1'
require_text "Activity / launchMode"

tap_text "singleTop"
require_focus 'PluginContainerActivities$SingleTop0'
tap_text "再次启动当前 Activity"
require_text "newIntent#=1"
go_back

tap_text "singleTask"
require_focus 'PluginContainerActivities$SingleTask0'
tap_text "再次启动当前 Activity"
require_text "newIntent#=1"
go_back

tap_text "singleInstance"
require_focus 'PluginContainerActivities$SingleInstance0'
tap_text "再次启动当前 Activity"
require_text "newIntent#=1"
go_back

tap_text "startActivityForResult → RESULT_OK"
require_focus 'PluginContainerActivities$Standard2'
tap_text "返回 RESULT_OK"
require_text "Activity Result=PASS"

echo "[3/6] Plugin Service start/bind/stop"
tap_text "START"
require_text "Service: onStartCommand"
tap_text "BIND"
require_text "Service bind=PASS"
tap_text "STOP"
require_text "Service: onDestroy"

echo "[4/6] Plugin BroadcastReceiver normal/ordered/goAsync"
tap_text "NORMAL"
require_text "goAsync.finish=PASS"
tap_text "ORDERED"
require_text "ordered result=PASS"

echo "[5/6] Plugin ContentProvider CRUD/call/batch/observer/file"
tap_text "运行 CRUD / call / batch / observer / file"
require_text "Provider CRUD/call/batch/file=PASS"

echo "[6/6] Cross-plugin and plugin-to-host navigation"
tap_text "跨 ClassLoader 打开 Layout 插件"
require_focus 'PluginContainerActivities$Standard0'
go_back
tap_text "插件 → 宿主 Activity"
require_focus "$task_package.MainActivity"
require_text "Navigation origin: component plugin → host"

task_app_pid=$("$task_adb" shell pidof "$task_package" | tr -d '\r')
if [[ -z "$task_app_pid" ]]; then
    echo "Plugin host process exited before log verification" >&2
    exit 1
fi
if "$task_adb" logcat -d -v brief --pid="$task_app_pid" \
    | rg 'FATAL EXCEPTION|ActivityNotFoundException|VerifyError|AbstractMethodError|ClassCastException' \
        >/dev/null; then
    echo "Plugin showcase produced a fatal runtime error" >&2
    exit 1
fi

echo "X2C layout and component showcase verification passed"
