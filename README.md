# X2C Plugin

将受控 Android library 的一部分 XML/resources 编译为 Java class，并输出可独立消费的 JAR。

当前实现是一个可运行的 strict MVP，而不是对任意 Android 资源系统的无损替代。插件模式遇到未知资源、qualifier、Manifest 组件或未锁定的图片时会失败；普通 AAR 模式会保留并正常合并 Library Manifest。

## 编译器结构

对外入口固定为 `dev.x2c.compiler.ResourceCompiler`；原有 overload 保持兼容，新增带 `pluginMode` 的完整入口。实现位于 `dev.x2c.compiler.resource`，按稳定流水线拆分：

| 层 | 主要类 | 职责 |
|---|---|---|
| orchestration | `ResourceCompilationEngine` | 只控制扫描、解析、校验、生成和报告顺序 |
| discovery/model | `ResourceScanner`、`ResourceModel`、`ResourceSymbols`、`SyntheticIdAllocator` | 确定性输入、完整 R2 符号、稳定 ID |
| parser | `ValuesResourceParser`、`ColorResourceParser`、`DrawableResourceParser`、`LayoutResourceParser` | XML 到 model；不生成 Java |
| registry | `FrameworkViewRegistry`、`CustomViewRegistry` | framework/custom View、属性和 LayoutParams contract 的单一事实源 |
| generator | `*SourceGenerator`、`LayoutEmitter` | model 到 Java；不读取 XML |
| infrastructure | `XmlSupport`、`ResourceValueParser`、`JavaExpressions`、`FileSupport` | 安全 XML、typed value、Java 表达式、文件与摘要 |
| report/lock | `CompilationReportWriter`、`AssetLockVerifier` | 报告和 bitmap lock 的独立边界 |

新增资源类型时应先扩展 model，再分别增加 parser 和 generator；新增 framework View/属性只修改 registry 与对应 emitter。不要让 parser 直接写源码，也不要让 generator 重新读取 XML。包级依赖约束同时记录在 `resource/package-info.java`。

## 两种 JAR

| 产物 | 内容 | 用途 |
|---|---|---|
| `codegen.jar` | JVM `.class` | 作为 Android/JVM 构建依赖；Android 构建时由 D8 转为 DEX |
| `codegen-dex.jar` | 单个 `classes.dex` | Android 运行时通过 `DexClassLoader` 加载 |

普通 class JAR 不能被 Android Runtime 直接执行。Android 的 `DexClassLoader` 需要 DEX，因此插件单独提供 D8 任务。两种产物都不包含 `res/`、`assets/`、Android Manifest 或 resource table。

## 当前支持

- 双 ID 模式：plugin 模式生成稳定 synthetic R2；normal 模式通过宿主 `Resources#getIdentifier` 绑定真实资源 ID
- 完整 R2 namespace：覆盖当前编译器支持的 `id/layout/string/color/drawable/dimen/bool/integer/array/plurals/fraction`，不再生成重复的 `X2cIds`
- values: `string`、literal `color`、`bool`、`integer`、`dimen`、`fraction`、`string-array`、`integer-array`、typed `array`、`plurals` 数据和 `<item type="id">`
- color: `res/color` selector（完整状态集合、alpha、default-last）生成 `ColorStateList`
- layout: 每个 `res/layout/*.xml` 都生成独立 factory，通过 `X2cModule` 注册到宿主 `x2c-runtime`；业务以自身 Class 自动发现模块，并通过 `X2cResources.setContentView/inflate` 使用布局
- custom View/ViewGroup: 声明式构造策略、`<view class="...">`、类型化 setter/field、父容器 LayoutParams contract
- drawable: rectangle/oval/line/ring `shape`，支持 solid/gradient、四角半径、虚线 stroke、size、padding；状态 `selector` 支持淡入淡出、镜像、扩展 state 与内联 shape
- drawable composition: `layer-list`、`inset`、`clip`、`scale`、`rotate`、`level-list`，统一执行未知引用、循环和异步 bitmap 混用检查
- bitmap: plugin 模式把 PNG/JPEG/WebP/GIF/AVIF 转为 HTTPS CDN 元数据；normal 模式保留 AAR 位图并通过宿主 `Resources` 同步读取
- resources facade: `getText/getString/getQuantityText/getQuantityString`、color/state list、boolean/integer、dimension/pixel offset/pixel size/fraction、text/string/int/typed array、drawable，以及 optional `findIdentifier/hasResource`
- 安全 XML parser: 禁止 DTD、外部实体和外部 schema
- 确定性 class JAR: 固定 entry 顺序和时间戳，只收集当前 variant 的 Java/Kotlin/Scala project `.class`
- class contract verifier: plugin 模式拒绝业务 bytecode 中的非系统 `R`/`R$*`、`TypedArray/styleable` 和动态资源查询；normal 模式允许 `Resources.getIdentifier` 以支持宿主资源绑定，其余约束保持
- 可选 DEX JAR: 使用 Android SDK D8 生成 `classes.dex`

