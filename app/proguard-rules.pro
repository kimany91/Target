# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.target.**$$serializer { *; }
-keepclassmembers class com.target.** { *** Companion; }
-keepclasseswithmembers class com.target.** { kotlinx.serialization.KSerializer serializer(...); }

# ONNX Runtime (JNI)
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
