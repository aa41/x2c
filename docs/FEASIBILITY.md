# JAR-first 可行性结论

## 结论

有条件可行。

对源码可控、资源集合封闭、允许 breaking API 的 Android UI/helper library，可以将 allowlisted XML/resources 编译为 Java，输出只含 class 的普通 JAR，并进一步用 D8 输出供 Android `DexClassLoader` 使用的 DEX JAR。

不能承诺把任意 Android library 透明、无损地转换成 JAR。JAR 天生不参与 Manifest merge，也不携带 Android resource table；qualifier 选择、theme/style、framework XML consumer、public `R`、overlay、Data Binding 和系统启动前读取的资源都不是 class 常量可以等价替代的。

## 已验证链路

主环境：AGP 8.11.1、Gradle 8.13、Android Studio JBR 21、compile/target SDK 36、Build Tools 36.0.0。
兼容环境已增加 AGP 3.5.4/Gradle 5.4.1/JDK 8、AGP 4.1.3/Gradle 6.5/JDK 8、
AGP 7.3.1/7.4.2/Gradle 7.5/JDK 11，以及 AGP
8.0.1/8.1.0/8.3.1/8.5.2/8.7.3/8.9.1/8.11.1/8.12.3/8.13.1。各环境均实际验证
generated source → JavaCompile → `codegen.jar` → Activity transform/registry → DEX JAR，并检查
JavaCompile source 集合包含规范生成目录。DEX 任务会自动避开 classfile 版本高于当前 Gradle JVM 的 D8。

```text
真实 res 输入
  -> strict parser / typed model
  -> generated Java implementation（Values/R2/X2cModule/Bootstrap/Provider/Drawables/Layouts）
  -> host x2c-runtime（X2cResources/X2C registry/init/PluginClassLoader/image SPI）
  -> AGP JavaCompile
  -> current-variant JVM compiler outputs
  -> deterministic codegen.jar
  -> Activity superclass/final-callsite transform + generated direct registry
  -> activity-plugin.jar
  -> D8
  -> codegen-dex.jar (classes.dex)

codegen.jar
  -> 独立 Android application file dependency
  -> Java compile + D8 + APK package
```

验证结果：

- 当前 fixture 的 `codegen.jar` 无 Manifest、`res/`、图片、`R.class`、runtime 副本或 metadata；plugin 模式生成完整 synthetic `R2.id/layout/string/color/drawable/dimen/bool/integer/array/plurals/fraction`，`X2cModule` 将私有 provider 与布局注册到宿主持有的 runtime，不再保留重复的 `X2cIds`。producer 业务源码不直接引用任何 generated 类型。
- `codegen-dex.jar` 只有一个有效 `dex\n035` `classes.dex`。
- DEX dump 可见 generated values/layout/drawable/image classes 和 library entry class。
- consumer release APK 构建成功，宿主 `classes.dex` 不含任何 producer/generated class；APK 为两个测试
  payload 各携带 DEX JAR、signed descriptor 和 RSA signature，标准 AGP 仍保留空 `resources.arsc`。
