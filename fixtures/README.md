# Fixtures

这里同时保留两种发布模式，并把动态插件测试拆成两个互不混杂的 payload。

## 动态插件宿主

`consumer` 是唯一 APK。Host Application 只调用一次 `X2C.init(base)`；
`DynamicLibraryLoader` 验签、安装并创建两个独立 `PluginClassLoader`，再分别安装 generated
ComponentRegistry 与 X2C resource module。宿主以 implementation 持有 runtime、loader 和
`x2c-plugin-base`，插件对这些模块只使用 compileOnly。

宿主 APK 中的两个测试 envelope：

```text
assets/x2c-layout-showcase/codegen-dex.{jar,descriptor,sig}
assets/x2c-component-showcase/codegen-dex.{jar,descriptor,sig}
```

两个 JAR 都只有 `classes.dex`，不包含 Android `res/`、resource table、业务 Manifest 组件或
`x2c-plugin-api/base/runtime/loader`。fixture 私钥只用于测试，不能进入生产。

测试 envelope 同样遵守生产防回滚约束：修改任一 payload 后，必须同步递增
`fixtures/consumer/build.gradle.kts` 中对应的 showcase `versionCode`。复用版本号但改变摘要会在加载
ClassLoader 前被明确拒绝，不能通过清空或放宽生产安装器来规避。

## Demo 1：Layout / resources / image showcase

物理 module 为 `producer`，pluginId 为 `dev.x2c.fixture.layout-showcase`。

- `content.xml`：对外能力展厅、成熟登录页、点击/输入/校验/延迟结果/SharedPreferences；
- `capability_catalog.xml`：以 hero、数据卡、支持层级表和能力自检展示 53 种 framework View、
  28 种 ViewGroup/LayoutParams、values/drawable/custom View/图片的准确边界；
- `framework_matrix.xml`：LinearLayout、FrameLayout、RelativeLayout、GridLayout、
  TableLayout/TableRow、RadioGroup、HorizontalScrollView 及常用 framework View；
- 自定义 View：Context-only、AttributeSet、四构造函数、自定义属性 setter、自定义 FlowLayout 和 LayoutParams；
- values：string/format、color selector、dimen、integer、bool、fraction、array、typed-array、
  plurals、synthetic id/R2；
- drawable：shape/selector/layer-list/inset/clip/scale/rotate/level-list 和 bitmap-to-CDN metadata；
- 图片：XML `android:src` 自动异步加载，成功、取消、重新绑定、宿主内存缓存和明确失败路径；
- `JvmFeatureProbe`：内部类、lambda、泛型、集合、异常、逻辑运算等 JVM class-only 用例。

业务 Activity 继承独立 `business-base` Android library 中的普通 `BusinessBaseActivity extends Activity`。
该根使用 `@X2cPluginBase`；宿主使用 `implementation`，两个插件使用 `compileOnly project`。插件构建只把
实际继承的标注 class 闭包复制进 payload，并转换为 `BusinessBaseActivity -> PluginActivity`。页面会显示 constructor、
attach/create/start/resume 顺序以及 plugin-private ClassLoader，验证真实业务基类代码被完整执行。

## Demo 2：Activity / 四大组件 showcase

物理 module 为 `producer-secondary`，pluginId 为
`dev.x2c.fixture.component-showcase`。

- Component Dashboard；
- 四个独立 Activity 覆盖 standard、singleTop、singleTask、singleInstance、实例 identity、
  taskId、onCreate/onNewIntent、self relaunch 与 Activity Result；
- Service 覆盖 started/bound/stop、Binder、ComponentName、`START_NOT_STICKY`；
- Receiver 覆盖显式 normal/ordered broadcast、result code/data、`goAsync().finish()`；
- Provider 覆盖虚拟 authority、CRUD、query、call、applyBatch、ContentObserver 与 read-only file；
- 插件内跳转、跨两个 ClassLoader 跳转、插件返回宿主 Activity；
- Activity 同样使用 plugin-private `business-base` 闭包；Service/Receiver/Provider 保留宿主持有的
  `x2c-plugin-base` 根，两种 ownership 在同一 payload 中同时验证。

## 标准 AAR 模式

`normal-library` + `normal-app` 验证 `pluginMode=false`。它保留正常 AAR resource table、
Manifest Activity 和本地图片，由 generated provider 使用宿主 Resources；不会生成动态 DEX JAR。

## 验证

```bash
./scripts/verify.sh
./scripts/verify-plugin-modes.sh
./scripts/verify-plugin-showcases.sh    # 需要 adb 设备
```

`verify.sh` 会检查两个 JAR、BusinessBase 依赖闭包与公共组件 base 边界、四种 launchMode 与其他组件报告、RSA envelope、
宿主资源隔离、Manifest 固定容器、图片锁定元数据、deterministic rebuild，以及 normal AAR 对照组。
