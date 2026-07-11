# Bento's backup/DataStore contracts use generated kotlinx serializers. Keep
# those generated entry points explicit even though normal references are
# static, so import/export cannot be broken by future reflective call sites.
-keepattributes Signature,*Annotation*
-keep class **$$serializer { *; }
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
