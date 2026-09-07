# Android 插件组件协议

## 设计结论

当前实现采用与 Tencent Shadow 相同的关键边界：不 Hook framework、不调用 hidden API，也不尝试把后装 JAR 伪装成 PackageManager 已安装组件。构建期改写 framework 继承锚点和已知调用点；运行时由宿主 Manifest 中预声明的非导出容器接收系统事件；generated `ComponentRegistry` 使用直接 `new` 构造业务组件。

准确的反射承诺是：每个 ClassLoader 安装时对固定 registry 类执行一次 bootstrap reflection；Activity、Service、Receiver、Provider 的逐实例创建和生命周期热路径不使用反射。Android framework 自身的内部实现不属于该承诺。

```text
业务组件 class
  -> 保留 payload 自定义基类、复制标注 compileOnly base，或连接宿主持有的 x2c-plugin-base
  -> 替换最底层 framework Activity/Service/Receiver/Provider
  -> 改写 final API 与 ContentResolver URI 调用
  -> generated ComponentRegistry（direct new）
  -> DEX JAR

系统事件
  -> 宿主非导出容器
  -> (pluginId, component class / authority) 严格校验
  -> plugin delegate
```

入口组件必须是 `public`、非抽象并具有 `public` 无参构造。支持三类继承闭包：自定义基类位于当前 payload；
Activity base 位于 `compileOnly project` 且以 `@X2cPluginBase` 选择性复制；或直接/间接连接
`x2c-plugin-base` 中四个宿主持有的批准根。未标注、仅宿主持有的普通组件基类仍在构建期失败，插件若
误打包 API/base/runtime class 也会失败。

## 公共基类与宿主共用边界

`x2c-plugin-base` 提供 `BasePluginActivity`、`BasePluginService`、`BasePluginReceiver`、
`BasePluginProvider`。宿主使用 `implementation` 持有唯一 class，插件使用 `compileOnly` 编译：

```kotlin
// host
implementation(project(":x2c-plugin-base"))

// plugin payload
compileOnly(project(":x2c-plugin-base"))
```

这四个类是插件 delegate 的公共根，不是普通宿主组件根。`BasePluginActivity` 继承
`PluginActivity`，依赖容器在构造后完成 attach；让 Manifest 中的原生宿主 Activity 也继承它会绕过正常
framework attach/lifecycle，属于不支持用法。因此不能让同一个具体 Activity base 同时充当“原生宿主
Activity”和“插件 delegate Activity”。需要两端复用的登录、埋点、状态机、Presenter/ViewModel 等逻辑应放在
不依赖 Activity 身份的 controller/helper/interface 中，原生 base 与插件 base 分别做薄适配。

这不代表插件业务必须在源码中直接继承 `BasePluginActivity`。当前转换器也支持：

```text
PluginPage -> payload 内 BusinessBaseActivity -> android.app.Activity
                                             ↓ transform
PluginPage -> payload 内 BusinessBaseActivity -> PluginActivity
```

`BusinessBaseActivity` 的构造、字段初始化和所有 `super` 生命周期代码都会保留。真正不支持的是让插件继承
一个只存在于宿主 ClassLoader、仍以 `android.app.Activity` 为根的 `HostBaseActivity`：JVM 单继承下它既不能
同时成为 `PluginActivity`，宿主已加载的 class 也不能在另一个 ClassLoader 中原地修改父类。

Shadow 同样没有让插件复用宿主中那个未改写的 BaseActivity class。它会对完整插件 transform 输入做类型替换；
插件 ClassLoader 得到的是 `Activity -> ShadowActivity` 后的独立副本。X2C 使用
`@X2cPluginBase + compileOnly(project(...))` 达到相同结果：从 compile classpath 复制实际被继承的 base、
Activity 父类链与结构性 nest/内部类，并统一改写为 `BusinessBaseActivity -> PluginActivity`。宿主仍
`implementation` 原 class。两端复用源码和 API，但不共享 class identity、static state 或 Activity 实例。
未标注的普通 base 不能通过 ClassLoader fallback 充当插件父类。

这里不会再把 BaseActivity 的所有字段类型、生命周期埋点、controller 或 SDK 自动复制进插件。必须同
ClassLoader 的插件私有实现通过 `@X2cPluginBase(include = {...})` 明确声明；analytics、账户、网络、路由等
未进入 payload 的能力由 PluginClassLoader 在插件查找失败后直接委托宿主。无需业务注解或白名单；宿主需
保证相关 class/member 存在，并为后发插件调用配置 shrink keep 与版本兼容策略。

