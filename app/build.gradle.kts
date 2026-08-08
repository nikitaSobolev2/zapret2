import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
    // Previous make leaves relative symlinks; Sync cannot replace those in-place.
    doFirst { delete(zapretStaged) }
    from(zapretSourceRoot) {
        exclude(".git", ".github", ".cursor", "app", "docs", "nfq2", "tmp", "third_party")
        exclude("**/.DS_Store", "**/.gradle/**")
        // Always rebuild inside the stage dir; do not copy a host binaries/ tree.
        exclude("binaries")
    }
    into(zapretStaged)
}

val tgWsProxyRoot = zapretSourceRoot.resolve("third_party/tg-ws-proxy")
val tgWsProxyStaged = layout.buildDirectory.dir("appResources/common/tg-ws-proxy")

val buildTgWsProxySidecar = tasks.register<Exec>("buildTgWsProxySidecar") {
    onlyIf { tgWsProxyRoot.resolve("packaging/build_sidecar.sh").canExecute() }
    commandLine(
        tgWsProxyRoot.resolve("packaging/build_sidecar.sh").absolutePath,
        tgWsProxyStaged.get().asFile.absolutePath,
    )
}

val buildStagedZapret = tasks.register<Exec>("buildStagedZapret") {
    dependsOn(stageZapretSource)
    doFirst { workingDir = zapretStaged.get().asFile }
    commandLine("make", "mac")
    doLast {
        // make mac links tpws/ → binaries/my/; jpackage ad-hoc codesign and Sync dislike that.
        val root = zapretStaged.get().asFile.toPath()
        Files.walk(root).use { paths ->
            paths.filter { Files.isSymbolicLink(it) }.forEach { link ->
                val target = link.toRealPath()
                Files.delete(link)
                Files.copy(target, link, StandardCopyOption.COPY_ATTRIBUTES)
                link.toFile().setExecutable(true, false)
            }
        }
    }
}

// the Compose plugin collects appResourcesRootDir in this task, so the sources must be staged first
tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(buildStagedZapret, buildTgWsProxySidecar)
}

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