通用 View 属性覆盖：ID、宽高、weight、gravity、margin、padding、background/tint/foreground、visibility、enabled/click/focus/state、content description/tag/tooltip/transition、alpha/elevation/rotation/scale/translation/min size、layout direction、over-scroll 和常用 accessibility 属性。TextView 家族增加文本、hint、行数、字体样式、ellipsize、inputType/IME；ImageView 家族增加 src/scale/tint；CompoundButton、ProgressBar/SeekBar/RatingBar 也有对应 typed setter。

framework 静态 children 容器覆盖 `LinearLayout`、`FrameLayout`、`RelativeLayout`、`GridLayout`、`TableLayout/TableRow`、`RadioGroup`、两类 ScrollView、ViewAnimator 家族、Toolbar、ActionMenuView 及常见 framework 复合容器；分别生成真实的 Linear/Frame/Relative/Grid/Table/Radio/Toolbar/LayoutParams 和 Relative rule/Grid spec。`ListView`、`GridView`、`Spinner` 等 AdapterView 虽是 ViewGroup，但其 child 由 adapter 管理，因此作为 XML 静态父节点会明确失败。

以下内容继续直接失败：resource qualifier、style/theme/styleable、vector/ripple/nine-patch/animated-vector、Data/View Binding、include/merge、Manifest 资源入口、重复 overlay 资源和未知标签/属性。它们需要 AAPT2 resource table、theme/configuration 或专用运行时协议，不能用普通 class 常量无损替代。

`plurals` 生成显式 `Quantity` API，由调用方提供 CLDR quantity；它不会伪装成 `Resources.getQuantityString()` 的 locale 自动选择。`ring` 的显式 innerRadius/thickness 只在 `x2c.minApi >= 29` 时允许，因为更低版本没有对应 public API。

## 使用

在 Android library 中应用插件：

```kotlin
plugins {
    id("com.android.library") version "8.11.1"
    id("dev.x2c.codegen") version "0.1.0-SNAPSHOT"
}

dependencies {
    // 动态 plugin library 不把 runtime 打入 payload；宿主使用 implementation。
    compileOnly("dev.x2c:x2c-runtime:0.1.0-SNAPSHOT")
}

android {
    namespace = "com.example.library"
    compileSdk = 36
}

x2c {
    pluginMode.set(true)
    generatedPackage.set("com.example.library.generated")
    assetLockFile.set(layout.projectDirectory.file("x2c-assets.lock.json"))
    customViewsFile.set(layout.projectDirectory.file("x2c-custom-views.json"))
    minApi.set(21)
}
```

宿主必须持有唯一的 runtime：

```kotlin
dependencies {
    implementation("dev.x2c:x2c-runtime:0.1.0-SNAPSHOT")
}
```

### 两种资源 ID 模式

| 配置 | R2 字段 | 资源边界 | 适用场景 |
|---|---|---|---|
| `pluginMode=true`（默认） | 编译期 `public static final int` synthetic ID | JAR/DEX JAR 不依赖宿主 resource table | 动态插件、纯 class payload |
| `pluginMode=false` | `X2cModule.init(context)` 时通过 `context.getResources().getIdentifier(name, type, context.getPackageName())` 赋值 | 原始资源必须正常合并进宿主 | 普通 Android library 集成 |

