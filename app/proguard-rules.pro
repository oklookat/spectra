# Project specific ProGuard / R8 rules

# Keep all models and data classes used for Gson serialization
-keep class com.oklookat.spectra.model.** { *; }
-keepclassmembers class com.oklookat.spectra.model.** { *; }

-keep class com.oklookat.spectra.data.** { *; }
-keepclassmembers class com.oklookat.spectra.data.** { *; }

# Keep androidx @Keep annotated classes and members
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Keep Gson attributes and SerializedName fields
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod,InnerClasses

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep libv2ray
-keep class libv2ray.** { *; }
-keepclassmembers class libv2ray.** { *; }
