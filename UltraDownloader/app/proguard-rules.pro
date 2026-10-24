-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class com.ultradownloader.app.** {
    companion object;
}
-dontwarn okhttp3.**
-dontwarn okio.**
