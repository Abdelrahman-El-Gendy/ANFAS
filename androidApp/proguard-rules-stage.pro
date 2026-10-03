# R8 rules added to the `stage` app APK only -- never to `release`.
#
# `stage` is `initWith(release)`, so it inherits every production rule and this file is purely
# additive on top. Release shrinks exactly as it did before; nothing here can reach it.
#
# Why the app APK needs rules for a test's benefit at all: kotlin-stdlib is a dependency of both
# the app and the androidTest APK, and AGP ships a shared dependency in the app APK only, so the
# test APK resolves stdlib out of the app at runtime. The app inlines every `lazy` call and
# therefore retains no `kotlin.LazyKt`, while androidx.test's own Kotlin code
# (`TestDirCalculator.kt`) calls it -- so AndroidJUnitRunner died in `onCreate` with
# `NoClassDefFoundError: kotlin.LazyKt` before a single test ran. This cannot be fixed from
# `testProguardFiles`: with `-dontshrink` there R8 still keeps only the test APK's own program
# input, and stdlib is not in it.
#
# Scoped to stage rather than added to proguard-rules.pro deliberately. A keep rule in the
# production file to satisfy a test dependency is a rule nobody can later justify, and it makes
# the shipped app carry code no user path reaches. The cost of this scoping, stated rather than
# hidden: stage's shrink is not byte-identical to release's. It differs only by stdlib entry
# points the tests need, which cannot affect whether the app's own classes or its libraries'
# reflectively-instantiated ones survive -- and that is the failure mode these tests exist for
# (see the ML Kit / Firebase ComponentDiscovery note in proguard-rules.pro).
-keep class kotlin.LazyKt* { *; }
