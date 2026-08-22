plugins {
    id("anfas.kmp.compose")
}

/**
 * Capture and text recognition, behind a platform-agnostic interface.
 *
 * A :core:* module rather than source sets inside :feature:intake-ocr, for two reasons. The
 * reconstruction algorithm is the valuable, reusable part and would be trapped in a module no
 * other feature may depend on; and anfas.kmp.feature's contract is that a feature declares
 * almost nothing of its own, which growing ML Kit, a FileProvider and a res/xml folder would
 * break.
 *
 * Deliberately NOT granted by anfas.kmp.feature: only :feature:intake-ocr needs it, and adding
 * it there would put ML Kit on all seven features' compile classpaths. Intake opts in with one
 * line, exactly as :core:auth was once opted into.
 *
 * Note this module knows nothing about IntakeBatch. It returns List<OcrLine>; assembling those
 * into rows is :core:model's job and orchestration is the feature's, which keeps
 * IntakeRepository.createBatch the single ingestion seam its KDoc promises.
 */
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(project(":core:common"))
            implementation(libs.bundles.koin)
        }
        androidMain.dependencies {
            implementation(libs.mlkit.textRecognition)
            // rememberLauncherForActivityResult — capture is an activity result, not a camera API.
            implementation(libs.androidx.activity.compose)
            // FileProvider, for handing the camera app a writable URI.
            implementation(libs.androidx.core.ktx)
            // androidContext() — every actual here needs a Context.
            implementation(libs.koin.android)
        }
    }
}