第一阶段 ownership 规则是：平台与 X2C runtime/API/base/loader 为 parent-first 且禁止打包；business
base transform unit 和显式 include 为 plugin-private；其余未打包业务引用采用 host fallback。相关
`R`/TypedArray/resource-backed API、JNI、重复 class 和 AndroidX/AppCompat/Material 均
构建期失败。依赖 class digest 写入 transform 报告，签名 payload digest 覆盖合并转换后的全部 class。

公共 base 本身由宿主预编译，转换器不会改写其中的字节码。因此公共 base 的实现不得直接调用需要 ASM bridge
的 framework final API（例如 `Activity.getApplication()` / `setResult()`）；请调用公开的 plugin bridge，或把
这类调用留在 payload 内的业务类/同 JAR base。`PluginClassLoader` 对 `dev.x2c.plugin.base.*` 强制
parent-first，并且打包器拒绝 payload 内同名 class，防止类型身份分裂和伪造安全根。

## 统一安装

宿主验签并创建隔离 ClassLoader 后，在主线程调用：

```java
PluginComponentRegistry registry =
        PluginComponentManager.loadAndInstall(appContext, pluginClassLoader);
```

`PluginComponentManager` 会原子安装 Activity、Service、Receiver、Provider registry。Provider 在安装阶段立即执行一次 `attachInfo/onCreate`；任一 metadata、ClassLoader、authority 或构造结果不匹配都会回滚已完成的安装步骤。同一 `pluginId` 在一个进程内只能安装一次，重复安装会在修改 registry 前失败。

每个插件必须使用唯一 `pluginId`。Provider authority 还必须在当前进程所有已安装插件间全局唯一。

当前明确不支持热卸载/同进程替换。runtime 无法证明所有 framework token、Service transition、
Receiver `PendingResult` 和 Provider 调用已经结束，因此 `PluginComponentManager.uninstall()` 会 fail closed。
升级插件必须创建新的应用进程，不能逐项移除 registry 后复用旧 ClassLoader。

## 支持矩阵

### Activity

Activity 的继承链、生命周期、Window/content、同插件/跨插件跳转、Result 和四种 launchMode 详见 [Activity 插件设计](ACTIVITY_PLUGIN.md)。宿主预留每种 launchMode 八个容器。

### Service

已支持的同进程普通 Service：

- `startService`、`stopService`、`stopSelf/stopSelfResult`；
- `bindService/unbindService`、`onBind/onUnbind/onRebind`，bind flags 由真实 framework Service 处理；
- `onCreate/onStartCommand/onTaskRemoved/onDestroy` 与 configuration/memory callbacks；
- ServiceConnection 收到虚拟插件 ComponentName，而不是容器类名；
- 同 JAR 多层自定义 BaseService 和 `attachBaseContext`，并检查必须调用 `super`；
- 八个进程级 Service slot，固定 target 不会在运行中被另一个插件复用。
- 停止一个从未启动或绑定的插件 Service 直接返回 `false`，不会为了 stop 操作占用 slot；

安全限制：

- 只接受 `START_NOT_STICKY`。`START_STICKY`/`START_REDELIVER_INTENT` 需要进程死亡后的插件重装、route 持久化和 startId 恢复，当前会明确失败；
- Android 14+ 使用 foreground Service 前，宿主必须为实际 slot 声明所需 `foregroundServiceType` 和权限；runtime 不会预声明所有敏感类型；
- `JobService`、`IntentService`、`AccessibilityService`、`VpnService`、`NotificationListenerService`、`FirebaseMessagingService` 等 framework/SDK 驱动的特殊 Service 不属于普通代理协议；继承闭包无法安全转换时构建失败；
- `process`、`isolatedProcess`、permission 和系统 binding 身份只能来自宿主容器，不能由动态 JAR 新增。

### BroadcastReceiver

已支持：

- 指向已安装插件 Receiver 的显式广播；
- 普通和 ordered broadcast、result code/data/extras、abort/clear 和 `goAsync()`；
- 每次广播由 generated registry 直接创建新实例；
- 插件 Context 提供插件 ClassLoader，并能继续路由同插件显式组件；
- 宿主代理 Receiver 固定 `exported=false`，路由 extras 不能由其他应用注入。

当前不宣称支持：动态 JAR 在未加载时被系统 action 冷启动、运行时新增 manifest intent-filter、exported Receiver、跨应用 permission/ordered broadcast 等价性，以及 sticky broadcast 初始化语义。需要这些能力时，应由宿主显式声明公开入口并完成独立权限审计。

### ContentProvider

