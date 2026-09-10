# JAR 插件与宿主 Application 契约

JAR 插件没有独立 Application。插件 Context 的 `getApplicationContext()` 与转换后的 Activity/Service `getApplication()` 均返回 `X2C.hostApplication()`，即初始化时绑定的真实宿主 Application。普通集成仍采用 Android 的原生行为。

## 初始化

在真实 Application 的 `onCreate()` 中，或加固 SDK 明确通知宿主环境就绪后调用 `X2C.init(this)`，然后再安装插件。自动 Provider 不提前绑定 runtime。需要在 Application.onCreate 之前访问 X2C 的原生 Provider 必须由宿主另行安排真实 Application 就绪后的初始化时序。

代理加载器可使用 `X2C.init(realApplication, stableHostLoader)`。加载器必须能够解析同一份 X2C 共享类型；重复绑定同一 Application/加载器允许，不同对象则报错且不改变已有状态。这里不 Hook SDK，也不会自动检测 SDK 的加固完成时间。初始化后宿主必须保持应用环境稳定，各进程分别初始化。

## 代码身份

`X2C.isPlugin(MyBusinessClass.class)` 按类的定义加载器识别代码归属，不能表示“当前调用者是谁”。插件调用宿主门面时，宿主门面类仍属于宿主。

内置 PluginClassLoader 在构造时登记。组件安装入口校验 registry 的定义加载器和 ABI 后也会登记。仅加载资源模块的第三方加载器必须通过 `X2C.registerPluginClassLoader(definingLoader)` 显式登记真实定义加载器，并保证共享 runtime 来自宿主。宿主业务类则通过宿主加载器能否解析出同一个 Class 来识别，允许代理加载器对象不同。未登记、且不能由宿主解析为同一 Class 的代码会报错。已登记插件缺失 bootstrap 不会回退为系统资源。

## 路由与 SDK

- `pluginActivity.startActivity(...)`、插件 Activity/Service Context 上的组件调用继续保留已有路由。
- `getApplicationContext().startActivity/startService/sendBroadcast(...)` 是宿主 Android 调用，不携带插件身份。插件目标必须使用公共 PluginActivityManager、PluginServiceManager、PluginReceiverManager 等明确的插件路由 API。
- 资源仍通过 `X2C.resources(context, MyBusinessClass.class)` 获取。宿主 Application 不能用于推断资源属于哪个插件。
- 需要 Application 的 SDK 可接收 `X2C.hostApplication()`。需要系统真实 Activity 的 SDK，应在插件场景接收 `PluginActivity.getContainerActivity()`。系统生命周期回调观察容器，这次没有新增 SDK 生命周期适配器。

## 版本兼容

该变化属于行为契约升级，组件 runtime ABI 提升为 2。ABI 1 载荷会被现有安装检查拒绝，需使用新编译插件重新构建，不应跳过 ABI 检查。仍需针对具体加固产品验证 DEX 加载、组件、资源重命名等策略，普通设备回归不是厂商兼容认证。
