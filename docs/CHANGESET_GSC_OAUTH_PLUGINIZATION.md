# gscandroidkit OAuth 插件化兼容改造说明

## 1. 文档目的

本文记录 2026-09-16 为 `gscandroidkit` 的 `OAuthLoginActivity` 接入 X2C 动态插件时，
对 `x2c-plugin` 做的通用能力补全。它用于在另一个 Android 项目中独立审查以下问题：

- 修改是否解决真实的 Android/Gradle 兼容问题；
- 修改是否属于通用插件 SDK 能力，而不是对单一业务页面的硬编码；
- 原有 normal mode、其他 `BaseActivity` 子类和宿主代码是否保持兼容；
- 正向能力和 fail-closed 边界是否都有自动化或真机证据。

本次变更基于提交 `c7480b3418bafd83bbc75c1939c79b62fdf56032` 的工作树差异。
本文是变更审查说明，不是发布记录；当前工作树尚未因此自动创建版本或提交。

## 2. 原始约束

消费项目提出了四个不可放宽的约束：

1. `OAuthLoginActivity` 必须继续继承现有业务 `BaseActivity`；
2. View 树必须由 X2C 从原布局转换，不能在业务代码中手写或复制布局；
3. 只改插件入口所需路径，不能改变其他 `BaseActivity` 子类的正常生命周期和
   `setContentView(initLayout())` 逻辑；
4. Router 的 boolean 灰度/测试开关属于消费项目，X2C SDK 只提供可靠的插件装载、
   Activity 代理和资源生成能力，不能反向依赖 Router 或业务类。

因此，本仓库的实现中没有 `OAuthLoginActivity`、Router、业务包名或页面专用判断。

## 3. 修改总览

| 能力 | 触发问题 | 通用实现 | 主要验证 |
|---|---|---|---|
| Gradle 5.4 Provider 兼容 | 老项目没有 `Provider.orElse(Provider)` | 用 `project.provider` 保持 `pluginMode` 显式值优先、否则回退 `x2cEnable` | 老项目 clean assemble 成功 |
| 宿主资源依赖推断 | 资源为空插件的布局需要少量宿主 value/drawable，手工白名单维护成本高 | plugin layout 自动推断本地缺失引用，报告精确依赖并在模块发布前验证 | 正向、normal mode 隔离、generated drawable 拒绝 |
| 本地 `<include>` | 原布局依赖 include，不能手写展开 | 编译期展开本地 layout，复刻 id/visibility 与整组 LayoutParams 规则并检测环 | 差分语义和循环引用失败测试 |
| 根 LayoutParams 审查 | 固定生成 `FrameLayout.LayoutParams` 会破坏任意 parent 的 inflate | 新增向后兼容的 `ParentAwareLayoutFactory`，按真实 framework/custom parent contract 生成 | detached/attached 保留根 parent 属性 |
| 自定义 View framework 能力继承 | 业务自定义 TextView 需要通用 text/drawable 属性 | `customViewsFile` 新增可选 `frameworkBase` | 自定义类仍被直接构造，TextView setter 被生成 |
| TextView compound drawable | 登录布局使用 `drawableStart`/`drawablePadding` 等属性 | 增加 typed drawable/padding 校验和 setter 生成 | Resource compiler 自测 |
| Java 8 匿名/局部类闭包 | `BaseActivity.class` 被复制但 `$1/$2` 遗漏，运行时报 `IllegalAccessError` | 读取 `EnclosingMethod` owner，将 owner 已入选的匿名/局部类纳入结构闭包 | 匿名类 JAR 回归测试和真机 |
| Activity API 容器桥接 | delegate 执行 `setFinishOnTouchOutside` 访问未初始化 `mWindow` | 非 final 方法正常 override；真正 final API 使用 receiver-aware 静态桥 | ASM 虚分派/静态桥断言 |