normal 模式的 R2 字段不是 Java compile-time constant，因此可用于 `findViewById`、`getString`、等值比较，
但不能用于 `case R2...` 或要求编译期常量的 annotation。生成布局的分发已经改为 runtime registry，不依赖
`switch`，所以两种模式都可工作。任何 `getIdentifier()` 返回 0 的资源都会在初始化时抛
`Resources.NotFoundException`，不会把无效 ID 继续传递。

Gradle 插件会在 Android namespace 下生成 `x2c.X2cModuleBootstrap`。宿主已经加载插件入口类后，
runtime 会沿入口类包路径找到 bootstrap、初始化内部 `X2cModule` 并返回不暴露模块名称的句柄：

```java
Class<?> pluginEntry = pluginClassLoader.loadClass("com.example.library.PluginEntry");
X2cResources resources = X2C.loadModule(applicationContext, pluginEntry);
```

bootstrap 必须和业务 anchor 来自同一个 ClassLoader，避免 plugin loader miss 后错误认领宿主模块。
插件业务代码直接使用 `X2C.resources(MyPluginClass.class)`，无需声明 module name 或引用 generated 类。

普通 AAR 不需要插件安装器。传入 Context 和任意 library 业务类即可从应用 ClassLoader 按需初始化：

```java
X2cResources resources = X2C.resources(activity, MyLibraryActivity.class);
resources.setContentView(activity, "activity_login");
```

带 moduleName/moduleClassName 的旧 API 继续保留用于兼容和诊断，但不再是业务推荐入口。

Library Activity 仍在自己的 Manifest 中声明，由宿主正常合并，并通过普通显式 Intent 跳转。

任务和输出：

```text
x2cGenerateRelease  -> build/generated/java/x2cGenerateRelease/
                    -> build/reports/x2c/release/report.json
                    -> build/reports/x2c/release/assets-candidates.json
x2cReleaseJar       -> build/outputs/x2c/release/codegen.jar
x2cReleaseDexJar    -> build/outputs/x2c/release/codegen-dex.jar
```

`x2cGenerateRelease` 的规范源码目录是 `build/generated/java/x2cGenerateRelease/`；debug 对应
`build/generated/java/x2cGenerateDebug/`。两个 variant 不共享生成目录，也不会再同时留下旧的
`build/generated/sources/x2c/<variant>`。

`x2cReleaseJar` 不会触发 AAR 打包。插件使用 AGP 3.5–8.x 共同提供的 public legacy Variant API
`registerJavaGeneratingTask` 将目录导出到 Android Studio Gradle Model；插件还显式对当前 variant 的
`JavaCompile` 执行 `source(generatedDirectory)` 与 `dependsOn(x2cGenerate<Variant>)`，避免 IDE/AGP resync
只保留 model 目录却丢失实际编译边。`sourceSets` Gradle 报告只列静态 DSL source set，不列
variant generated source，因此不能用它判断 IDE/JavaCompile 是否已接线；应检查当前 Build Variant 对应的
`x2cGenerate<Variant>` 目录或直接运行对应编译任务。

兼容测试还会直接读取 Android Studio 使用的 AGP v2 Tooling Model，并断言 release artifact 的
`generatedSourceFolders` 包含 `x2cGenerateRelease`，不是只验证命令行 Javac。

Android Studio 只索引当前选中的 Build Variant。若选择 debug，跳转目标是 `x2cGenerateDebug`，而不是
`x2cGenerateRelease`；执行一次 Build/`x2cGenerateDebug` 后即可导航生成源码。

## AGP 兼容范围

插件主字节码为 Java 8（class major 52），生成源码也兼容旧 Javac。当前真实构建矩阵：

