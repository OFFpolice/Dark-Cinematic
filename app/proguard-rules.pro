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

# Keep application data models and Room entities
-keep class com.example.data.** { *; }
-keepclassmembers class com.example.data.** { *; }
-dontwarn com.example.data.**

# Keep ViewBinding and Android components
-keep class com.example.databinding.** { *; }
-keep class com.example.VideoWallpaperService** { *; }

