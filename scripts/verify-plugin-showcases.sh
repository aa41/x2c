#!/usr/bin/env bash
set -euo pipefail
task_root=$(cd "$(dirname "$0")/.." && pwd)
task_adb=${ADB:-adb}
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
task_package=dev.x2c.fixture.consumer
"$task_adb" get-state >/dev/null
bash "$task_root/scripts/verify-demo-suite.sh"
"$task_adb" install -r "$task_root/fixtures/consumer/build/outputs/apk/debug/consumer-debug.apk" >/dev/null
"$task_adb" shell am force-stop "$task_package"
"$task_adb" shell input keyevent KEYCODE_WAKEUP
"$task_adb" shell wm dismiss-keyguard
"$task_adb" shell am start -W -n "$task_package/.MainActivity" >/dev/null
task_size=$("$task_adb" shell wm size | sed -nE 's/.*size: ([0-9]+)x([0-9]+).*/\1 \2/p' | tail -1)
read -r task_width task_height <<<"$task_size"
ensure_demo_foreground() {
    local task_resumed
    task_resumed=$("$task_adb" shell dumpsys activity activities | rg 'mResumedActivity|topResumedActivity|ResumedActivity:' || true)
    if [[ "$task_resumed" != *"$task_package/"* ]]; then
        echo "Stopped: foreground is no longer the X2C demo; no input will be injected." >&2
        exit 1
    fi
}
nodes() {
    ensure_demo_foreground
    "$task_adb" shell input keyevent KEYCODE_WAKEUP
    "$task_adb" shell wm dismiss-keyguard
    "$task_adb" shell uiautomator dump /sdcard/x2c-suite.xml >/dev/null
    "$task_adb" exec-out cat /sdcard/x2c-suite.xml | sed 's/></>\n</g'
}
swipe() { ensure_demo_foreground; "$task_adb" shell input swipe "$((task_width/2))" "$((task_height*8/10))" "$((task_width/2))" "$((task_height*3/10))" 600; }
tap() {
    local task_node task_bounds task_try
    for task_try in {0..16}; do
        task_node=$(nodes | rg -F "text=\"$1\"" | sed -n '1p' || true)
        if [[ -n "$task_node" ]]; then
            task_bounds=$(sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' <<<"$task_node")
            read -r task_x1 task_y1 task_x2 task_y2 <<<"$task_bounds"
            ensure_demo_foreground
            "$task_adb" shell input tap "$(((task_x1+task_x2)/2))" "$(((task_y1+task_y2)/2))"
            sleep 1
            return
        fi
        swipe
    done
    echo "Cannot locate: $1" >&2; exit 1
}
tap_twice_without_intermediate_dump() {
    local task_node task_bounds task_try task_x task_y
    for task_try in {0..16}; do
        task_node=$(nodes | rg -F "text=\"$1\"" | sed -n '1p' || true)
        if [[ -n "$task_node" ]]; then
            task_bounds=$(sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' <<<"$task_node")
            read -r task_x1 task_y1 task_x2 task_y2 <<<"$task_bounds"
            task_x=$(((task_x1+task_x2)/2))
            task_y=$(((task_y1+task_y2)/2))
            ensure_demo_foreground
            "$task_adb" shell input tap "$task_x" "$task_y"
            sleep 2
            # A running Chronometer prevents uiautomator from becoming idle. Stop it at the same
            # stable button coordinate before dumping the accessibility tree again.
            ensure_demo_foreground
            "$task_adb" shell input tap "$task_x" "$task_y"
            sleep 1
            return
        fi
        swipe
    done
    echo "Cannot locate: $1" >&2; exit 1
}
expect() {
    local task_nodes task_try
    for task_try in {0..16}; do
        task_nodes=$(nodes)
        if [[ "$task_nodes" == *"$1"* ]]; then return; fi
        swipe
    done
    echo "Missing expected result: $1" >&2; exit 1
}
back() { ensure_demo_foreground; "$task_adb" shell input keyevent KEYCODE_BACK; sleep 1; }
home() { "$task_adb" shell am force-stop "$task_package"; "$task_adb" shell am start -W -n "$task_package/.MainActivity" >/dev/null; }
favorite() {
    local task_nodes task_try
    for task_try in {0..16}; do
        task_nodes=$(nodes)
        if rg -q 'text="✓ 收藏"' <<<"$task_nodes"; then return; fi
        if rg -q 'text="收藏"' <<<"$task_nodes"; then tap "收藏"; return; fi
        swipe
    done
    echo "Cannot find favorite control" >&2; exit 1
}

echo "[1/3] Capability laboratory"
tap "全能力实验室"
tap "06 · Application / ClassLoader / ID 隔离"
expect "PASS · 真实宿主 Application"
expect "PASS · 插件代码归属"
back
tap "01 · 53 种 View / ViewGroup"
tap "Chronometer"
tap_twice_without_intermediate_dump "启动 / 停止计时"
expect "chronometer=stopped"
back
tap "01 · 53 种 View / ViewGroup"
tap "Chronometer"
tap "启动 / 停止计时"
expect "chronometer=running"
tap "启动 / 停止计时"
expect "chronometer=stopped"
back
tap "01 · 53 种 View / ViewGroup"
tap "运行 53 项类型 / 属性 / 子树断言"
expect "完成 53 项"
if nodes | rg -q 'FAIL '; then echo "Framework constructor assertion failed" >&2; exit 1; fi
back
tap "02 · 组合布局与自定义 View"
expect "PASS · 自定义 CONTEXT 构造 + onDraw"
expect "PASS · 自定义 CONTEXT_ATTRS 构造 + onDraw"
expect "PASS · 自定义 CONTEXT setter"
expect "PASS · 自定义 CONTEXT_ATTRS_DEF_STYLE + 8 种 typed setter"
expect "PASS · 自定义 LayoutParams / childrenReady"
back
tap "03 · Values / Drawable / 宿主资源"
expect "PASS · 宿主 values 回退"
back
tap "05 · Activity / Service / Receiver / Provider"
tap "START"
expect "onStartCommand"
tap "BIND"
expect "Service bind=PASS"
tap "NORMAL"
expect "goAsync.finish=PASS"
tap "ORDERED"
expect "ordered result=PASS"
tap "运行 CRUD / call / batch / observer / file"
expect "Provider CRUD/call/batch/file=PASS"
home

echo "[2/3] Plugin video community"
tap "哔哩哔哩风格首页"
tap "刚升空就坠毁，一字之差竟是机毁人亡，上海莘庄空难真相"
expect "本地 8 秒音视频样片"
tap "播放 / 暂停"
# Saved demo state is intentionally retained across APK upgrades.
favorite
expect "✓ 收藏"
home

echo "[3/3] Normal API workbench"
tap "X2C API 工作台"
expect "PASS · host native merge"
if nodes | rg -q 'FAIL ·'; then echo "Normal API assertion failed" >&2; exit 1; fi
tap "关闭"
tap "点击 +1（旋转/重建后保留）"
expect "点击次数：1"
echo "Three-demo device smoke passed"