Activity bridge 曾将宿主/插件二进制协议由 2 提升为 3；宿主主题 metadata 契约将协议提升为 4；
Opaque/Translucent 双 Window Profile 容器与 metadata 将当前协议提升为 5。宿主必须拒绝所有非 ABI 5
payload。`x2c-plugin-runtime` 同时携带 consumer rules，保留后发 DEX 动态调用的 X2C SDK ABI。

## 4. 详细设计

### 4.1 Gradle 5.4 到 8.x 的 Provider 兼容

修改位置：

- `x2c-gradle-plugin/.../X2cCodegenPlugin.java`
- `x2c-gradle-plugin/activity-gradle-plugin/.../X2cActivityPlugin.java`

旧代码调用 `extension.getPluginMode().orElse(extension.getX2cEnable())`。该调用在新 Gradle 可用，
但消费项目使用的 Gradle 5.4 API 不提供这个 overload。现在由 `project.provider` 延迟计算：

```text
pluginMode 有显式值 -> 使用 pluginMode
pluginMode 未配置   -> 使用 x2cEnable
```

这个修改只影响 task input 的取值方式，不改变 DSL 优先级，也不把值提前固化到配置阶段。

### 4.2 宿主资源依赖自动推断

plugin mode 解析 layout 时，本地不存在的 `string`、`color`、`bool`、`integer`、`dimen`、
`drawable` 引用会自动成为宿主依赖，不再要求维护额外 DSL 或 JSON 白名单。推断只在 layout 阶段开启：

- 插件自身的 values 和 generated drawable 必须继续自包含；
- normal mode 的未知资源仍在构建期失败；
- `id`、`layout`、fraction、array 和 plurals 不参与 layout 宿主推断；
- 运行时仍遵循 plugin-first，插件声明的同名资源优先；
- 动态名称依赖可能被 Android resource shrinker 删除，宿主必须提供静态引用或 `tools:keep`。

`report.json` 记录精确的推断结果：

```json
{
  "counts": { "inferredHostResources": 4 },
  "inferredHostResources": {
    "color": ["host_text"],
    "dimen": ["host_width"],
    "drawable": ["host_panel"],
    "string": ["host_title"]
  },
  "invariants": ["host-resource-dependencies=inferred-and-preflighted"]
}
```

生成模块在注册 provider/layout 前逐项调用宿主资源解析，缺失时模块初始化失败，不会把半初始化布局发布出去。

### 4.3 `<include>` 编译期展开

资源扫描阶段先建立 layout 文件索引；解析 `<include layout="@layout/name">` 时重新解析被包含布局。
编译期规则对齐 `LayoutInflater`：`id`、`visibility` 特殊覆盖；include 同时提供 width/height 时，所有
include-site `layout_*` 作为一个完整 LayoutParams 集合替换被包含根参数；缺少任一尺寸时整组回退被包含根
参数。`text` 等普通 View 属性不会从 include site 覆盖，但仍按 included root 的能力矩阵校验；未知属性、
错误 namespace 和 `visiblity` 一类拼写错误会在构建期失败，不会被静默吞掉。

当前边界：

- 只接受同一插件资源模型中的 `@layout/name`；
- 不接受动态名称或宿主 layout；
- 循环 include 在构建期失败，并输出引用链；
- `<merge>` 仍不支持；
- qualifier/overlay 仍不支持。

这是源码生成阶段的结构展开，不会在运行时重新读取 XML。

### 4.4 根布局 parent contract

不能把所有根 View 固定成 `FrameLayout.LayoutParams`，因为 `X2C.inflate(..., arbitraryParent, false)`
必须使用调用方传入的真实 parent contract。runtime 因此保留原 `LayoutFactory`，并新增其子接口
`ParentAwareLayoutFactory`；旧插件继续调用 `create(Context)`，新插件由 runtime 检测后调用
`create(Context, ViewGroup)`，不会形成强制重打包的 ABI 断层。

生成布局按实际 framework parent 类型创建对应 LayoutParams，自定义 ViewGroup 则使用
`customViewsFile.container` 声明的 class/factory/margin/typed layout attribute contract。只有 width/height
时未知 parent 可退回通用 LayoutParams；存在 margin/gravity 或自定义 `layout_*` 等父专属语义时未知 parent
明确失败。`<include>` 仍在编译期以内联模板展开，不按出现次数复制公开类，因此 XML 顺序变化不会制造
`${parent}_${include}_${count}` 一类不稳定符号。

