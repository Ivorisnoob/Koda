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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

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
