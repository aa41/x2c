# Activity 动态插件设计

## 结论与边界

本文只描述 Activity 子协议。Service、BroadcastReceiver、ContentProvider 的代理、安全边界和验证见
[Android 插件组件协议](PLUGIN_COMPONENTS.md)。插件的 library Manifest 必须保持无业务组件声明，宿主只合并
runtime 预声明的固定非导出代理容器。

插件开发代码仍使用正常的 framework 类型：

```java
@X2cPluginActivity(launchMode = PluginLaunchMode.SINGLE_TOP)
public final class DetailActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        X2C.resources(DetailActivity.class).setContentView(this, "detail");
    }
}
```

构建期解析业务 module 及其 plugin-private runtime 依赖闭包中的完整 Activity 继承链，只把直接连接 framework `Activity` 的链底
改写为宿主中间层，并同步改写必要 callsite；业务源码不需要继承专用插件基类：

```text
res XML/values/drawable
  -> X2C generated Java -> JavaCompile -> codegen.jar
  -> Activity superclass/final-API callsite transform
  -> activity-plugin.jar + activities.json
  -> D8 -> codegen-dex.jar
```

带 `@X2cPluginActivity` 的入口必须是 public concrete class，并具有 public 无参构造函数。入口可以直接
继承 `android.app.Activity`，也可以经过 module 自身的一层或多层纯 class 自定义基类；同 module 的中间
`BaseActivity` 不需要注解，也不会进入 registry 或占用 launchMode slot。例如：

```text
DemoActivity -> BaseActivity -> android.app.Activity
                        transform
DemoActivity -> BaseActivity -> PluginActivity
```

另一个安全入口是宿主实际持有、插件 `compileOnly` 的公共插件根：

```text
PluginDetailActivity -> dev.x2c.plugin.base.BasePluginActivity -> PluginActivity
```

此边界无需复制或改写公共 base，并由 parent-first ClassLoader 保持唯一类型身份。它不能作为 Manifest 原生
宿主 Activity 的父类；公共 base 已预编译，其内部也不能调用依赖 transform 的 final Activity API。

需要宿主和插件共享正常 `BaseActivity extends Activity` 源码时，在业务 project 的根上标记：

```java
@X2cPluginBase(include = {PluginPrivateDependency.class})
public abstract class BusinessBaseActivity extends Activity { /* ... */ }
```

插件使用 `compileOnly(project(":business-base"))`，宿主使用 `implementation`。构建器只复制被插件入口
真实继承的标注 base 链、nest/内部实现类以及 `include` 明确声明的插件私有闭包。普通 compileOnly 引用
不会复制进 payload；插件 ClassLoader 查找不到时会自动交给宿主 ClassLoader。仅宿主
BaseActivity、AppCompat/FragmentActivity/ComponentActivity 仍会在 Activity 继承闭包
无法闭合时构建失败。遗漏入口注解、单
launchMode 超过 8 个、未知组件或不安全 final API 同样 fail closed。

marker 来自 `x2c-plugin-api`，业务 base project 对它使用 `compileOnly`，不会把 API 打入宿主或插件副本。

第一阶段依赖闭包接受传递 JAR、classes 目录和无资源 Android library classes。Android resources、Java
resources、assets、JNI、重复 class、X2C 宿主包以及 AndroidX/AppCompat/Material 会在构建期拒绝。转换报告
记录每个插件私有依赖的 class 数量、canonical class SHA-256 和 compileOnly host-fallback 策略；
最终签名 payload digest 覆盖 module、依赖和 generated registry。

因此这里有两种不同的“共用 BaseActivity”语义：

| 方案 | 当前支持 | 运行时身份 |
|---|---|---|
| 多个插件继承宿主持有的 `BasePluginActivity` | 是 | 全进程唯一、parent-first，但它必须以 `PluginActivity` 为根 |
| 插件 `compileOnly` 标注的 `BusinessBaseActivity` | 是 | 选择性复制为 plugin-private 副本，链底改为 `PluginActivity` |
| 插件 `compileOnly` 继承未标注的宿主 `HostBaseActivity` | 否，构建期报错 | 未转换宿主 class 无法充当 delegate 根 |
| 宿主和插件复用同一 base library，各自编译/转换 | 是（第一阶段纯 class） | 同 FQCN、不同 ClassLoader/identity/static state |

Shadow 属于最后一种思路：它转换完整插件输入闭包中的 BaseActivity，不是直接继承宿主进程已经加载的原始
BaseActivity。Shadow 的插件基类代码和生命周期可以执行，是因为该基类副本本身也参与了 transform；这里的
`@X2cPluginBase + compileOnly project` 实现相同的 ClassLoader 结果，但复制范围被注解和依赖闭包约束。

