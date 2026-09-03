# X2C dynamic JAR demo

这套 demo 同时验证两条互不混淆的集成路径：

- `producer` + `producer-secondary` + `consumer`：两个 `pluginMode=true` 无资源 DEX JAR 的顺序动态加载。
- `normal-library` + `normal-app`：`pluginMode=false` 的标准 AAR 静态依赖。

## producer：测试 library

`fixtures/producer` 应用 `dev.x2c.codegen`，产出：

- `build/outputs/x2c/release/codegen.jar`：只含 JVM `.class`。
- `build/outputs/x2c/release/codegen-dex.jar`：只含 Android `classes.dex`。

它覆盖当前核心资源链路：标准拆分的 strings/colors/dimens/bools/integers/arrays/plurals/fractions/ids、`res/color` selector、完整 synthetic `R2` namespace、宿主 runtime `X2C.setContentView/inflate`、framework View/ViewGroup 专属 LayoutParams、自定义 ViewGroup contract、扩展 shape/selector、组合 drawable 和 CDN bitmap metadata。

额外运行时代码：

- `JvmFeatureProbe`：静态/非静态内部类、局部类、匿名类、lambda、方法引用、泛型、record、enum switch、数组、位运算、布尔逻辑、异常、同步方法、默认接口方法和 ClassLoader identity。
- `DemoActivity`：只打进 JAR/DEX JAR，不在 library Manifest 声明；页面会创建由 XML 生成的成熟登录页和 framework 能力矩阵。
- `producer-secondary/SecondaryActivity`：第二个 ClassLoader 中的完整电商页，覆盖 CDN 商品图、收藏、SKU、数量、购物车、购买、失败/取消/重试/缓存状态。
- `LoginLogic`：不依赖 Android 的登录状态机，覆盖空值、邮箱格式、密码长度、账号归一化、错误凭据和成功凭据。
- `LoginInputView`：通过 custom view contract 生成，验证自定义构造函数、typed setter、输入法与密码模式。

登录页交互覆盖：

- 邮箱/密码输入与逐字段错误提示；
- 密码显示/隐藏、记住账号与 Activity 重建恢复；
- 忘记密码、演示账号一键填入；
- 微信、GitHub 和隐私政策入口反馈；
- 登录中、失败、成功状态和关闭返回；
- CDN 图片的异步加载与取消请求。

页面根 `ScrollView` 直接来自 `content.xml`，Activity 通过自身 Class 自动取得 `X2cResources`，再调用句柄的 `setContentView/inflate`；业务不声明或传递 moduleName。独立的 `framework_matrix.xml` 用于验证多 layout 分发及多 JAR ID 隔离。能力矩阵覆盖 FrameLayout、RelativeLayout ID rule、GridLayout row/column/spec/weight、TableLayout/TableRow、RadioGroup、HorizontalScrollView、Button/CheckBox/Switch/ImageButton、ProgressBar/SeekBar/RatingBar。

producer Manifest 保持为空，验证 JAR-first library 不依赖 Manifest merge。

## consumer：动态宿主 app

`fixtures/consumer` 没有对 producer JAR 的 `implementation`/`compileOnly` 依赖。构建时只把 `codegen-dex.jar` 作为测试 payload 放入签名 APK：

consumer 通过 `implementation(project(":x2c-runtime"))` 持有 runtime；producer 仅 `compileOnly`，因此
payload 中不会出现第二份 `dev.x2c.runtime.*`。

```text
assets/x2c-demo/codegen-dex.jar
assets/x2c-demo/codegen-dex.sha256
assets/x2c-demo-secondary/codegen-dex.jar
assets/x2c-demo-secondary/codegen-dex.sha256
```

运行流程：

