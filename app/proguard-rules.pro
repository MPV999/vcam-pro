# VCAM PRO keeps its SDK entry points reachable for reflection-free usage.
-keep class com.vcam.sdk.** { *; }
-keep interface com.vcam.sdk.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# MediaCodec / Media3 are platform APIs; nothing to keep explicitly.
