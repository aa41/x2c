# 三合一测试 APK

唯一安装入口：`:fixtures:consumer`。完整说明与测试矩阵见 [DEMO_SUITE](../docs/DEMO_SUITE.md)。

1. 全能力实验室：producer 的 Layout/资源/图片用例，并可跳转 producer-secondary 的四大组件用例。
2. 哔哩哔哩风格首页：producer-secondary 内的 VideoHomeActivity/VideoDetailActivity，XML2Java 布局、本地交互和宿主 raw 视频播放。
3. 普通 API 工作台：normal-library 静态集成进 consumer，默认无 X2C 配置，使用系统 Resources。

`business-base` 保留宿主/插件共用业务基类与埋点闭包，runtime 等共享类型由宿主持有。两个签名插件载荷使用不同 ClassLoader；normal-library 使用宿主 ClassLoader。宿主 Application 就绪后初始化 X2C，再安装载荷。

构建需要 JDK 17+ 和本机 Android SDK：

```bash
./gradlew :fixtures:consumer:assembleDebug
bash scripts/verify.sh
ADB=/path/to/adb ANDROID_SERIAL=device bash scripts/verify-plugin-showcases.sh
```

APK：`consumer/build/outputs/apk/debug/consumer-debug.apk`。

旧 normal-app 独立工程、旧登录/JVM 演示与独立组件首页入口已退役；组件功能作为全能力实验室中的分类继续使用。不会删除基础库的自动化回归测试。

普通 API 页面通过 `-Px2c.normal.enable=true` 切换到生成路径，省略或传 false 使用系统资源。不要给整个三合一工程统一传 `-Px2c.enable=false`，因为其中两个资源为空的动态插件必须开启生成。脚本 `build-x2c-artifact.sh` 的 module 选项用于单独构建模块。