### 4.5 自定义 View 的 `frameworkBase`

`x2c-custom-views.json` 可声明自定义类实际继承的 framework View 行为：

```json
{
  "schema": 1,
  "views": [{
    "tag": "com.example.widget.LabelView",
    "frameworkBase": "TextView",
    "constructor": "CONTEXT"
  }]
}
```

生成器仍直接构造 `LabelView`，只复用 `TextView` 已支持属性的校验和 typed setter 发射。
`frameworkBase` 必须是 registry 已支持的 framework tag，不能填写任意类名。

这是静态 contract：编译器当前不会加载消费项目 class 来证明 `LabelView instanceof TextView`。
合同维护者必须保证真实继承关系，否则生成的 Java 会在消费项目编译阶段以缺失 setter 的形式失败。

本次同时加入 TextView compound drawable 属性：

- `drawableLeft`、`drawableTop`、`drawableRight`、`drawableBottom`；
- `drawableStart`、`drawableEnd`；
- `drawablePadding`。

同步 compound drawable 不能绑定 plugin CDN bitmap：plugin 模式在构建期明确失败。normal 模式保留本地
bitmap，并生成 `Resources.getDrawable(getIdentifier(...))`；插件本地 drawable 始终先于宿主 fallback 判定，
同名本地 bitmap 不会被宿主 drawable 错误替换。

### 4.6 framework XML 兼容调整

- `RelativeLayout` 接受 `android:orientation`，与 Android framework 一样忽略它；
- 允许同时存在 absolute 和 relative margin，先生成 `setMargins`，再生成
  `setMarginStart/setMarginEnd`，保持 Android 的相对边覆盖语义；
- 未注册属性仍然 fail closed。

### 4.7 Java 8 匿名/局部类结构闭包

`@X2cPluginBase` 的 compileOnly 提取必须把需要同一 ClassLoader/package access 的结构成员作为一个转换单元。
现代 class 文件的 nest 成员已有 `NestHost` 处理，但 Java 8 匿名/局部类主要通过
`EnclosingMethod`/`visitOuterClass` 关联 owner。

现在 `CompileOnlyBaseClosure` 记录 `enclosingOwner`，固定点闭包中只要 owner class 已入选，就把对应匿名/局部
class 一并纳入。它不会按普通引用把整个 compileOnly 依赖递归打包，因此 analytics、账户、网络等宿主依赖
仍保持 host fallback。

对应真实故障为：payload 有 `BaseActivity.class`，却缺少 `BaseActivity$1.class`、`BaseActivity$2.class`，
运行时抛出 `IllegalAccessError`。修复后的 OAuth payload 已确认同时包含这三个 class。

### 4.8 Activity API 桥接

插件 Activity 对象是 delegate，不是 Android framework 实际 attach 的容器。直接执行
`Activity.setFinishOnTouchOutside(boolean)` 会读取 delegate 内未初始化的 `mWindow`。

该方法不是 final，正确实现是在 `PluginActivity` 中 override 并转给真实容器，不需要字节码特例：

```text
virtual Activity.setFinishOnTouchOutside(Z)V
  -> PluginActivity override
  -> requireContainer().setFinishOnTouchOutside(Z)V
```

真正 final 的 Activity API 改写到 `ActivityApiBridge` 静态方法。bridge 接收原 Activity 对象，只有对象是
`PluginActivity` 时才切换到容器，否则对外部真实 Activity 执行原 API，避免基于调用类猜测接收者。

## 5. API 与兼容性结论

### 5.1 保持兼容的部分

