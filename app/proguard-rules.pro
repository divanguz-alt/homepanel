# Libraries ship their own consumer rules (kotlinx.serialization, OkHttp, Glance, WorkManager, Tink).
# Annotation-only dependencies Tink references but doesn't need at runtime:
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Glance creates widget click callbacks by reflection (Class.forName + no-arg constructor). Without
# this R8 strips the constructor and every tap fails with NoSuchMethodException in the receiver.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
