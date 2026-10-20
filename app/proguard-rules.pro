# Tink references these optional annotation-only classes. They are not needed at runtime.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy

# Tink (used by androidx.security.crypto's EncryptedSharedPreferences, for PasscodeStore and
# ProfileStore) resolves its key managers by class name at runtime; R8 strips that registry
# unless every class in it is kept, which otherwise only breaks in a release build.
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# SQLCipher's JNI bridge loads these classes by name; net.zetetic is the Support* wrapper this
# app calls into, net.sqlcipher is the native binding it wraps.
-keep,includedescriptorclasses class net.zetetic.** { *; }
-keep,includedescriptorclasses class net.sqlcipher.** { *; }
-dontwarn net.zetetic.**
-dontwarn net.sqlcipher.**