- `ResourceCompiler` 保持原 public compile 签名，不新增宿主资源配置参数；
- normal mode 的原生 `Resources/LayoutInflater` 路径不变；
- Activity transform 只作用于插件 module 输入和被明确选中的 compileOnly base 结构闭包；
- 宿主原始 `BaseActivity` class 不被替换，其他原生子类不受影响；
- 业务 Activity 仍可继承自身多层 BaseActivity，不要求改为 SDK 专用基类；
- 生成布局仍来自原 XML，不引入手写 View 树或运行时 XML 反射 fallback。

### 5.2 明确不支持或未承诺的部分

- `<merge>`、resource qualifier/overlay、style/theme/styleable、Data/View Binding；
- 宿主 `id`/`layout` fallback；
- generated drawable 内引用宿主资源；
- 自动修改宿主 `tools:keep`；
- 自动验证 `frameworkBase` 的真实 Java 继承关系；
- 未列入 bridge 表的任意 final Activity API；
- 热替换已加载插件。升级仍要求新进程激活。

## 6. 文件级变更索引

| 文件/目录 | 变更职责 |
|---|---|
| `GenerateX2cTask.java`、`ResourceCompiler.java` | 保持稳定的 Gradle/compiler 输入边界 |
| `resource/HostResourceDependencies.java` | layout 阶段宿主依赖推断与确定性报告 |
| `ResourceCompilationEngine.java`、`ResourceModel.java` | 控制推断仅在 plugin layout 阶段可见，预索引 layouts |
| `ResourceValueParser.java`、`CompilationReportWriter.java` | 引用校验和报告 |
| `LayoutResourceParser.java` | include、root contract、frameworkBase、compound drawable 校验 |
| `LayoutEmitter.java` | parent-agnostic root 宽高、compound drawable setter 生成 |
| `CustomViewRegistry.java`、`FrameworkViewRegistry.java` | frameworkBase 与属性注册 |
| `x2c-runtime/.../X2C.java` | 以真实 content parent 创建 generated root，并保留根 LayoutParams |
| `CompileOnlyBaseClosure.java` | Java 8 enclosing-owner 结构闭包 |
| `ActivityBytecodeRewriter.java` | final API callsite 映射 |
| `x2c-plugin-runtime/.../PluginActivity.java` | 真实容器 bridge |
| `X2cCodegenPlugin.java`、`X2cActivityPlugin.java` | Gradle 5.4 Provider 兼容 |
| 两个 `*SelfTest.java` | 52 项资源编译测试和 Activity transformer/ABI 回归 |
| `README.md`、`skills/x2c-android-integration/**` | 对外能力、合同、排障同步 |

## 7. 已完成验证

### 7.1 X2C 仓库

```bash
./gradlew -p x2c-gradle-plugin compilerTest :activity-compiler:compilerTest
./gradlew verifyX2c --offline --configuration-cache
```

已观察到：

- `ResourceCompilerSelfTest: 51 tests passed`；
- `ActivityJarTransformerSelfTest: passed`；
- `verifyX2c: BUILD SUCCESSFUL`。

发布候选还应执行完整矩阵：

```bash
./scripts/verify.sh
./scripts/verify-plugin-modes.sh
X2C_OFFLINE=true ./scripts/verify-agp-compatibility.sh
```

### 7.2 gscandroidkit 消费项目

- JDK 11、Gradle 5.4.1、AGP 3.5.x 路径 clean assemble 成功；
- OAuth payload descriptor：`pluginId=com.gsc.oauth_login`、`versionCode=3`；
- payload SHA-256：`4c785e9fd3d2ee02f179c3c2c18eda99dfb933d46c5042379d9e2b2aadb83156`；
- 真机：LG LM-V600，Android API 31；
- 安装日志：`Installed OAuth plugin version=3`；
- Router 入口最终前台为 `PluginContainerActivities$SingleTask0`；
- OAuth 授权登录 View 完整渲染；
- 未出现 `FATAL EXCEPTION`、`IllegalAccessError`、`VerifyError` 或
  `setFinishOnTouchOutside` 空指针。

真机结果证明本次消费路径可运行，但不替代跨 AGP、跨 API 和负向安全矩阵。

## 8. 在另一个项目中的独立验收步骤

