plugins {
    id("anfas.kmp.library")
}

// :core:model depends on NOTHING. No project dependencies, and no external
// dependencies beyond the pure-Kotlin allowlist. See CLAUDE.md layering rules.
