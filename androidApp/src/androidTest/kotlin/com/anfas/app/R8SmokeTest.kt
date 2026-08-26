package com.anfas.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one thing only a device can tell us: whether the shrunk app actually runs.
 *
 * These run against the **`stage`** build type (`testBuildType = "stage"`), which is
 * `initWith(release)` — minified and resource-shrunk — but debuggable and debug-signed, because
 * `connectedAndroidTest` needs a debuggable APK and `release` must never be one. So this is the
 * *shrunk* app, not the debug one.
 *
 * Until now nothing verified that build except installing the APK by hand and reading logcat. That
 * is how the ML Kit breakage was found: R8 stripped constructors that Firebase's
 * `ComponentDiscovery` instantiates reflectively, OCR silently stopped working, and it surfaced
 * only as a *warning*.
 *
 * ### What this file may and may not do
 *
 * Every assertion goes through the app's **own entry points** — launch it, recreate it — and never
 * calls an app or library API directly. That is a hard constraint, learned the expensive way rather
 * than chosen as a style. The first version of this file called `runBlocking`,
 * `GlobalContext.getOrNull()`, a `DefaultComponentContext` constructor and `kotlin.test`'s
 * assertions, and all five tests failed with `NoSuchMethodError` / `NoClassDefFoundError` — because
 * the app never calls any of those, so R8 correctly removed them. **Any library API a test reaches
 * for is by definition outside the app's reachable graph**, so a keep rule bringing it back proves
 * only that the keep rule works. Hence also `org.junit.Assert` rather than `kotlin.test`: JUnit is
 * Java, ships inside the test APK, and touches no shrunk code.
 *
 * ### What `stage` does and does not verify
 *
 * Shrinking, not obfuscation. AGP disables obfuscation and optimization for debuggable build types
 * and says so at configuration time; verified rather than assumed — in
 * `build/outputs/mapping/stage/mapping.txt` every non-identity entry is an
 * `R8$$REMOVED$$CLASS$$n`, a deletion, and not one class is renamed. Shrinking is the failure mode
 * that has actually bitten this project, so it is the one covered here; a rename-specific
 * regression would need a non-debuggable build and a different harness.
 */
@RunWith(AndroidJUnit4::class)
class R8SmokeTest {

    /**
     * Cold start on the minified APK — the broadest single check available, and previously only
     * ever done by hand.
     *
     * Launching runs `AnfasApplication.onCreate` (so Koin starts and every module is registered),
     * builds the Decompose root, opens the Room database through the bundled SQLite driver, and
     * composes the start destination. A class R8 removed from anywhere on that path throws here,
     * and `ActivityScenario.launch` fails the test rather than logging a warning.
     */
    @Test
    fun theMinifiedAppStarts() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                assertFalse(
                    "MainActivity finished during launch on the minified build",
                    activity.isFinishing,
                )
            }
        } finally {
            scenario.close()
        }
    }

    /**
     * The documented R8 hazard, exercised through the framework rather than asserted about.
     *
     * Decompose serialises the whole back stack into Essenty's `StateKeeper` on save and reads it
     * back on restore. A `@Serializable` sealed hierarchy whose variants carry no explicit
     * `@SerialName` defaults its discriminator to the class name, which a shrinker may rename — and
     * because writer and reader agree within any one build, that tests clean everywhere except
     * across an app update. `recreate()` is the closest a test gets to the real thing: it drives a
     * genuine save-then-restore of instance state inside the minified app, so the serializers,
     * their descriptors and the `StateKeeper` round trip all have to survive for it to return.
     *
     * `ConfigSerializationTest` pins the discriminator strings themselves on the JVM. This proves
     * the machinery that consumes them is still there after shrinking.
     */
    @Test
    fun theMinifiedAppSurvivesSaveAndRestore() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            // Identity of the instance before and after, so this test cannot pass by doing
            // nothing: if `recreate()` were a no-op the save/restore path would never run, and a
            // test asserting only "did not finish" would still be green.
            val before = arrayOfNulls<Any>(1)
            scenario.onActivity { activity -> before[0] = activity }

            scenario.recreate()

            scenario.onActivity { activity ->
                assertFalse(
                    "MainActivity finished while restoring saved state on the minified build",
                    activity.isFinishing,
                )
                assertNotSame(
                    "recreate() did not replace the activity, so saved state was never " +
                        "written and read back — this test would be vacuous",
                    before[0],
                    activity,
                )
            }
        } finally {
            scenario.close()
        }
    }
}