```text
HostApplication.attachBaseContext
  -> X2C.init(baseContext)，记录宿主 ClassLoader
  -> 从 APK asset 复制 DEX JAR 到 app 私有 files 目录
  -> 校验 SHA-256
  -> 创建 PluginClassLoader（plugin-first，miss 时 host fallback，平台/runtime parent-first）
  -> X2C.loadModule(context, entryClass) 从业务包路径发现 generated bootstrap
  -> bootstrap 反射无关地初始化内部 X2cModule，并返回生成的唯一模块标识
  -> 注册 runtime resource provider、CDN metadata 和全部 layout factory
  -> 反射执行 FixtureLibrary/JvmFeatureProbe
  -> 创建第二个 PluginClassLoader 并加载 producer-secondary
  -> 自动发现并注册 secondary module，验证 primary module 仍可访问
  -> HostApplication 安装共享 VerifiedHttpImageLoader

点击宿主按钮
  -> 显式 Intent 指向 dev.x2c.fixture.producer.DemoActivity
  -> Activity 声明来自 consumer AndroidManifest.xml
  -> DynamicAppComponentFactory 使用独立 PluginClassLoader 实例化 Activity
  -> DemoActivity 通过 X2C.resources(DemoActivity.class) 取回对应 runtime 句柄
  -> 打开 JAR 内的 DemoActivity 页面

点击“打开动态电商页面”时，`DynamicAppComponentFactory` 改用第二个 `PluginClassLoader` 实例化
`SecondaryActivity`。商品图使用题给百度 JPEG；JAR 内只有 URL/MIME/bytes/SHA-256 元数据，宿主下载后
逐项校验。页面可主动触发成功、内容缓存、同 View 重绑、取消、故障 URL 与成功重试。
```

宿主使用 framework `AppComponentFactory`，因此 demo app 的 minSdk 是 28。API 21–27 若需要直接加载 Activity，应实现代理 Activity/Instrumentation 方案；library 本身仍保持 minSdk 21。

## normal-library / normal-app：标准 AAR

`normal-library` 设置 `pluginMode=false`，通过 `api(project(":x2c-runtime"))` 暴露 runtime，原始
`res/`、resource table 和 Library Manifest 均保留在 AAR 中。`normal-app` 只声明自己的 launcher
Activity，并通过普通静态依赖和显式 Intent 跳转：

```java
startActivity(new Intent(this, NormalDemoActivity.class));
```

Library Activity 的 Manifest 声明由 Android 标准 Manifest Merger 自动进入宿主，不需要
`AppComponentFactory`、反射、DEX payload 或宿主重复声明。页面通过 runtime 高层入口加载：

```java
X2cResources resources = X2C.resources(this, NormalDemoActivity.class);
resources.setContentView(this, "normal_content");
```

首次调用会从业务 Class 的包路径找到 `<namespace>.x2c.X2cModuleBootstrap`，再从应用
`PathClassLoader` 按需初始化生成的 `X2cModule`；normal `R2` 使用
`Resources#getIdentifier` 绑定宿主最终的 `R.layout/R.id/...`；generated Provider 也在模块名称白名单校验后
直接调用宿主 `Resources#getIdentifier`。Demo 会在运行时断言 X2C layout/ID 与 Android R 完全相等，
并显示保留在 AAR 中的同一张本地 JPEG，证明它没有被转为 CDN metadata。

## 构建与运行

完整验证：

```bash
./scripts/verify.sh
```

AGP 8.11.1 要求 JDK 17+。如果当前 `JAVA_HOME` 较旧，可使用 Android Studio 自带 JBR：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./scripts/verify.sh
```

生成可安装的 debug APK：

```bash
./gradlew :fixtures:consumer:assembleDebug
adb install -r fixtures/consumer/build/outputs/apk/debug/consumer-debug.apk
adb shell am start -n dev.x2c.fixture.consumer/.MainActivity
```

打开宿主页后点击“打开动态登录页面”。动态 Activity 在宿主 Manifest 中声明为 `exported=false`，因此必须由宿主应用内的显式 Intent 启动；ADB shell 直接启动会按预期被 Android 安全策略拒绝。

普通 AAR demo：

```bash
./gradlew :fixtures:normal-app:assembleDebug
adb install -r fixtures/normal-app/build/outputs/apk/debug/normal-app-debug.apk
adb shell am start -n dev.x2c.fixture.normalapp/.MainActivity
```

已在 LG LM-V600 真机验证宿主页、动态登录 Activity、电商 Activity、真实 CDN 成功/失败/取消/重试、
SKU/数量/购物车交互，以及 normal AAR 本地图片。图片测试截图位于
`.agent-workflows/screenshots/image-ecommerce/`。

## 生产注意事项

demo payload 位于已签名 APK 内，SHA-256 用来验证私有目录复制结果。若改为网络下载，必须增加独立签名、公钥校验、版本/回滚策略、下载与存储上限，并确认应用商店对动态执行代码的政策；图片 CDN 不应承载可执行 DEX。