## 模块职责

| Module | 归属 | 职责 |
|---|---|---|
| `x2c-plugin-api` | 宿主 + plugin/base project compileOnly | 四类组件 annotation、Base marker、launchMode、metadata/registry contract |
| `x2c-plugin-base` | 宿主 implementation + plugin compileOnly | 四类宿主持有的插件专用公共 delegate 根 |
| `x2c-plugin-loader` | 宿主 | descriptor、RSA 签名、SHA-256、防降级、私有只读安装、ClassLoader |
| `x2c-plugin-runtime` | 宿主 AAR | 四类组件中间层、真实代理容器、生命周期、Intent/URI 路由 |
| `activity-compiler` | 构建机 | ASM 组件父类/callsite 转换、direct-constructor registry 生成 |
| `activity-gradle-plugin` | 构建机 | 把组件 transform 插入 `codegen.jar` 与 D8 之间；配置统一读取 `x2c {}` |
| `x2c-runtime` | 宿主 | X2C module/resource registry、图片 SPI、plugin-first/host-fallback ClassLoader |

插件 payload 不得打入 `x2c-runtime`、`x2c-plugin-api/base/runtime/loader`。`PluginClassLoader` 对这些包和
Android/Java 平台包强制 parent-first，避免 singleton 与类型身份分裂；其余业务类 plugin-first，插件中
不存在时自然回退宿主。因此宿主业务能力不需要额外注解，但如果同名类也被显式打进插件，则插件副本优先。

宿主在 `Application.attachBaseContext` 中调用且只调用一次 `X2C.init(base)`；若不需要在该阶段安装插件，
也可放到 `Application.onCreate`。插件安装器、generated `X2cModule`、Activity 和资源访问只验证宿主已经
初始化，不再重复绑定 Context/ClassLoader。每个插件 module 的 Provider/Layout registry 在首次 load 时
初始化一次，并缓存业务 anchor class 到 module 的映射；后续 `X2C.resources(Activity.class)` 不再执行
bootstrap 反射。

## Activity 创建与反射边界

每个独立 DEX JAR 都包含相同类名 `dev.x2c.generated.plugin.ComponentRegistry`，但它们位于不同
`PluginClassLoader`，所以可以并存。宿主安装时对这个固定类名执行一次 constructor reflection；registry
内部是 generated switch 和直接 `new DetailActivity()`，Activity 路由和逐实例创建均不使用反射。

因此准确承诺是“一次固定 registry bootstrap 反射，零逐 Activity 反射”，不是 JVM/ART 意义上的绝对
零反射。动态获得一个服务端 DEX 中事先未知的首个类型，本身必须存在类名查找或等价 VM 链接步骤。

## 代理池与 launchMode

宿主 Manifest 声明 32 个不可导出的真实 Activity：

| launchMode | 容器数 |
|---|---:|
| `standard` | 8 |
| `singleTop` | 8 |
| `singleTask` | 8 |
| `singleInstance` | 8 |

slot 对 `pluginId/activityClass` 稳定分配。容量是全进程每种 8 个，不是每插件各 8 个；同一 mode 下第 9 个
不同目标会明确失败。`standard` 的同一代理 class 仍可由 Android 建立多个实例；其他三种模式依靠不同代理
class 隔离系统 task 语义。当前不支持热卸载；slot 随应用进程结束统一释放，升级必须进入新进程。

## 当前 Activity API

已转发生命周期/回调：`onCreate/start/restart/postCreate/resume/postResume/pause/stop/destroy`、
`onNewIntent`、save/restore state、activity result、permission result、configuration/memory、content changed、
window focus、user interaction/leave hint。

已代理的主要能力：

- Window、theme/resources、LayoutInflater、set/add content、find/require View、current focus；
- Intent/component、title、Application、task/orientation、finish/recreate/back；
- plugin 内/外 `startActivity` 和 `startActivityForResult`；
- result、permission、UI thread、volume/media controller、window feature/progress/feature drawable final API。

fixture 组件矩阵实际覆盖：宿主→插件、同插件普通显式 `Intent`、插件→插件 Result、跨两个
`PluginClassLoader`、插件→宿主，以及 standard/singleTop/singleTask/singleInstance 与 `onNewIntent`。跨插件时使用
`PluginActivityManager.startActivity(...)` 明确给出目标 pluginId；同插件显式 Intent 会由 delegate
自动识别并路由。`scripts/verify-plugin-showcases.sh` 可在连接设备上从冷启动自动执行完整链路。

