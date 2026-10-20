# Libraries ship their own consumer rules (kotlinx.serialization, OkHttp, Glance, WorkManager, Tink).
# Annotation-only dependencies Tink references but doesn't need at runtime:
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
