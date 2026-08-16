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

// Final .app copy also strips +x — restore after Compose writes the bundle.
tasks.matching {
    it.name in setOf(
        "createDistributable",
        "createReleaseDistributable",
        "runDistributable",
        "runReleaseDistributable",
    )
}.configureEach {
    doLast {
        val distRoots = listOf(
            layout.buildDirectory.get().asFile.resolve("compose/binaries/main/app"),
            layout.buildDirectory.get().asFile.resolve("compose/binaries/main-release/app"),
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

fun runProcess(vararg command: String): Pair<Int, String> {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    return process.waitFor() to output
}

fun buildDmgWithHdiutil(appBundle: File, dmgFile: File, volumeName: String) {
    check(appBundle.isDirectory && appBundle.name == "Zapret.app") {
        "App bundle missing at $appBundle"
    }
    val srcFolder = appBundle.parentFile
    check(srcFolder != null && srcFolder.resolve("Zapret.app") == appBundle) {
        "Zapret.app must sit in its Gradle output folder"
    }
    dmgFile.parentFile.mkdirs()
    val (code, output) = runProcess(
        "/usr/bin/hdiutil",
        "create",
        "-volname",
        volumeName,
        "-srcfolder",
        srcFolder.absolutePath,
        "-ov",
        "-format",
        "UDZO",
        "-imagekey",
        "zlib-level=9",
        dmgFile.absolutePath,
    )
    check(code == 0 && dmgFile.isFile) { "hdiutil create failed for $dmgFile\n$output" }
}

fun registerHdiutilDmg(
    taskName: String,
    appImageTask: String,
    appDir: String,
    dmgDir: String,
) {
    tasks.register(taskName) {
        dependsOn(appImageTask)
        val dmgOut = layout.buildDirectory.file("$dmgDir/Zapret-${version}.dmg")
        inputs.dir(layout.buildDirectory.dir(appDir))
        outputs.file(dmgOut)
        doLast {
            val bundle = layout.buildDirectory.get().asFile.resolve(appDir).resolve("Zapret.app")
            val dest = dmgOut.get().asFile
            val resources = bundle.resolve("Contents/app/resources")
            if (resources.isDirectory) restoreSidecarExecuteBits(resources)
            logger.lifecycle("Packaging ${dest.name} with hdiutil (not jpackage)")
            buildDmgWithHdiutil(bundle, dest, "Zapret")
            logger.lifecycle("The distribution is written to ${dest.absolutePath}")
        }
    }
}

compose.desktop {
    application {
        mainClass = "zapret.MainKt"

        nativeDistributions {
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

registerHdiutilDmg(
    "packageDmg",
    "createDistributable",
    "compose/binaries/main/app",
    "compose/binaries/main/dmg",
)
registerHdiutilDmg(
    "packageReleaseDmg",
    "createReleaseDistributable",
    "compose/binaries/main-release/app",
    "compose/binaries/main-release/dmg",
)

gradle.projectsEvaluated {
    val dmgClass = tasks.getByName("packageDmg").javaClass.name
    check(!dmgClass.contains("JPackage") && !dmgClass.contains("jpackage")) {
        "packageDmg is still $dmgClass; Compose jpackage DMG must not be registered"
    }
}