Java 不能 override 的 final Activity API 由 transformer 改写到 `PluginActivity` bridge；helper class 以具体
plugin Activity 类型调用时也会改写。已知无法保持语义的 deprecated managed-dialog/keyboard/splash 等 final
API 会构建失败。当前没有转发 options/context menu、FragmentManager/LoaderManager、PiP、voice interaction、
Activity embedding 和任意 Window callback；这些能力在加入专门协议和测试之前不属于支持范围。

Activity 构造函数/字段初始化阶段不得访问 Context、Window 或 Resources；delegate 在构造完成后才 attach
真实容器。attach 会在 runtime 状态注入完成后通过虚调用进入业务/BaseActivity 的
`attachBaseContext`，并要求最终调用 `super`。生命周期由容器转发给业务对象，业务继承链最终调用
`PluginActivity.onXxx` 时再回桥真实容器的 `Activity.super.onXxx`，因此 `super` 的时点和普通 Android
一致。推荐所有 Android 初始化放入 `onCreate`。

## 服务端 DEX 安装协议

服务端返回三个对象：`payload.dex.jar`、固定七行 UTF-8 descriptor、RSA signature。descriptor 是：

```text
x2c-plugin-v2
<pluginId>
<positive versionCode>
<versionName>
<positive component runtime ABI version>
<lowercase sha256 of canonical packaged dependency classes>
<lowercase sha256 of payload>
```

签名算法当前为 `SHA256withRSA`，签名输入是 descriptor 的原始 canonical bytes。宿主固定 publisher public
key，先验签 metadata，并在落盘前校验 host/plugin component runtime ABI。descriptor 不维护宿主业务 API
白名单；宿主依赖按正常 ClassLoader 链在首次使用时解析。dependency closure digest 用于服务端目录和构建
报告审计，最终 payload digest 同时绑定 module、依赖、transform 和 registry。通过后再把
payload 流式写入 app 私有目录并校验 SHA-256。动态代码临时文件会在写入任何
内容前设为只读，以满足 Android 14 动态代码加载要求；安装器持久化每个 pluginId 的最高 versionCode，
拒绝降级及“相同 versionCode、不同摘要”。生产还应补充证书轮换、吊销、灰度、崩溃回滚索引、磁盘配额、
下载超时和服务端审计，但回滚不能绕过签名与防降级策略。

fixture 内的 private key 只用于可重复测试，不能复制到生产；生产私钥必须留在隔离签名服务/HSM。

## Google Play 风险

如果可执行 DEX/JAR 由自有服务端在安装后下发，这不是一个可以承诺通过 Google Play 审核的方案。Google
Play 的 Device and Network Abuse 政策通常禁止应用从 Google Play 之外下载可执行代码并用于自我更新；
即使代码具备 RSA 签名、只修改 UI、没有 native code，也不会自动获得豁免。技术安全校验解决的是供应链
完整性，不解决商店合规性。

面向 Google Play 的发行版应关闭服务端 DEX，改用 Play Feature Delivery 或随 APK/AAB 发布的代码；服务端
只下发数据、配置和图片。若业务必须后发 DEX，应按目标地区和分发渠道单独评估，通常需要非 Play 渠道，
不能把 Play Asset Delivery 视为任意远程代码更新通道。

参考：

- [Google Play Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/9888379)
- [Android dynamic code loading risks](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading)
- [Play Feature Delivery](https://developer.android.com/guide/playcore/feature-delivery)

## 已有验证

- ASM 自测：三层 BaseActivity 继承链只改最底 framework 锚点、标注 compileOnly base/helper/include 精确闭包、
  非 Activity 标注根和未标注外部基类拒绝、基类 final API bridge、
  direct Activity 与 lifecycle super call、跨 helper final API callsite、annotation/launchMode、direct registry、
  deterministic JAR、遗漏 annotation、无法桥接的 final API 和 8-slot 上限 fail-closed；
- runtime 单测：四种 mode 独立分配、同 key 稳定、全进程每 mode 8 个、uninstall 回收；
- loader 自测：descriptor round-trip、RSA 验签、metadata 篡改拒绝；
- fixture：两个物理 DEX JAR、两个 ClassLoader、两个 registry、Layout/资源/图片页和四大组件页、32 个 Manifest 容器；
- ADB 导航测试：宿主/同插件/跨插件/宿主回跳、plugin Activity Result、四种 launchMode/new-intent；Layout 页
  同时验证公共插件 base 与业务类由不同 ClassLoader 持有；
- `scripts/verify.sh`：JAR/DEX/Manifest/签名 envelope/宿主隔离/确定性检查；
- `scripts/verify-agp-compatibility.sh`：Activity Gradle 插件与原资源插件共用 AGP 3.5.x–8.x 代表版本矩阵，
  并验证 JDK 8/11/17 下 D8 自动选择与 Android Studio generated-source model。
