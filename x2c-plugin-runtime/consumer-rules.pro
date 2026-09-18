# These host-owned classes are called by code delivered after the host APK was shrunk.
# R8 cannot discover those call sites while building the host, so their binary ABI must remain stable.
-keep class dev.x2c.plugin.runtime.** { *; }
-keep class dev.x2c.plugin.api.** { *; }
-keep class dev.x2c.plugin.base.** { *; }
-keep class dev.x2c.runtime.** { *; }