### 8.1 发布本地候选

使用 JDK 17 构建当前 X2C 仓库（其基线 AGP 8.11 要求 JDK 17），并发布同一版本的一整套构建期和
运行时 artifact，避免只替换 compiler。消费项目构建时再使用其 AGP/Gradle 矩阵要求的 JDK：

```bash
export JAVA_HOME=<path-to-jdk-17>

./gradlew -p x2c-gradle-plugin \
  publishToMavenLocal \
  :activity-compiler:publishToMavenLocal \
  :activity-gradle-plugin:publishToMavenLocal

./gradlew \
  :x2c-runtime:publishToMavenLocal \
  :x2c-plugin-api:publishToMavenLocal \
  :x2c-plugin-base:publishToMavenLocal \
  :x2c-plugin-runtime:publishToMavenLocal \
  :x2c-plugin-loader:publishToMavenLocal
```

消费项目应明确启用 `mavenLocal()`，并记录实际解析到的 module/version，不要混用旧 runtime 与新 compiler。

### 8.2 最小正向矩阵

1. Activity 继承至少一层业务 `BaseActivity`，base 中包含 Java 8 匿名 `Runnable`；
2. base 的 `onCreate` 调用 `setFinishOnTouchOutside(false)`；
3. 页面 XML 包含本地 `<include>`，覆盖 id/visibility 和完整 LayoutParams；
4. 页面包含继承 `TextView` 的自定义 View，并通过 `frameworkBase=TextView` 使用
   `text`、`drawableStart` 和 `drawablePadding`；
5. layout 引用一个插件本地不存在的宿主 string/dimen/drawable，并审计自动推断报告；
6. 构建 `activity-plugin.jar` 和 `codegen-dex.jar`，安装后从宿主路由进入插件页面；
7. 核对页面层级、根布局位置、include 内容、宿主资源和返回/生命周期。

### 8.3 必须执行的负向矩阵

- 宿主缺少一个 `inferredHostResources` 名称时，模块初始化必须在注册前失败；
- normal mode layout 引用本地不存在的资源，构建必须失败；
- 在 generated drawable 中引用宿主 color，构建必须失败；
- 制造两个 layout 循环 include，构建必须失败；
- 填写未知 `frameworkBase`，构建必须失败；
- 使用未桥接且被规则禁止的 final API，构建必须失败；
- 从 payload 人工移除 `BaseActivity$1.class`，运行应能复现旧问题，以证明测试确实覆盖故障模型；
- 关闭业务 Router 插件 boolean 开关，必须仍进入原生 Activity。这一项验证消费项目路由，
  不属于 X2C compiler/runtime 的职责。

### 8.4 产物审计

```bash
jar tf <activity-plugin.jar>
javap -classpath <activity-plugin.jar> -c -p <business.BaseActivity>
unzip -l <codegen-dex.jar>
```

应确认：

- payload 包含选中 base 及其匿名/局部结构成员，但不包含无关宿主业务依赖；
- `setFinishOnTouchOutside` 保持 invokevirtual，由 `PluginActivity` override；final API callsite 指向
  receiver-aware `ActivityApiBridge`；
- `report.json.inferredHostResources` 是真实宿主依赖集；
- JAR 不包含 `res/`、Manifest 或宿主持有的 X2C runtime class；
- DEX JAR 只有合法 `classes.dex`。

## 9. 通用 SDK 设计审查结论

本次修复采用构建期 contract、receiver-aware 字节码桥接和显式失败策略，没有引入业务类判断、运行时 XML、
全量 compileOnly 依赖复制或反射式 API 猜测。当前 Activity 层仍是受限 ABI，并非完整 Shadow 等价实现。

正式合并前仍建议重点审查三点：

1. `frameworkBase` 是否需要在未来增加 classpath 继承验证；
2. 将手写 Activity 代理面迁移为基于 android.jar 自动生成/差异审计的 Shadow 式 ABI；
3. `inferredHostResources` 是否进入版本化资源合同，并由宿主 CI 校验资源存在及 shrink keep。
