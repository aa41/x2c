#!/usr/bin/env python3
"""Read/click only this project's installed demo, preserving stored user/demo data."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", "adb")
SERIAL = os.environ.get("ANDROID_SERIAL")
PREFIX = [ADB] + (["-s", SERIAL] if SERIAL else [])
PACKAGE = "dev.x2c.fixture.consumer"


def adb(*args):
    if len(args) >= 2 and args[:2] == ("shell", "input"):
        resumed = subprocess.check_output(
            PREFIX + ["shell", "dumpsys", "activity", "activities"], timeout=25).decode("utf-8")
        active = [line for line in resumed.splitlines() if "ResumedActivity" in line]
        if not any(PACKAGE + "/" in line for line in active):
            raise AssertionError("Stopped: foreground is no longer the X2C demo")
    return subprocess.check_output(PREFIX + list(args), timeout=25).decode("utf-8")


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/x2c-video-reference.xml")
    xml = adb("exec-out", "cat", "/sdcard/x2c-video-reference.xml")
    return list(ET.fromstring(xml).iter("node"))


def find(value, attribute="text"):
    for node in nodes():
        if node.get("package") == PACKAGE and node.get(attribute) == value:
            return node
    raise AssertionError(f"Missing {attribute}: {value}")


def click(value, attribute="text"):
    node = find(value, attribute)
    left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
    if right <= left or bottom <= top:
        raise AssertionError("Invisible target: " + value)
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
    time.sleep(.3)


def main():
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
    click("哔哩哔哩风格首页")
    find("推荐，已选中", "content-desc")
    for name in ("首页", "关注", "发布", "会员购", "我的", "搜索", "游戏分区", "消息", "分区导航"):
        find(name, "content-desc")
    click("消息", "content-desc")
    find("暂无消息。本地 UI 演示不连接哔哩哔哩账号或消息服务。")
    click("知道了")
    click("分区导航", "content-desc")
    click("科技")
    find("科技，已选中", "content-desc")
    click("分区导航", "content-desc")
    click("推荐")
    click("轮播图第2页", "content-desc")
    find("一起发现城市里的精彩")
    click("轮播图第1页", "content-desc")
    find("我来英都，就是为了找到超能力者。")
    click("刚升空就坠毁，一字之差竟是机毁人亡，上海莘庄空难真相，更多操作", "content-desc")
    find("收藏 / 取消收藏")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    click("发布", "content-desc")
    find("创作中心 · 本地草稿")
    click("取消")
    click("关注", "content-desc")
    find("关注动态")
    click("我的", "content-desc")
    find("我的收藏")
    find("观看历史")
    click("会员购", "content-desc")
    find("本页为交互演示，不产生订单或扣款。")
    click("首页", "content-desc")
    click("绝区零联动杯子")
    adb("shell", "input", "text", "2026")
    adb("shell", "input", "keyevent", "KEYCODE_ENTER")
    find("搜索「2026」")
    find("2026最不可思议奇迹：死透的诺基亚突然爆火！每年躺赚的新生意")
    # Return to a clean homepage without clearing any persisted state.
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
    click("哔哩哔哩风格首页")
    find("推荐，已选中", "content-desc")
    print("PASS: reference navigation, channels, carousel, card menu, draft dialog, profile, membership, search")


if __name__ == "__main__":
    main()
