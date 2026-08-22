# R8 rules for the release and stage build types.
#
# Deliberately short. Verified by unzipping the actual artifacts, the risky libraries already
# ship correct consumer rules: room3-runtime.aar carries the RoomDatabase keep, sqlite-bundled.aar
# carries the JNI keep, and kotlinx-serialization-core ships rules for Companion/serializer().
# Room's KMP @ConstructedBy path is a direct constructor call rather than Class.forName, so the
# classic Room/R8 failure mode does not apply to this project at all.
#
# Anything added here should say WHY, and be something the libraries do not already cover.

# --- Decompose navigation state -------------------------------------------------------------
# decompose-android.aar ships no proguard.txt of its own, and Decompose serialises the whole back
# stack into Essenty's StateKeeper on every state save. The durable fix is the explicit
# @SerialName on each Config variant in RootComponent.kt -- this is a second layer, so a variant
# that someone forgets to annotate degrades to "works" rather than "crashes on cold resume after
# an app update". See ConfigSerializationTest.
-keepnames class com.anfas.app.navigation.RootComponent$Config
-keepnames class com.anfas.app.navigation.RootComponent$Config$* { *; }

# --- Kotlin/Native and coroutines internals -------------------------------------------------
# Coroutines' debug agent probes for these at runtime and logs a warning when they are missing.
-dontwarn kotlinx.coroutines.debug.**

# --- ML Kit ----------------------------------------------------------------------------------
# Found by installing the minified APK and reading logcat, not by reasoning about it:
#
#   W ComponentDiscovery: Caused by: java.lang.NoSuchMethodException:
#     com.google.mlkit.vision.text.internal.TextRegistrar.<init> []
#
# ML Kit registers its components through Firebase's ComponentDiscovery, which reads class names
# out of AndroidManifest <meta-data> entries and instantiates them reflectively. R8 sees no caller
# and strips the no-arg constructors, so text recognition silently fails to initialise -- in
# release builds only, and as a *warning*, so nothing crashes and nothing obviously breaks. The
# sheet just never produces rows.
#
# Names as well as constructors have to survive, because the manifest refers to these classes by
# their original fully-qualified names -- hence -keep rather than -keep,allowobfuscation.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}

# Warnings only: ML Kit probes for optional Play Services modules that a bundled-model build does
# not contain.
-dontwarn com.google.android.gms.internal.**

# Keep source file and line numbers so a release stack trace is readable once retraced against
# mapping.txt. Without this a crash report is a list of unnamed frames.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