- `aapt2 dump resources` 只输出 `Binary APK`，没有 resource package entries。
- 编译器 36 项自测覆盖完整 synthetic R2、namespace bootstrap 自动发现、normal 模式宿主 ID/本地位图绑定、多 layout runtime registry、未声明 ID 拒绝、framework ViewGroup/LayoutParams 矩阵、扩展 values、Android 文本转义、color selector、shape/selector/组合 drawable 与 API 门槛；未知文本转义会构建期失败。
- producer 中多种构造策略 fixture 加一个真实 `FlowLayout extends ViewGroup`；后者使用自定义 `MarginLayoutParams`、静态 factory、三个类型化 layout setter 和 children-finished hook，生成源码经 Android JavaCompile 成功，报告记录 `customViews=5`。
- JVM probe 已在普通 JDK `URLClassLoader` 中执行并返回 `PASS checksum=175`；同一套内部类、局部类、匿名类、lambda、方法引用、泛型、record、switch、异常、同步与逻辑运算代码已通过 D8。
- producer `DemoActivity` 只存在于 DEX JAR，不存在于 producer/consumer Manifest。当前 fixture 已使用独立
  `business-base` Android library：宿主 `implementation` 副本保持 `BusinessBaseActivity -> Activity`，两个
  插件从 `compileOnly project` 只复制 `@X2cPluginBase` 最小转换单元并转换为
  `BusinessBaseActivity -> PluginActivity`；未复制的 analytics 由两个 PluginClassLoader 缺失回退到同一个
  宿主 ClassLoader。编译器自测同时覆盖 same-JAR 三层链、标注 compileOnly
  base/显式 include/普通宿主依赖 fallback、非 Activity 标注拒绝、外部 runtime dependency链、重复 class、resource AAR 和
  AndroidX fail-closed。generated registry 直接构造 delegate；
  宿主 Manifest 只有 4 种 launchMode × 8 个
  固定代理容器，不再使用 `AppComponentFactory`。loader 对插件业务类 plugin-first、miss 后回退宿主，对
  平台与全部 X2C/plugin runtime 强制 parent-first。模块 provider 按真实 loader 登记，Activity 使用自身
  class 精确取回对应 `X2cResources`。
- JAR 打包前会解析 class constant pool，拒绝非系统 `R`/`R$*`、`TypedArray`、`obtainStyledAttributes`、`Resources.obtainAttributes/getIdentifier`；不会仅靠“产物里没有 R.class”判断安全。
- producer 的 JAR/DEX 任务已验证 configuration cache 可存储并在下一次构建复用。
- 安装器对 canonical pluginId/versionCode/versionName/runtime ABI/dependency closure SHA-256 和
  payload SHA-256 descriptor 执行 RSA 验签；在动态代码落盘前拒绝 runtime ABI 不匹配，写入前设为只读，并拒绝
  降级或相同 versionCode 的内容替换。
- 全部源码中的进程级 `X2C.init(context)` 调用只存在于 plugin demo 的 `HostApplication` 和 normal demo
  的 `NormalApplication`，两者均位于 `attachBaseContext`。loader、业务 Activity、generated bootstrap/module
  不再重新绑定宿主；generated module 只用 `requireInitialized` 校验，并以 volatile 双重检查完成每个
  ClassLoader/module 一次的 Provider/Layout 注册。anchor class 到 module name 的结果也会缓存。
- 已在 LG LM-V600 真机验证宿主持有的 runtime、plugin-first/host-fallback `PluginClassLoader`、动态 Activity
  创建、登录交互与 framework 矩阵；完整导航脚本同时通过宿主→插件→宿主 Result、同插件显式 Intent、
  插件 `startActivityForResult`、两个 ClassLoader 间双向跳转、`singleTop/onNewIntent` 和插件→宿主，并检查
  BaseActivity 探针的 constructor/attach/create/start/resume 次数及 lifecycle `super` 前后顺序，无 `FATAL`、
  `ActivityNotFoundException`、`VerifyError` 或 `AbstractMethodError`。同时捕获并修复了旧 ART
  不提供 `getClassLoadingLock` 的兼容问题。

## 为什么不是 AAR

核心发布物已改为 JAR：

- library 构建依赖使用 `codegen.jar`，由 app 的 D8/R8 统一处理。
- 需要运行时动态加载时，显式构建 `codegen-dex.jar`。
- 支持该开关的新 AGP 会关闭 `LibraryAndroidResources.enable`；旧 AGP 仍可能执行 AAPT/AAR 任务，但
  `x2c*Jar` 从 JVM 编译输出构建并过滤 Android `R.class`，不会把资源或 resource table 放进 JAR。
- `x2c*Jar` 任务只依赖编译 class artifact，不依赖 `bundleAar`。

代价是所有 AAR-only contract 都会消失，包括 Manifest merge、consumer ProGuard、lint model、native libs、assets/JNI 和 resource overlay。当前插件对非空 library Manifest fail closed，但其他契约仍需要继续扩展 verifier。

## 资源语义边界