| AGP | Gradle/JDK | 结果 |
|---|---|---|
| 3.5.4 | Gradle 5.4.1 / JDK 8 | generated source、JavaCompile、R2/runtime 引用、class JAR 通过 |
| 4.1.3 | Gradle 6.5 / JDK 8 | 同上 |
| 7.3.1、7.4.2 | Gradle 7.5 / JDK 11 | 同上 |
| 8.0.1、8.1.0 | Gradle 8.2.1 / JDK 17+ | 同上 |
| 8.3.1、8.5.2、8.7.3、8.9.1 | Gradle 8.4/8.7/8.9/8.12 / JDK 17+ | 同上 |
| 8.11.1、8.12.3、8.13.1 | Gradle 8.13 / JDK 17+ | 同上 |

3.5.x–8.x 使用同一条公共 API 契约，不依赖仅在新 AGP 存在的 `androidComponents`/`ScopedArtifacts`
类，避免插件在旧 AGP 启动时发生 `NoClassDefFoundError`。AGP 3.x 会把 Android `R.class` 编入 Javac
目录，打 JAR 时会显式过滤它，并继续扫描所有保留 class 对 `R/TypedArray` 等资源运行时 API 的引用。

插件不再全局关闭 AGP resource pipeline，因为 normal 模式必须把资源合并到宿主。跨全部版本保证物理上
不含资源的是 `x2c<Variant>Jar` 与 `x2c<Variant>DexJar`；plugin 模式使用它们作为发布物，normal 模式则
必须把原 Android library/AAR 的资源正常交给宿主，不能只分发脱离 resource table 的 JAR。

完整兼容矩阵可运行：

```bash
X2C_OFFLINE=true ./scripts/verify-agp-compatibility.sh
```

## X2C 布局入口

每个 `res/layout/*.xml` 都会同时生成：

- `R2.layout.<name>`：plugin 模式为稳定 synthetic ID，normal 模式为宿主真实 layout ID。
- `X2cLayouts.<name>(context)`：单布局的底层 factory。
- `X2cResourceProviderImpl`：把 R2、values、drawable 等内部实现适配到 runtime 中间层。
- `X2cModule`：注册 provider、CDN 图片元数据和全部 layout factory。
- `<android namespace>.x2c.X2cModuleBootstrap`：让 runtime 从业务 Class 自动发现上述内部模块。

不会再生成与宿主 runtime 重名且重复的 generated `X2C.java`。`X2cValues`、
`X2cColorStateLists`、`X2cDrawables`、`X2cImages`、`X2cLayouts` 也只在模型中确实存在对应内容时生成；
其中 `X2cImages` 是 package-private 的 CDN metadata/layout bridge，不再暴露重复的 loader/cancel API。
始终保留的最小注册骨架是 `R2`、`X2cResourceProviderImpl`、`X2cModule` 和自动发现 bootstrap。

这些都是生成实现，业务源码不应 import。宿主加载模块后，业务只使用 `x2c-runtime`。推荐由顶层
`X2C` 承担布局动作，由模块句柄承担 value/drawable/ID 查询：

```java
X2cResources x2c = X2C.resources(MyPluginActivity.class);
x2c.setContentView(activity, "activity_login");

View title = x2c.requireView(activity.getWindow().getDecorView(), "title", TextView.class);
View detached = x2c.inflate(context, "row_account", parent, false);
View attached = x2c.inflate(context, "panel_account", parent, true);
String message = x2c.string("welcome_message", userName);
int accent = x2c.color("accent");
```

与原版 X2C 一致，三参数 `inflate(context, layoutId, parent)` 在 `parent != null` 时默认 attach。由于本项目的目标产物不包含 XML、resource table 或 `res/`，未知 layout ID 会明确抛出异常，不会回退到 `LayoutInflater.inflate(resourceId)`。

`dev.x2c.runtime.X2C` 是宿主唯一 runtime。新业务不得依赖 generated R2；`R2` 只服务生成实现，
此前重复的 generated `X2C` 门面和 `X2cIds` 均已移除。

