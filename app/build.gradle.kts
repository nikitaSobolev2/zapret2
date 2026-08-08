import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("org.jetbrains.compose") version "1.11.1"
}

group = "zapret"
// CI passes -PappVersion=1.2.3 (from git tag v1.2.3); local default stays 1.0.0
version = providers.gradleProperty("appVersion").orElse("1.0.0").get()

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(compose.desktop.currentOs)
    @Suppress("DEPRECATION")
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    testImplementation(kotlin("test"))
}

// Packaged app carries zapret2 sources plus prebuilt universal mac binaries (make mac).
val zapretSourceRoot: File = rootDir.parentFile
val zapretStaged = layout.buildDirectory.dir("appResources/common/zapret2-src")

val stageZapretSource = tasks.register<Sync>("stageZapretSource") {
    from(zapretSourceRoot) {
        exclude(".git", ".github", ".cursor", "app", "docs", "nfq2", "tmp")
        exclude("**/.DS_Store", "**/.gradle/**")
        // Always rebuild inside the stage dir; do not copy a host binaries/ tree.
        exclude("binaries")
    }
    into(zapretStaged)
}

val buildStagedZapret = tasks.register<Exec>("buildStagedZapret") {
    dependsOn(stageZapretSource)
    doFirst { workingDir = zapretStaged.get().asFile }
    commandLine("make", "mac")
}

// the Compose plugin collects appResourcesRootDir in this task, so the sources must be staged first
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(buildStagedZapret) }

compose.desktop {
    application {
        mainClass = "zapret.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Zapret"
            packageVersion = project.version.toString()
            description = "Управление zapret2 на macOS"
            appResourcesRootDir.set(layout.buildDirectory.dir("appResources"))

            macOS {
                bundleID = "org.zapret.macos.control"
                dockName = "Zapret"
                minimumSystemVersion = "12.0"
                // unsigned local DMG: user may need right-click → Open the first time (Gatekeeper)
                appCategory = "public.app-category.utilities"
                iconFile.set(project.file("icons/Zapret.icns"))
            }
        }
    }
}
