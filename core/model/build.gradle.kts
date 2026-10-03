plugins {
    id("anfas.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

// :core:model depends on NOTHING. No project dependencies, and no external
// dependencies beyond the pure-Kotlin allowlist. See CLAUDE.md layering rules.

// kotlinx-datetime only. LocalDate is a domain concept here — a renewal's start and end are
// calendar dates, not instants — and datetime is on the pure-Kotlin allowlist the layering
// check enforces for this module.
//
// `api`, not `implementation`: LocalDate appears in this module's public types
// (SubscriptionTerm.startsOn, RenewalQuote.endsOn), so it is part of the ABI and consumers
// cannot compile against it otherwise.
//
// kotlinx-serialization-json is here for one file, `RelayWire.kt`: the request/response shapes
// that the device and `:server` both speak. This module is the only thing both already depend
// on, and a wire contract declared once cannot drift between its two ends. It is on the
// allowlist, and nothing else in the module is serialisable.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.json)
        }
    }
}
