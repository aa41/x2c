# pluginMode 安全切换指南

`pluginMode` 只控制资源编译模型，不代表一个普通 AAR 可以在不调整依赖和 Manifest 的情况下直接变成动态插件。安全切换必须同时核对输出物、依赖方式、组件声明和宿主初始化。

## 30 秒安全选择

```text
需要服务端后发、DexClassLoader、无 res/resource table
  -> pluginMode=true -> x2c<Variant>DexJar

需要标准依赖、Manifest merge、原生 Resources/本地图片
  -> pluginMode=false -> assemble<Variant>/AAR
```

同一业务如果必须发行两种形态，优先使用两个 publishing module，共享源码而不共享 Manifest/依赖配置。
不要只改一个 boolean 后直接发布：这两种形态的运行时依赖、组件声明和图片策略也不同。

## 推荐：用一个严格的 Gradle Property 控制

插件默认读取 `x2c.pluginMode`。没有配置时默认为 `true`，非法值会在配置阶段直接失败，不会把拼写错误静默解释为 `false`。

```bash
# 动态、无资源的 DEX JAR
./gradlew :library:x2cReleaseDexJar -Px2c.pluginMode=true

# 普通 AAR
./gradlew :library:assembleRelease -Px2c.pluginMode=false
```

如果 `build.gradle(.kts)` 中显式调用了 `pluginMode.set(...)`，显式配置优先于命令行 convention。需要快速切换的 module 应删除硬编码，或统一写成：

```kotlin
val pluginBuild = providers.gradleProperty("x2c.pluginMode")
    .map { value ->
        when (value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> error("x2c.pluginMode must be true or false: $value")
        }
    }
    .orElse(true)

x2c {
    pluginMode.set(pluginBuild)
    generatedPackage.set("com.example.feature.generated")
    minApi.set(21)
}
```

不要在同一次 Gradle invocation 中用不同值构建同一 module。`pluginMode` 是 module 级配置，不是 variant/flavor 级配置。

短期本地切换可以使用 `-Px2c.pluginMode=true|false`；团队/CI 应把它写成显式 job 参数。不要靠修改
`build/generated`、删除 task output 或在用户级 `~/.gradle/gradle.properties` 隐式切换。若 module 中使用了
`pluginMode.set(...)`，该显式值优先于 property convention，命令行不会覆盖它。

## `pluginMode=true` 检查表

- 应用 `dev.x2c.codegen`；需要动态 Android 组件时同时应用 `dev.x2c.activity-plugin`。
- 业务 module 使用 `compileOnly` 编译 `x2c-runtime`、`x2c-plugin-api`、`x2c-plugin-runtime`、
  `x2c-plugin-base`；最终宿主唯一持有这些公共 class。
- `AndroidManifest.xml` 不声明插件 Activity、Service、Receiver 或 Provider。系统只认识 runtime AAR 预声明的非导出代理容器。
- 每个同时加载的 JAR 必须使用唯一 `x2c.pluginId` 和唯一 `x2c.generatedPackage`。
- 位图必须经过不可变 asset lock/CDN 流程；最终 `codegen.jar` 只能包含 class，DEX JAR 只能包含 `classes.dex`。
- 宿主只在 `Application` 初始化一次 `X2C.init(applicationContext)`，验签并创建 ClassLoader 后调用：

```java
PluginComponentRegistry registry =
        PluginComponentManager.loadAndInstall(appContext, pluginClassLoader);
```

- 不再调用 `R2.init()`，也不要从业务源码直接依赖 generated implementation。

推荐验证：

```bash
./gradlew :library:x2cReleaseJar :library:x2cReleaseDexJar
jar tf library/build/outputs/x2c/release/codegen.jar
unzip -l library/build/outputs/x2c/release/codegen-dex.jar
```

报告 `build/reports/x2c/release/report.json` 必须包含 `"mode": "PLUGIN_SYNTHETIC_IDS"`；转换报告必须列出 generated `ComponentRegistry` 和所有组件。

## `pluginMode=false` 检查表

- 输出物是 AAR，不是动态 DEX JAR；使用 `assembleRelease`/`bundleReleaseAar`。
- 原始 `res/`、位图和正常 Android Manifest 必须保留并合并到宿主。
- library 使用 `api` 或 `implementation` 依赖 `x2c-runtime`，由 application ClassLoader 正常加载。
- Activity、Service、Receiver、Provider 由 Android framework 按 Manifest 原生创建，不执行插件组件 superclass transform。
- `R2` 是无状态查询 facade，通过模块 Provider/宿主 `Resources` 解析最终资源 ID；没有 `R2.init()`。
- 不需要 asset lock，也不应运行 `x2c<Variant>Jar`/`x2c<Variant>DexJar`；两者都会明确失败，因为 normal class 不能脱离宿主 resource table。若误运行组件转换 task，也会报错 `Android component transformation requires x2c.pluginMode=true`。
- 若仍应用 `dev.x2c.activity-plugin`，配置阶段会失败。正常 AAR 必须移除该组件 transform 插件，让 framework 根据合并后的 Manifest 创建组件。

推荐验证：

```bash
./gradlew :library:assembleRelease -Px2c.pluginMode=false
unzip -l library/build/outputs/aar/library-release.aar
```

报告必须包含 `"mode": "HOST_RESOURCE_IDS"`，AAR 中应存在预期的 `res/layout`、`res/values`、drawable 和 Manifest 组件。

## 单 module 双模式时还必须切换的内容

仅适合确实需要从同一源码发布两种 artifact 的工程：

| 项目 | `true` | `false` |
|---|---|---|
| 发布物 | class-only JAR / DEX JAR | AAR |
| runtime/base 依赖 | `compileOnly`，宿主持有 | `api`/`implementation` |
| 业务组件 Manifest | 不声明 | 正常声明 |
| 组件 annotation/transform | 使用 | 不执行 transform |
| 位图 | asset lock + CDN | 保留在 AAR |
| 加载 | 验签、独立 ClassLoader、`PluginComponentManager` | application ClassLoader |

建议为两种模式使用独立 publishing module；共享业务源码可以放到普通 Java/source module。这样可避免条件 Manifest、条件依赖和错误发布物混用。仓库中的 `fixtures/producer` 与 `fixtures/normal-library` 就是两条独立、可同时验证的基线。

## 切换后的安全回归

```bash
./scripts/verify-plugin-modes.sh
./scripts/verify.sh
```

Gradle task 已把 `pluginMode` 声明为输入，正常切换不依赖手工删除 generated 目录。如果怀疑旧 IDE 模型或第三方缓存污染，只在诊断时执行一次 `./gradlew clean` 后重新 Sync；不要手工修改 `build/generated` 文件。

动态插件升级必须在新的应用进程中安装。当前 runtime 会拒绝重复 `pluginId` 和热卸载：系统仍可能持有
Activity token、Service bind/start transition、广播 pending result 或 Provider 调用，局部删除 registry
无法证明安全。生产更新流程应下载并验签新版本、记录待激活版本，然后在下次受控进程启动时安装。
