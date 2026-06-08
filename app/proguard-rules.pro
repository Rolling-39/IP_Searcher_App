# Add project specific ProGuard rules here.
# You can control the set of detected configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep data model classes
-keep class com.example.ipsearcher.data.model.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
