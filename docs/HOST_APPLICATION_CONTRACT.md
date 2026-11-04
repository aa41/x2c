# JAR 插件与宿主 Application 契约

JAR 插件没有独立 Application。插件 Context 的 `getApplicationContext()` 与转换后的 Activity/Service `getApplication()` 均返回 `X2C.hostApplication()`，即初始化时绑定的真实宿主 Application。普通集成仍采用 Android 的原生行为。

## 初始化

在真实 Application 的 `onCreate()` 中，或加固 SDK 明确通知宿主环境就绪后调用
`X2C.init(this, hostConfig)`，然后再安装插件。自动 Provider 不提前绑定 runtime。需要在
Application.onCreate 之前访问 X2C 的原生 Provider 必须由宿主另行安排真实 Application 就绪后的初始化时序。

```java
X2C.init(
        this,
        PluginHostConfig.builder()
                .defaultActivityTheme(R.style.HostPluginDefault)
                .activityTheme("commerce.detail.light.v1", R.style.HostPluginCommerceDetail)
                .activityTheme("commerce.dialog.v1", R.style.HostNativeTranslucentDialog)
                .activityTheme("video.home.dark.v1", "HostPluginVideoDark")
                .build());
```

配置构建后不可变。Activity payload 保存稳定 `themeKey` 与 `windowProfile`；安装时必须能从宿主表解析到
真实 style ID，且 Style 的窗口结构必须与 `OPAQUE`/`TRANSLUCENT` 声明一致。
直接 `R.style` 注册优先；字符串注册只允许宿主本地 style entry，并仅在首次初始化时调用一次
`Resources.getIdentifier(name, "style", hostPackage)`。名称解析的 style 在开启资源裁剪时需要静态引用或
`tools:keep`。宿主已有的原生 Style 可直接注册，无需修改 parent；也可以继承
`X2cPluginContainer.Opaque` 或 `X2cPluginContainer.Translucent`。缺少默认/keyed 主题、名称不存在、ID
不是 style、Profile 不匹配，或 floating/NoDisplay/ActionBar 结构都会 fail closed。插件 Manifest 中的
Activity theme 不参与协议。全部 qualifier 变体必须保持相同 Profile；style ID 只解析一次，runtime 会在
每次真正应用主题前按当前 Configuration 重新检查结构属性。

主题来源严格按组件创建模式隔离：normal AAR Activity 只使用合并后 Manifest 中自身的
`android:theme`，未声明时继承 application theme；即使源码保留 `@X2cPluginActivity`，其
`themeKey` 也不生效。动态插件 Activity 不读取插件 Manifest，只使用 `themeKey` 和上述宿主主题表。
同一宿主 APK 同时包含 normal AAR 与动态插件时，这两条规则仍互不覆盖。

重复传入同一 Application 和等值配置是幂等操作，不会重新查询资源；不同 Application 或不同配置会报错且
不会改变已发布的不可变主题表。normal AAR 模式不需要 `PluginHostConfig`，继续单独调用 `X2C.init(...)`。

代理加载器可使用 `X2C.init(realApplication, stableHostLoader, hostConfig)`。加载器必须能够解析同一份 X2C 共享类型；重复绑定同一 Application/加载器允许，不同对象则报错且不改变已有状态。这里不 Hook SDK，也不会自动检测 SDK 的加固完成时间。初始化后宿主必须保持应用环境稳定，各进程分别初始化。

## 代码身份

`X2C.isPlugin(MyBusinessClass.class)` 按类的定义加载器识别代码归属，不能表示“当前调用者是谁”。插件调用宿主门面时，宿主门面类仍属于宿主。

内置 PluginClassLoader 在构造时登记。组件安装入口校验 registry 的定义加载器和 ABI 后也会登记。仅加载资源模块的第三方加载器必须通过 `X2C.registerPluginClassLoader(definingLoader)` 显式登记真实定义加载器，并保证共享 runtime 来自宿主。宿主业务类则通过宿主加载器能否解析出同一个 Class 来识别，允许代理加载器对象不同。未登记、且不能由宿主解析为同一 Class 的代码会报错。已登记插件缺失 bootstrap 不会回退为系统资源。

## 路由与 SDK

- `pluginActivity.startActivity(...)`、插件 Activity/Service Context 上的组件调用继续保留已有路由。
- `getApplicationContext().startActivity/startService/sendBroadcast(...)` 是宿主 Android 调用，不携带插件身份。插件目标必须使用公共 PluginActivityManager、PluginServiceManager、PluginReceiverManager 等明确的插件路由 API。
- 资源仍通过 `X2C.resources(context, MyBusinessClass.class)` 获取。宿主 Application 不能用于推断资源属于哪个插件。
- 需要 Application 的 SDK 可接收 `X2C.hostApplication()`。需要系统真实 Activity 的 SDK，应在插件场景接收 `PluginActivity.getContainerActivity()`。直接注册在宿主 Application 上的系统生命周期回调仍观察容器；需要业务 Activity 身份的回调使用下面的显式桥接入口。

