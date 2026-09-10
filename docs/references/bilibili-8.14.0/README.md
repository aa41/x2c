# Android 8.14.0 首页实机参考

来源：用户连接的 LG V60，用户授权 ADB 截图并确认按此版本设计。包名 `tv.danmaku.bili`，versionName `8.14.0`，versionCode `8140300`。不将其声称为已验证的 2026 最新版本。

- `home.png`：1080 × 2460 原图，非页面背景。
- `layout.json`：手工测量的区域/间距/比例。
- `assets.json`：所有裁切素材的来源坐标；由 `python3 scripts/prepare-bilibili-reference.py` 重建。
- 图片与图标在 consumer 的 `drawable-nodpi` 中；插件通过 `X2cResources` 的宿主回退获取。插件没有新增位图或 Android 资源表。

## 实现边界

首页的搜索、频道、轮播容器、视频卡片为 XML2Java；导航和点击状态是原生 View。图标只使用参考图的透明蒙版，不使用 emoji 或外部图标库；标题、计数、时长、作者、轮播标题仍是可访问的真实文本。

四张封面及轮播图只作本地 UI 验证；更多测试条目复用这些封面，不生成不存在的官方素材。标题/作者为测试元数据，不访问平台 API，不建立真实账号、交易或消息。播放仍是明确标注的 8 秒测试视频，所以时长、计数和推荐内容不伪装为官方在线结果。

只掌握首页一张参考图：未提供的消息、创作、会员、个人页及详情页保留本地演示行为，不声称像素级复刻。消息入口解释未连接平台服务，游戏入口筛选本地游戏示例；原“换一批”保留在页面底部、频道菜单和重复点击首页中。

轮播图片按原比例显示，原截图底部文字/指标已从图片裁切区分离，由真实文本绘制。对截去的底部区域使用纯色与渐变承接，不声称重建被文字覆盖的原始图片。状态栏内容由真实系统提供；未复刻参考截图中的时间、电量、透明导航装饰。

## 验证

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' bash scripts/verify-demo-suite.sh
ADB=/path/to/adb ANDROID_SERIAL=device python3 scripts/verify-video-reference.py
```

第二条只操作本项目测试包，不操作真实哔哩哔哩，也不清空应用数据。设备必须已解锁；测试会打开消息/分区/创作弹窗但不提交草稿或订单。
