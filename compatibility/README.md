# AGP compatibility fixture

`fixture` 是独立的 Groovy Android library，用同一份源码验证不同 AGP/Gradle/JDK 组合。它不加入
仓库根工程，避免旧 AGP 污染日常 Gradle sync。

验证项：

- `build/generated/java/x2cGenerateRelease` 被创建，旧 `generated/sources/x2c` 不存在；
- `compileReleaseJavaWithJavac.source` 包含 X2C generated Java 目录；
- AGP 8.11 的 Android Studio v2 Tooling Model 中 `generatedSourceFolders` 包含该目录；
- 普通 Java 源码只引用 `x2c-runtime`，通过 `X2C.loadModule/X2cResources` 使用生成实现；
- plugin 模式输出完整 synthetic R2；normal 模式输出无状态 R2 兼容方法，并由 generated Provider
  唯一调用和缓存宿主 `Resources#getIdentifier`；
- 生成的 `X2cModule` 与 `X2cResourceProviderImpl` 编译并依赖宿主提供的 Java 8 `x2c-runtime` JAR；
- plugin 模式的 `codegen.jar` 只包含 class，且过滤旧 AGP 编译进 Javac 目录的 Android `R.class`。
- Android 组件插件阶段在 plugin 模式矩阵中生成 `activity-plugin.jar`、直接构造 registry 和 DEX JAR，
  并在每个 AGP 版本中从 `compileOnly project` 提取标注的 resource-free BusinessBase class 闭包，验证
  Activity 业务父类保留、依赖链底从 framework `Activity` 改写为宿主 `PluginActivity`。
- normal 模式不应用组件 transform，也不生成 class-only/DEX JAR；它使用独立 Manifest 和 runtime
  依赖，验证标准 AAR 保留 layout/values 与原生 Activity 声明。
- 当 Android SDK 同时安装多个 Build Tools 时，DEX 任务按 `d8.jar` classfile 版本选择当前 JDK 可运行的
  最新版本；这覆盖了 JDK 8 工程与新版 Build Tools 并存的常见开发机配置。

运行完整代表版本矩阵：

```bash
./scripts/verify-agp-compatibility.sh
```

离线运行会把现有 Gradle module cache 以符号链接映射到临时 Maven layout；该目录位于根
`build/compat-maven-cache`，不会进入版本控制：

```bash
X2C_OFFLINE=true ./scripts/verify-agp-compatibility.sh
```

可通过 `X2C_JAVA8_HOME`、`X2C_JAVA11_HOME`、`X2C_JAVA17_HOME` 指定对应 JDK。

当前固化的代表版本是 AGP 3.5.4、4.1.3、7.3.1、7.4.2，以及
8.0.1/8.1.0/8.3.1/8.5.2/8.7.3/8.9.1/8.11.1/8.12.3/8.13.1。脚本按版本使用
Gradle 5.4.1、6.5、7.5、8.2.1、8.4、8.7、8.9、8.12、8.13 和对应 JDK。
矩阵另外在 AGP 8.11.1 上执行一次 `-Px2cPluginMode=false`，验证真实 Android JavaCompile 接受宿主
resource ID 模式，而不是只检查生成源码文本。
