# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep real file names and line numbers in release stack traces. The in-app
# bug reporter (ReportBugScreen / CrashReporter) puts traces straight into
# user reports, and without this a minified release trace reads
# "a.b.c: Unknown source(1)". Pair with the mapping.txt upload in build.yml,
# which is what deobfuscates the renamed symbols.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Fix for NewPipe Extractor / Rhino (missing java.beans and javax.script on Android)
-dontwarn org.mozilla.javascript.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn com.google.common.**
-dontwarn com.google.re2j.**

# Keep NewPipe Extractor classes if they are being stripped too aggressively
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }

# youtubedl-android (bundled Python + yt-dlp). The library invokes the Python
# runtime via JNI/reflection, so its classes must not be renamed or stripped.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**

# jAudioTagger reads local lyric tags. Its tag bodies are instantiated by
# identifier and its desktop-only artwork helpers are never used on Android.
-keep class org.jaudiotagger.tag.id3.framebody.** { *; }
-keep class org.jaudiotagger.tag.datatype.** { *; }
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**

# WorkManager opens its Room-backed database reflectively from the
# androidx.startup initializer (WorkManagerInitializer -> Class.forName ->
# getDeclaredConstructor). R8 full mode strips the generated *_Impl
# constructors as unreferenced, which crashes the process at
# InitializationProvider.onCreate before any Activity - seen live August 2026
# as "NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []"
# on a minified build that worked fine unminified. Keep every Room database
# implementation and worker reachable.
-keep class * extends androidx.room.RoomDatabase { <init>(...); }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