`X2cResourceProviderImpl` 仍然必要：它保存当前模块的资源名称白名单，以及“名称 → 编译后 Java
value/drawable”分发表。plugin 模式的 `getIdentifier()` 返回 generated `R2` synthetic ID；normal 模式先用
生成白名单阻止越权查询，再直接调用宿主
`context.getResources().getIdentifier(name, type, context.getPackageName())`，返回 0 时抛
`Resources.NotFoundException`。normal `R2.init()` 仍需绑定生成布局和 View ID，因此 Provider 不能整体移到
runtime；runtime 并不知道每个模块声明过哪些名称，也不能为无 resource table 的 plugin 模式合成 ID。
通用数组、文本、像素换算和 optional lookup 继续由 `X2cResourceProvider` default method 复用。

这些 API 只覆盖编译器声明支持的资源子集。`openRawResource`、`getXml/getLayout` 返回 XML parser、
`obtainTypedArray/obtainAttributes`、theme/style、density/qualifier 自动选择等需要完整 AAPT2 Resources 语义的
方法不会伪装实现；遇到这类需求应继续使用 normal AAR 的 Android `Resources`。

## 自定义 View 与任意 ViewGroup

自定义 View 必须通过 JSON 明确声明，生成器不使用反射，也不猜测构造函数或 setter：

```json
{
  "schema": 1,
  "views": [{
    "tag": "com.example.widget.StatusView",
    "constructor": "CONTEXT_ATTRS_DEF_STYLE",
    "attributes": [
      { "name": "label", "setter": "setLabel", "type": "STRING" },
      { "name": "accent", "setter": "setAccent", "type": "COLOR" },
      { "name": "spacing", "setter": "setSpacingPx", "type": "DIMENSION_INT" }
    ]
  }]
}
```

构造策略：

| 配置 | 生成代码 |
|---|---|
| `CONTEXT` | `new View(context)` |
| `CONTEXT_ATTRS` | `new View(context, null)` |
| `CONTEXT_ATTRS_DEF_STYLE` | `new View(context, null, 0)` |

setter/field 类型包括 `STRING`、`COLOR`、`DIMENSION` (float px)、`DIMENSION_INT` (rounded int px)、`BOOLEAN`、`INTEGER`、`FLOAT`、`GRAVITY`、`DRAWABLE` 和 `IMAGE_ASSET`。其中 `DRAWABLE` 接收已编译的同步 shape/selector；`IMAGE_ASSET` 接收宿主 runtime 的 `dev.x2c.runtime.ImageAsset`，自定义 View 不再依赖 generated `X2cImages` 类型。

自定义 ViewGroup 不受 `LinearLayout` 继承限制。任何 framework、AndroidX 或业务 ViewGroup 都可以声明父容器 contract：

```json
{
  "tag": "com.example.widget.FlowLayout",
  "constructor": "CONTEXT",
  "container": {
    "layoutParamsClass": "com.example.widget.FlowLayout.LayoutParams",
    "layoutParamsFactoryClass": "com.example.widget.FlowLayout.ParamsFactory",
    "layoutParamsFactoryMethod": "create",
    "marginLayoutParams": true,
    "childrenFinishedSetter": "onX2cChildrenReady",
    "layoutAttributes": [
      { "name": "layout_span", "setter": "setSpan", "type": "INTEGER" },
      { "name": "layout_pinned", "field": "pinned", "type": "BOOLEAN" }
    ]
  }
}
```

- `"container": {}` 使用通用 `android.view.ViewGroup.LayoutParams(int, int)`。
- 自定义 LayoutParams 默认直接调用 `(int width, int height)`；特殊构造方式通过 public static factory `(Context, int, int)` 声明。
- `marginLayoutParams: true` 表示目标类型继承 `ViewGroup.MarginLayoutParams`，生成器会处理全部标准 `layout_margin*`。
- 父容器专属 `layout_*` 必须逐项映射为 setter 或 public field；未知属性在生成阶段失败。
- 子树先完整构造并挂到该容器，之后调用可选的 public children-finished hook，最后才把该容器挂到外层父节点。
- 生成代码全部是直接构造、调用和字段赋值，不使用反射；错误的 ViewGroup、factory、setter 或字段签名会在 Android JavaCompile 阶段明确失败。