| 能力 | 当前裁决 | 原因 |
|---|---|---|
| 标量、array、typed array、fraction、plural 数据、ID | 支持 | 生成确定性 Java API；plural quantity 由调用方显式提供 |
| `res/color` selector | 支持 | 生成 `ColorStateList`，支持状态和 alpha |
| 常用 framework View/ViewGroup layout | 支持 allowlist | Linear/Frame/Relative/Grid/Table/Radio/Toolbar 等 LayoutParams 已显式映射 |
| 注册的任意 View/ViewGroup | 条件支持 | 构造、属性、LayoutParams、factory、margin、setter/field 与 hook 全部显式 contract；不要求继承 LinearLayout |
| rectangle/oval/line/ring shape | 支持 allowlist | solid/gradient/corners/dash/size/padding 映射为 `GradientDrawable`；API-only 能力受 minApi 门禁 |
| selector/layer-list/inset/clip/scale/rotate/level-list | 支持同步子图 | 检测未知引用、循环和 bitmap 异步冲突 |
| plugin 位图异步 CDN | 支持 lock + SPI | 原始文件不进 JAR；宿主 loader 校验 HTTPS/MIME/bytes/SHA-256/尺寸并处理取消、重绑、缓存与错误 |
| normal AAR 本地位图 | 支持宿主 Resources | 不要求 lock、不生成 CDN helper，位图保留在 AAR 并使用最终 drawable ID |
| locale/night/density 等 qualifier | 拒绝 | 需要复现 Android best-match 与 configuration change |
| theme/style/styleable/Material | 拒绝 | 依赖 `Resources.Theme`/`TypedArray`/默认 style 语义 |
| vector/ripple/nine-patch/animated drawable | 拒绝 | 仍需 path/theme/state animation 专用编译器或资源表 |
| public `R`、overlay、动态资源名 | 拒绝 | JAR 无 resource table 和 final-link ID |
| Manifest component/system resource | 拒绝 | JAR 不参与 manifest/resource merge |

## 当前 Layout 与自定义 ViewGroup 能力

- LayoutParams：width/height、weight、layout gravity、margin、RelativeLayout ID rules、GridLayout row/column/span/weight、TableRow column/span、AbsoluteLayout x/y；根节点拒绝所有无父容器语义的 `layout_*`。
- 通用 View：padding、background/tint/foreground、visibility 与交互状态、content metadata、transform/min size、layout direction、over-scroll 和 accessibility 常用属性。
- framework：Linear/Frame/Relative/Grid/Table/Radio/Scroll/ViewAnimator/Toolbar 等静态 child 容器；TextView/ImageView/CompoundButton/Progress/Seek/Rating 等常用 typed setter。AdapterView children 继续 fail closed。
- 自定义 View：支持全限定 XML tag 和 `<view class="...">`；构造策略为 `CONTEXT`、`CONTEXT_ATTRS`、`CONTEXT_ATTRS_DEF_STYLE`；自定义属性通过直接 setter 或 public field 支持 string/color/dimension/bool/int/float/gravity/同步 drawable/CDN asset。
- 自定义 ViewGroup：声明 `container` 后可以作为任意层级父节点或根节点，不限制父类；默认使用通用 LayoutParams，也可指定自定义类型、静态 factory、`MarginLayoutParams`、父专属 `layout_*` setter/field 和 public children-finished hook。
- 生成顺序：节点构造与属性 → 创建父容器要求的 LayoutParams → 递归完成并 attach children → children hook → attach 到外层 parent，避免过去固定生成 `LinearLayout.LayoutParams` 的错误。
- fail closed：未知 tag、未知 android/custom 属性、非法 enum/value、相对与绝对 padding/margin 混用都会中止构建。

关键差异是 `CONTEXT_ATTRS` 实际生成 `(context, null)`，三参数策略生成 `(context, null, 0)`。这能选择 Java 构造函数，但不能复现 Android inflater 提供的真实 `AttributeSet`、theme/styleable 和 defStyle 解析；需要这些语义的控件目前不应接入。注册为 container 的 Java 类型及其 factory/setter/field 由生成源码的静态类型检查证明，不靠反射。自定义 View 依赖也不会被 fat-jar 合并，动态加载时必须能从父 ClassLoader 解析。

