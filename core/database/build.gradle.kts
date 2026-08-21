plugins {
    id("anfas.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(project(":core:common"))

            // `api`, not `implementation`: AnfasDatabase extends RoomDatabase and
            // DatabaseBuilderFactory returns RoomDatabase.Builder, so room3-runtime is part
            // of this module's ABI — :core:data cannot compile against it otherwise.
            // Features never see any of this; anfas.kmp.feature withholds :core:database.
            api(libs.androidx.room3.runtime)
            // The driver is an implementation detail of buildDatabase().
            implementation(libs.androidx.sqlite.bundled)
        }
        androidMain.dependencies {
            implementation(libs.androidx.room3.sqliteWrapper)
        }
    }
}

// Extension is `room3`, matching the androidx.room3 artifact rename — not `room`.
room3 {
    // Migration schemas are committed to git. A schema change means a migration.
    schemaDirectory("$projectDir/schemas")
}

// KSP must be declared PER TARGET. A single `ksp(...)` line applies only to the
// default/JVM compilation and silently generates nothing for the native targets,
// so Room codegen would be missing on iOS with no error at all.
dependencies {
    add("kspAndroid", libs.androidx.room3.compiler)
    add("kspIosArm64", libs.androidx.room3.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room3.compiler)
    add("kspJvm", libs.androidx.room3.compiler)
}
