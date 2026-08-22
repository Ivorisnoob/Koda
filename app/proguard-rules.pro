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

# NewPipe and Rhino are called through ordinary bytecode. Keeping their entire
# package trees defeats R8's shrinking and retains extractors and JavaScript
# internals Koda never reaches. Their warning suppressions above are sufficient;
# concrete reflective entry points, if any are added later, should get narrow
# rules from the owning library instead of another package-wide keep.

# youtubedl-android (bundled Python + yt-dlp). The library invokes the Python
# runtime via JNI/reflection, so its classes must not be renamed or stripped.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**