## 工程决策

1. strict JAR 是独立 breaking artifact，不应静默替换原 AAR 坐标。
2. plugin 模式中 XML 是 generator input，class-only JAR/DEX JAR 是跨版本不含资源的发布边界；normal
   模式保留 AGP resource pipeline，由 module-scoped Provider 按需解析并缓存宿主合并后的真实 resource ID。
3. 只使用 AGP 3.5–8.x 共同存在的 public legacy Variant API。资源目录来自 variant `SourceProvider`；
   `registerJavaGeneratingTask` 负责 Android Studio generated-source model，并额外通过 Gradle 公共
   `JavaCompile.source/dependsOn` 固化真实编译边；
   class 产物来自当前 variant 的 Java/Kotlin/Scala 编译输出，不依赖新版 `ScopedArtifacts` 类。
4. 未支持输入一律构建失败，不生成 `0`、`null` 或 XML fallback。
5. 图片发布是独立 CI 流水线；Gradle 只消费不可变 lock，不上传、不读凭据、不访问网络。
6. 普通 JAR 和 DEX JAR分开，避免把 JVM ClassLoader 与 Android DexClassLoader 混为一谈。
7. `x2c-runtime` 是宿主依赖且只允许一个实例；动态 payload 对它使用 compileOnly，禁止复制进插件。
8. Activity 插件的架构、API 支持矩阵、安全安装协议与 Google Play 风险独立记录在
   [ACTIVITY_PLUGIN.md](ACTIVITY_PLUGIN.md)。

## 双模式边界

- `pluginMode=true`：所有 R2 字段是稳定的编译期常量，适合无 resource table 的动态 payload；这些
  synthetic 值不能传给宿主 `Resources.getString/getDrawable`，业务通过宿主 `X2cResources` 获取值，
  generated Values/Drawables 仅作为 provider 的内部实现。
- `pluginMode=false`：不再生成静态可变 R2 字段或 `R2.init()`。generated Provider 是唯一宿主 ID
  解析入口，在模块白名单校验后首次调用
  `context.getResources().getIdentifier(name, type, context.getPackageName())` 并缓存；返回 0 会抛
  `Resources.NotFoundException`。normal R2 仅保留 `R2.<type>.<name>(context)` 无状态兼容方法，业务推荐
  `X2cResources.id/layout/requireView`。模块初始化使用 volatile 双重检查，资源缓存命中走无锁读取；这些
  运行时 ID 不能用于 `switch case` 或 annotation。
- 生成的 `X2cModule` 消除了 layout switch 对编译期常量的依赖；namespace bootstrap 让两种模式都能以
  业务 anchor Class 自动初始化，不要求业务声明 generated package 或 module name。

## 仍需完成的生产门禁

- 真机覆盖 minSdk/主流 API 的 `DexClassLoader` 加载、UI 创建、进程重启和升级测试。
- AGP 9.x 适配尚未开始；当前承诺范围固定为 AGP 3.5.x–8.x，代表版本矩阵由
  `scripts/verify-agp-compatibility.sh` 回归。
- bytecode 扫描继续扩展：当前已覆盖 `R$`、`TypedArray/styleable`、`getIdentifier`；仍需处理反射/JNI 字符串和 `@*Res` public API 数据流。
- flavor/source-set overlay 的 Android 等价优先级；当前 duplicate resource 会 fail closed。
- R8/minify consumer、Maven fresh consumer、configuration cache relocation 和远程 build cache。
- 生产级 Coil/Glide adapter、磁盘缓存、离线占位/回滚和网络层证书策略；fixture 已覆盖无依赖 HTTP loader
  的最终 HTTPS、MIME/bytes/SHA-256、解码尺寸上限、内存缓存、取消、同 View 重绑、失败与重试。
- 更多 View/attribute/resource type 必须逐项增加 typed model 和标准 inflater 差分测试。

完整前期调研材料保存在 `.agent-workflows/research/20260901-1929-android-resource-codegen/`。其中以 AAR 为默认产物的旧建议已被本实现的 JAR-first 决策取代，但关于 Android resource semantics 的限制结论仍然有效。