当前 contract 面向能够用 `addView(child, layoutParams)` 表达静态 XML children 的 ViewGroup。`AdapterView`/`RecyclerView` 一类由 adapter 管理 children 的容器不应把数据项写成 layout 子节点。后续 annotation processor 可以生成相同 contract；本地源码 View 的资源生成早于 JavaCompile，因此本轮使用显式 JSON，避免扫描未编译注解造成任务时序循环。

两种 AttributeSet 构造策略传入的是 `null`，自定义 XML 属性会在构造后直接调用 setter/field，因此不支持依赖真实 `AttributeSet`、`styleable`、theme 或默认 style 的初始化逻辑。自定义 View 类会进入 project class JAR；它引用的外部依赖不会被合并成 fat JAR，必须由宿主或父 ClassLoader 提供。

## 位图与 CDN lock

`pluginMode=true` 时，图片上传与 Gradle 构建严格分离。CI 根据 `assets-candidates.json` 上传内容寻址对象，
然后提交 lock：

```json
{
  "schema": 1,
  "assets": [
    {
      "name": "hero",
      "url": "https://cdn.example.com/x2c/<sha256>.png",
      "sha256": "<64 hex chars>",
      "mime": "image/png",
      "bytes": 173
    }
  ]
}
```

生成器验证 HTTPS、名称全集、SHA-256、MIME 和字节数。宿主通过
`dev.x2c.runtime.X2cImages.setLoader(ImageLoader)` 安装唯一图片加载器，插件使用
`X2cResources.loadImage`；callback-aware overload 接收 `ImageLoadListener`，可观察 start/success/failure/cancel。
fixture 的无依赖 HTTP loader 会验证最终 HTTPS、HTTP/MIME、精确字节数、SHA-256、解码尺寸上限，支持取消、
同一 View 重绑和基于内容摘要的内存缓存。生产仍可替换为执行同等门禁的 Coil/Glide adapter。

`pluginMode=false` 不要求 asset lock，也不生成 `X2cImages` 或上传候选；原始位图保留在 AAR。生成 layout 的
`android:src` 和 Provider `getDrawable()` 使用宿主最终 `R2.drawable`/`Resources`，保留标准资源打包路径。

## ClassLoader

完整的 library + 动态宿主 app demo 见 [fixtures/README.md](fixtures/README.md)。宿主没有静态依赖
producer JAR，但静态依赖轻量的 `x2c-runtime` JAR。`X2C.init(context)` 固定宿主 Context/ClassLoader；
`PluginClassLoader` 对业务及第三方类 plugin-first，找不到时回退宿主，对 Android/Java/X2C runtime 类
强制 parent-first，避免 runtime 单例、resource provider 接口和图片 SPI 被插件重复加载。宿主使用
`X2C.loadModule(context, pluginEntryClass)` 后，runtime 自动发现 bootstrap，并按 provider 的实际 ClassLoader 建立模块映射；
可以依次加载多个物理 JAR，每个 JAR 使用独立 `PluginClassLoader` 和唯一 `x2c.generatedPackage`。provider、
layout 名称和图片仍在 runtime 内部按生成 moduleName 隔离，业务无需读取或传递它；即使两个模块出现相同
synthetic layoutId，也能通过各自的 `X2cResources` 句柄共存。旧 int-only API 在 ID 有歧义时会明确失败，重复 moduleName 会在注册时
直接报错，避免后加载 JAR 静默覆盖先加载 JAR。
插件 Activity 再通过自身 class 取回 `X2cResources`。DEX JAR 经摘要校验后复制到私有目录，Activity 仍由
`AppComponentFactory` 路由实例化。外部动态下载代码必须增加独立签名校验并遵守应用商店政策。

## 验证

```bash
./scripts/verify.sh
```

fixture 验证真实 values/layout/custom ViewGroup/shape/selector/JPEG lock、JVM 内部类与逻辑代码经由插件生成 class-only JAR 和 DEX JAR；consumer 不静态依赖 producer class，而是把两个 DEX JAR 作为独立动态 payload 加载并跳转到登录、电商 Activity。normal fixture 同时验证同一 JPEG 作为 AAR 本地 drawable。当前实测结果与边界见 [docs/FEASIBILITY.md](docs/FEASIBILITY.md)。