插件 Provider 使用虚拟 authority。所有安装插件共享一个宿主非导出 authority：

```text
content://<applicationId>.x2c.plugin.provider/<pluginId>/<virtualAuthority>/...
```

插件业务仍可使用原始 URI。编译器把支持的 `ContentResolver` 调用改写到 `PluginContentResolver`，运行时只在 authority 属于已安装插件时转换，其余 URI 原样调用系统 resolver。

已覆盖：

- legacy 与 Bundle query/insert/update/delete、bulkInsert、getType/getStreamTypes；
- `call`、`applyBatch`、canonicalize/uncanonicalize、refresh；
- content observer 注册/通知；
- input/output stream、file/asset/typed asset descriptor、thumbnail 与 persistable URI 方法；
- configuration/low-memory/trim-memory 回调；
- Provider Context 的插件 ClassLoader，以及返回 URI 恢复为业务 authority。

API 26/29/30 引入的 resolver/provider overload 和 API 24/29 引入的 Service overload 保留原生 Android
API 等级要求；runtime 对 `startForegroundService` 使用兼容回退，其余新 API 会先检查 `SDK_INT` 并以明确的
`UnsupportedOperationException` fail closed。业务仍应像调用原生 API 一样在调用点执行版本门禁。

安全限制：

- 宿主 Provider 永远 `exported=false`、`grantUriPermissions=false`、同进程；不提供跨应用访问、URI grant、真实 permission/pathPermission 或独立 process 身份；
- `ContentProviderClient`/unstable client 无法安全替换为虚拟 client，调用会在构建期失败；
- `getCallingPackage`、AttributionSource 和 calling-identity API 依赖 framework transport，当前在插件 Provider 中使用会构建失败；
- FileProvider 的 XML paths、DocumentsProvider、Settings/Search suggestion 等系统专用 Provider 需要独立宿主协议，不属于通用 Provider；
- 宿主代码若要访问虚拟 authority，需要先调用 `PluginProviderManager.route(originalUri)`；宿主本身不经过插件字节码转换。

## 与 Shadow 的对应关系

- Shadow `PluginServiceManager` 自己维护 Service 实例、start/bind 状态；当前实现让真实的八个宿主 Service slot 承担 framework startId、bind flags 和 Binder 生命周期，并只在安全子集上转发业务 delegate。
- Shadow `PluginContentProviderManager` 维护 plugin/container authority 映射，并由 `ContentProviderTransform` 改写 URI/ContentResolver；当前实现采用相同思路，但使用 ASM 和 generated registry，且固定为非导出单一容器 authority。
- Shadow 的 `ShadowService` 默认 `START_NOT_STICKY`，foreground 能力也有明确 TODO；当前协议同样不会把未完成语义包装成“完全兼容”。

参考的 Shadow 固定源码版本：

- [PluginServiceManager](https://github.com/Tencent/Shadow/blob/4e9f70a660cc0e55e0576650258e732382ca9031/projects/sdk/core/loader/src/main/kotlin/com/tencent/shadow/core/loader/managers/PluginServiceManager.kt)
- [ShadowService](https://github.com/Tencent/Shadow/blob/4e9f70a660cc0e55e0576650258e732382ca9031/projects/sdk/core/runtime/src/main/java/com/tencent/shadow/core/runtime/ShadowService.java)
- [PluginContentProviderManager](https://github.com/Tencent/Shadow/blob/4e9f70a660cc0e55e0576650258e732382ca9031/projects/sdk/core/loader/src/main/kotlin/com/tencent/shadow/core/loader/managers/PluginContentProviderManager.kt)
- [ContentProviderTransform](https://github.com/Tencent/Shadow/blob/4e9f70a660cc0e55e0576650258e732382ca9031/projects/sdk/core/transform/src/main/kotlin/com/tencent/shadow/core/transform/specific/ContentProviderTransform.kt)

## 验证

- compiler self-test 验证四类 superclass 替换、Provider resolver 改写、generated factory、deterministic JAR、slot 上限和不安全 API fail-closed；
- `scripts/verify.sh` 检查 class-only/Dex-only 输出、组件报告、宿主隔离和 Manifest 容器；
- `scripts/verify-plugin-showcases.sh` 在真机自动执行 Layout/图片 Demo，以及四种 launchMode、Activity Result、Service started/bound、normal/ordered/goAsync Receiver、Provider CRUD/call/batch/observer/file 和跨 ClassLoader 导航；
- `scripts/verify-plugin-modes.sh` 同时验证动态 JAR 和普通 AAR 两种资源模式。
