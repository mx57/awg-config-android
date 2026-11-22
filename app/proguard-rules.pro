# JNI: нативные методы GoBackend привязаны к именам классов — не переименовывать/не вырезать
-keep class com.wireguard.** { *; }
-dontwarn com.wireguard.**

# zxing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Keep source line numbers for readable stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
