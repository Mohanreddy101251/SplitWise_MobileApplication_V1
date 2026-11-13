# Native encrypted database (JNI) and PDF reader (loads resources by name)
-keep class net.zetetic.database.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
# App classes: keep names so crash reports stay readable; R8 still removes unused library code
-keep class com.spliwise.** { *; }
-keepattributes SourceFile,LineNumberTable
