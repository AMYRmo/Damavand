# Damavand Lite — ProGuard / R8 rules.
# Keep the libv2ray JNI bridge and the hev TProxy JNI entry points.
-keep class libv2ray.** { *; }
-keep class hev.htproxy.** { *; }
-keep class com.amyrmo.damavand.lite.XrayController { *; }
-keep class com.amyrmo.damavand.lite.HevTunCore { *; }
