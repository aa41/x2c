# 三合一 Demo APK

构建：`./gradlew :fixtures:consumer:assembleDebug`。唯一 APK 位于 `fixtures/consumer/build/outputs/apk/debug/consumer-debug.apk`。

仅切换第三个 Demo 的 XML2Java：构建时追加 `-Px2c.normal.enable=true`；省略或设为 false 使用系统资源。两个动态插件的生成模式不受该参数影响。

API 工作台区分两种 inflate 契约：系统路径 detached 时提供父容器专属 LayoutParams，attach=true 返回父容器；生成路径 detached 时保留 factory 的通用根 LayoutParams，attach=true 返回新布局，父容器在 addView 时转换参数。当前不保证这两种返回值/根参数完全一致，测试按实际公开行为展示，不将其当作无差别的 Android inflater 替代品。

首页三个入口：

| Demo | 内容 | 运行方式 |
| --- | --- | --- |
| 全能力实验室 | 53 个 framework 控件 XML；组合布局、通用属性、自定义构造/setter/LayoutParams；values/drawable、图片、宿主回退；四大组件 | Layout DEX + Component DEX，公共 runtime 由宿主提供 |
| 哔哩哔哩风格首页 | 推荐/分区/搜索/刷新/加载更多、视频详情、本地播放、点赞/投币/收藏/关注/评论、历史/草稿、会员套餐选择演示 | Component DEX 内独立 Activity，首页与卡片均由 XML2Java 构建 |
| X2C API 工作台 | 普通 AAR 真实 ID、字符串布局、资源 API、本地图片、inflate attach/detach、原生 merge、缺失资源/类型不匹配、点击状态恢复 | normal-library 静态集成，默认无生成代码 |

这是三个用户场景，不要求三个物理 JAR。两个签名 DEX JAR 由独立 PluginClassLoader 加载：能力实验室可跳转到另一 JAR 的四大组件测试，视频首页也在第二个 JAR 内；普通 API 工作台没有动态加载依赖。

## 全能力实验室测试设计

每个 framework 标签对应独立 `probe_*.xml`，来自当前编译器的 53 项注册表。预览显示预期与实际结果；批量操作逐项验证类型、synthetic ID、alpha、enabled、padding、tag、父 LayoutParams。其中 20 个公开可组合容器包含真实 XML 子树，验证 Linear weight、Frame 覆盖、Relative 规则、Grid 跨列、Table、RadioGroup、双向滚动条、Absolute 坐标、Switcher、TabHost、Toolbar 等专属关系；日期、搜索等拥有系统内部子树的复合控件用公开 API 配置，不向内部结构强塞业务子 View。列表、切换器、TabHost、Toolbar、搜索、选项、滑杆等均有实际交互；Chronometer 的启动/停止额外验证 synthetic `R2.id` 可安全用于 keyed `View#setTag`，WebView 使用本地 HTML并在退出时销毁。

通用属性页包含背景/前景/tint、状态、焦点、变换、最小尺寸、布局方向和无障碍属性。`framework_matrix.xml` 验证组合关系；`custom_views.xml` 现在提供可见 onDraw 效果，并独立断言 `CONTEXT`、`CONTEXT_ATTRS`、`CONTEXT_ATTRS_DEF_STYLE` 构造、八种 typed setter、三子项自定义 LayoutParams 和 childrenReady 回调。`android:textColorHint` 与 `android:scrollbars` 由 XML 编译器校验并直接生成 setter，不再由 Demo 补写。注册表覆盖不等于每个控件的所有 Android 属性都受支持，也不等于已穷举所有属性组合；未知属性和不支持的资源仍由编译器严格拒绝。

Values 覆盖当前 11 类资源命名空间；Drawable 覆盖 rectangle/oval/line/ring、状态选择器、layer/inset/clip/scale/rotate/level-list 和 color selector。ring 显式内半径/厚度要求 minApi≥29，当前 minApi21 的示例使用默认尺寸。插件 XML 不支持的 include/merge、vector、styleable 等不伪造成功；普通系统资源路径的 merge 在 API 工作台单独验证。

图片页使用此前提供的 3 张插件本地 JPG→CDN 元数据及 2 张宿主 JPG。记录 LOADING/PASS/FAIL，支持重载/取消；离线失败不会标记为 PASS。宿主同名资源不能覆盖插件声明；宿主独有 value/drawable 允许回退；id/layout 不越界回退。

四大组件页测试四种 launchMode、同/跨插件与宿主跳转、Activity Result、Service start/bind/stop、普通/有序广播与 goAsync、Provider CRUD/call/batch/observer/file。沿用底层组件实现作为能力页的一部分，旧独立组件首页入口已移除。

## 视频社区交互范围

首页现按用户确认的 Android 8.14.0 实机参考实现；来源、裁切坐标及视觉/功能边界见 [参考说明](references/bilibili-8.14.0/README.md)。首页封面和线性图标来自这张参考图，存放在宿主 drawable 中；后续条目复用封面。它不是未经核验的“2026 最新版完整客户端”。

数据和状态均为本地测试，不接入真实哔哩哔哩账号、评论、推荐、支付或上传服务。收藏、点赞、关注、评论、历史与草稿存入独立 SharedPreferences；清空操作需要确认且只清除此 Demo 的偏好数据。

宿主 `raw/x2c_sample.mp4` 是用 FFmpeg 生成的 8 秒 H.264/AAC 测试图与音轨，所有卡片共用这一段样片。支持播放/暂停、系统播放器进度控件、全屏、后台暂停和进度保存；不将测试样片宣传为标题对应的真实节目。分享会打开 Android 系统分享面板，由使用者选择目标。

## 验证与清理

- `bash scripts/verify.sh`：编译器/runtime 基础回归与 APK/DEX/资源完整性检查。
- `bash scripts/verify-demo-suite.sh`：构建 APK，并检查 53 个 XML 与注册表一致、两个资源为空的 DEX 载荷、新 Activity/四大组件入口、普通模块无生成代码、宿主视频存在。
- `ADB=/path/to/adb ANDROID_SERIAL=device bash scripts/verify-plugin-showcases.sh`：设备交互冒烟。只覆盖关键路径，不代表对全部交互的穷举验证。

已移除旧登录流程、JVM 功能演示类、旧 ClassJarSmoke、normal-app 独立工程与旧双入口启动页；保留基础库自动化测试，以及新能力页仍需要的 XML/自定义 View/四大组件基础实现。源码删除可通过版本控制恢复。新的唯一 APK 不会依赖历史安装的另一个应用。
