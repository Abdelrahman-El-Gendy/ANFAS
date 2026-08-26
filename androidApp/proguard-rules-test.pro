# R8 rules for the androidTest APK only, wired via `testProguardFiles`.
#
# Deliberately a separate file from proguard-rules.pro. The instrumented tests run against the
# minified `stage` build (that is the whole point of that build type), which means the *test* APK
# is shrunk too -- and androidx.test drags in references the app itself never has. Putting those
# rules in the production file would loosen the shipped app's configuration to satisfy a test
# dependency, and a `-dontwarn` added there is invisible the day it starts hiding a real problem.

# The test APK is not the artifact under test, so nothing is gained by shrinking it -- and
# something is lost. R8 shrinks the test APK against the app APK, and anything the app did not
# retain is gone: the runner died on `NoClassDefFoundError: kotlin.LazyKt` because the app inlines
# every `lazy` call and so ships no LazyKt, while androidx.test's own Kotlin code calls it.
# Keeping every such class by hand would be whack-a-mole against a dependency we do not control.
#
# Normally the test APK must be processed so its references track the app's obfuscated names --
# but `stage` is debuggable, and AGP disables obfuscation and optimization for debuggable build
# types (it says so at configuration time). Verified rather than assumed: in
# build/outputs/mapping/stage/mapping.txt every non-identity entry is an `R8$$REMOVED$$CLASS$$n`,
# a deletion, and not one class is renamed. With no renaming to track there is nothing to align,
# so R8 on the test APK reduces to a dexing pass.
-dontshrink

# androidx.test's Checks/Tracer are annotated with ErrorProne annotations that are compile-time
# only -- @CanIgnoreReturnValue and @MustBeClosed have CLASS retention and ship in
# com.google.errorprone:error_prone_annotations, which is not a dependency of anything here.
# Absent annotation types are erased at class-load, so there is nothing to keep; R8 only needs
# telling that the dangling references are expected. Kept alongside -dontshrink because R8 still
# resolves references even when it is not removing anything.
-dontwarn com.google.errorprone.annotations.**
