package com.anfas.app.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks the *persisted* form of every navigation [RootComponent.Config] variant.
 *
 * Decompose writes the back stack into Essenty's StateKeeper, so these strings end up in saved
 * state on real devices. If R8 renames a Config class and the discriminator defaults to the class
 * name, a build reading state saved by an earlier build cannot resolve it — a crash on cold resume
 * after an app update, reproducible only in a release build, only across an update. An explicit
 * @SerialName decouples the format from the class name, and this test is what stops someone
 * removing one.
 *
 * A failure here is never fixed by updating the expected string: an existing value may be sitting
 * in saved state on someone's phone, so the value is append-only.
 */
class ConfigSerializationTest {

    private val json = Json

    @Test
    fun `every config variant encodes its stable discriminator`() {
        val expected = mapOf<RootComponent.Config, String>(
            RootComponent.Config.Dashboard to "dashboard",
            RootComponent.Config.MembersList to "members-list",
            RootComponent.Config.ReminderQueue to "reminder-queue",
            RootComponent.Config.IntakeReview to "intake-review",
            RootComponent.Config.MemberProfile(memberId = "m-1") to "member-profile",
            RootComponent.Config.Renewal(memberId = "m-1") to "renewal",
        )

        expected.forEach { (config, discriminator) ->
            val encoded = json.encodeToString(
                RootComponent.Config.serializer(),
                config,
            )
            assertTrue(
                encoded.contains("\"$discriminator\""),
                "expected the literal discriminator \"$discriminator\" in $encoded",
            )
        }
    }

    @Test
    fun `a config with arguments survives a round trip`() {
        val original: RootComponent.Config = RootComponent.Config.Renewal(memberId = "m-42")
        val encoded = json.encodeToString(RootComponent.Config.serializer(), original)

        assertEquals(
            original,
            json.decodeFromString(RootComponent.Config.serializer(), encoded),
        )
    }

    /**
     * Decoding state written by an *earlier* build. Hardcoded rather than round-tripped on
     * purpose: a round trip passes even if every name changed, because the same code wrote it.
     */
    @Test
    fun `state written by an earlier build still decodes`() {
        val saved = """{"type":"renewal","memberId":"m-7"}"""

        assertEquals(
            RootComponent.Config.Renewal(memberId = "m-7"),
            json.decodeFromString(RootComponent.Config.serializer(), saved),
        )
    }
}