## 统一 Activity 生命周期注册入口

普通模式与插件模式使用相同入口，不需要在业务代码中声明 module name 或 pluginId：

```java
// callbacks 是 Application.ActivityLifecycleCallbacks，同一实例用于注册与取消注册。
// 在 X2C.init(...) 完成后注册。插件类作为 owner 时仅观察其定义加载器的 Activity；
// 同一类在 normal AAR 中由宿主持有时，采用全局观察范围。
X2C.registerActivityLifecycleCallbacks(MyBusinessActivity.class, callbacks);
X2C.unregisterActivityLifecycleCallbacks(MyBusinessActivity.class, callbacks);

// 宿主诊断/导航层可显式观察全部正常 Activity 和全部插件的业务 Activity。
X2C.registerActivityLifecycleCallbacks(callbacks);
X2C.unregisterActivityLifecycleCallbacks(callbacks);
```

底层统一入口是 `X2cActivityLifecycle.registerActivityLifecycleCallbacks(...)` 与
`unregisterActivityLifecycleCallbacks(...)`，与 `X2C` 便利方法使用同一个进程级 registry。

- 普通 Activity 原样投递；插件容器通过共享 runtime 的 `ActivitySource` 契约提供绑定的业务
  delegate，同一次实例的全部回调均投递这个对象。桥接不反射、不 Hook framework、不复制 Application。
- API 21–28 转发七项标准回调；API 29+ 同时转发十四项 pre/post 回调。容器原生
  `PreCreated` 发生时 delegate 尚未创建，因此不透传；在 delegate 完成 BaseContext 绑定后、业务
  `onCreate` 前补发插件 `PreCreated`，不会在旧 Android 上人为制造不存在的回调。
- 有 owner 的插件范围按定义 ClassLoader 隔离，不按 Context 推测调用者；未登记的未知加载器明确
  报错。两个插件若刻意共享同一加载器，则共享观察范围。宿主 owner（包括插件调用的宿主门面类）
  选择全局范围，不能把门面类误当成插件 owner。
- 同一 callback 对象、同一范围的重复注册幂等；不同范围独立管理。取消注册必须匹配原范围；
  未注册对象的取消为无操作。桥接仅取消自己注册的 wrapper，不会取消同对象直接注册的原生回调。
- 注册按对象 identity 而非 `equals/hashCode` 管理，并保留注册顺序。回调中允许注册/取消；当前
  通知使用快照，新注册者不会补收当前通知。取消会阻止后续转发，但不终止已经进入执行的回调。
- 桥接不静态持有 Activity；callback 和定义加载器会像原生注册一样被持有到取消注册。与 Activity
  生命周期绑定的监听器应在 `onDestroy` 等明确位置取消。监听器异常按原生语义向外抛出，不吞掉失败。
- 若宿主覆盖的原生取消方法抛错，wrapper 立即停止转发并保留可重试状态；再次注册会明确提示先
  重试取消，避免把停用状态误报为注册成功。

这个入口只统一**回调接收者身份**，不会把 delegate 变成 framework attach 过的 Activity。
宿主中未转换的 SDK 如果在桥接回调中调用 delegate 的 final `Activity.getApplication()`，仍可能
读到未初始化的 framework 字段。仅需 Application 时使用 `getApplicationContext()` 或
`X2C.hostApplication()`；依赖真实 Activity 的 SDK 继续接收容器。此能力也不代表已支持
AndroidX/FragmentActivity、FragmentManager 或任意 SDK 的透明兼容。
宿主那份 `BusinessBaseActivity` 与复制进插件并转换的私有 BaseActivity 也不是同一个 Class；
桥接不保证这类跨加载器业务类强转，需使用共享接口或显式适配。

回归入口：

```bash
# JVM 协议回归，模拟 API 21/28/29/35（不等价于这些版本的真机认证）。
./gradlew :x2c-runtime:check :x2c-plugin-runtime:check --offline

```

生命周期协议回归覆盖普通 Activity 原对象、插件稳定 delegate、宿主原生回调不变、定义加载器
范围、重复注册、取消注册及 API 29+ pre/post 转发。Android doubles 不进入正式 runtime JAR
或插件 payload；这些模拟测试不替代 framework 真机时序验证。独立仪器测试 Runner 与脚本已移除，
生产代码不修改或替换 Instrumentation。

## 版本兼容

Application/ClassLoader 身份契约引入时组件 runtime ABI 提升为 2。receiver-aware final Activity API bridge
和 `setFinishOnTouchOutside` 容器转发曾把协议提升为 3；宿主主题 key/metadata 契约进一步将当前 ABI
提升为 4；双 Window Profile 容器与 metadata 契约将当前 ABI 提升为 5。ABI 1/2/3/4 载荷会被
现有安装检查拒绝，需使用新编译插件重新构建，不应跳过 ABI 检查。仍需针对具体加固产品验证 DEX 加载、
组件、资源重命名等策略，普通设备回归不是厂商兼容认证。
