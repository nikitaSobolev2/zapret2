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

val generatedResources = layout.buildDirectory.dir("generated/zapretResources")

val writeAppVersion = tasks.register("writeAppVersion") {
    val outDir = generatedResources
    val appVersion = provider { project.version.toString() }
    inputs.property("appVersion", appVersion)
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile
        dir.mkdirs()
        File(dir, "version.txt").writeText(appVersion.get().trim() + "\n")
    }
}

sourceSets {
    main {
        resources.srcDir(generatedResources)
    }
}

tasks.named("processResources") {
    dependsOn(writeAppVersion)
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

val zapretSourceRoot: File = rootDir.parentFile
val engineRoot = zapretSourceRoot.resolve("engine")
val engineStaged = layout.buildDirectory.dir("appResources/common/engine")

val stageEnginePayload = tasks.register<Sync>("stageEnginePayload") {
    doFirst { delete(engineStaged) }
    from(engineRoot.resolve("payload")) {
        exclude("**/.DS_Store")
    }
    into(engineStaged)
}

val buildUtunws = tasks.register<Exec>("buildUtunws") {
    dependsOn(stageEnginePayload)
    val dest = engineStaged.map { it.asFile.resolve("bin/utunws") }
    inputs.dir(engineRoot.resolve("nfq"))
    outputs.file(dest)
    commandLine(
        engineRoot.resolve("build_utunws.sh").absolutePath,
        dest.get().absolutePath,
    )
    doLast {
        val binary = dest.get()
        check(binary.isFile) { "utunws missing at $binary" }
        binary.setExecutable(true, false)
    }
}

val tgWsProxyRoot = zapretSourceRoot.resolve("third_party/tg-ws-proxy")
val tgWsProxyStaged = layout.buildDirectory.dir("appResources/common/tg-ws-proxy")

val buildTgWsProxySidecar = tasks.register<Exec>("buildTgWsProxySidecar") {
    onlyIf { tgWsProxyRoot.resolve("packaging/build_sidecar.sh").canExecute() }
    commandLine(
        tgWsProxyRoot.resolve("packaging/build_sidecar.sh").absolutePath,
        tgWsProxyStaged.get().asFile.absolutePath,
    )
    doLast {
        val binary = tgWsProxyStaged.get().asFile.resolve("tg-ws-proxy")
        check(binary.isFile) { "tg-ws-proxy sidecar missing at $binary" }
        binary.setExecutable(true, false)
    }
}

fun restoreSidecarExecuteBits(root: File) {
    listOf(
        root.resolve("engine/bin/utunws"),
        root.resolve("tg-ws-proxy/tg-ws-proxy"),
    ).forEach { binary ->
        if (binary.isFile) binary.setExecutable(true, false)
    }
    root.resolve("engine").listFiles()
        ?.filter { it.isFile && it.name.endsWith(".sh") }
        ?.forEach { it.setExecutable(true, false) }
    val sidecar = root.resolve("tg-ws-proxy")
    if (sidecar.isDirectory) {
        sidecar.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            val name = file.name
            if (name == "Python" || name.endsWith(".so") || name.endsWith(".dylib")) {
                file.setExecutable(true, false)
            }
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(buildUtunws, buildTgWsProxySidecar)
    // Compose/DMG staging can drop +x on nested Mach-O; readiness checks canExecute().
    doLast {
        restoreSidecarExecuteBits(layout.buildDirectory.get().asFile.resolve("appResources/common"))
    }
}

// Final .app / DMG copy also strips +x — fix after Compose packages the bundle.
tasks.matching {
    it.name in setOf(
        "createDistributable",
        "createReleaseDistributable",
        "packageDmg",
        "packageReleaseDmg",
        "runDistributable",
        "runReleaseDistributable",
    )
}.configureEach {
    doLast {
        val distRoots = listOf(
            layout.buildDirectory.get().asFile.resolve("compose/binaries/main/app"),
            layout.buildDirectory.get().asFile.resolve("compose/binaries/main-release/app"),
            layout.buildDirectory.get().asFile.resolve("compose/binaries/main/dmg"),
            layout.buildDirectory.get().asFile.resolve("compose/binaries/main-release/dmg"),
        )
        distRoots
            .filter { it.isDirectory }
            .flatMap { root ->
                root.walkTopDown()
                    .maxDepth(6)
                    .filter { it.isDirectory && it.name == "resources" && it.parentFile?.name == "app" }
                    .toList()
            }
            .forEach(::restoreSidecarExecuteBits)
    }
}

compose.desktop {
    application {
        mainClass = "zapret.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Zapret"
            packageVersion = project.version.toString()
            description = "Управление zapret на macOS (utunws)"
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
