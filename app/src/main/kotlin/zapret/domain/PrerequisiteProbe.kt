package zapret.domain

import kotlin.time.Duration.Companion.seconds

/**
 * Machine readiness the app needs before install/start. Probe only — never prompts for a password.
 */
data class Prerequisites(
    val hasCompiler: Boolean,
    val hasSources: Boolean,
    val passwordlessControl: Boolean,
    val wanInterface: String?,
    val zapretInstalled: Boolean,
) {
    val canInstall: Boolean get() = hasCompiler && hasSources

    val canStart: Boolean get() = zapretInstalled

    /** True when nothing blocks the user from installing or running. */
    val isReady: Boolean get() = when {
        zapretInstalled -> wanInterface != null
        else -> canInstall
    }

    companion object {
        fun probe(passwordless: Boolean): Prerequisites = Prerequisites(
            hasCompiler = compilerPresent(),
            hasSources = ZapretPaths.sourceTree() != null,
            passwordlessControl = passwordless,
            wanInterface = WanInterface.detect(),
            zapretInstalled = ZapretPaths.isInstalled,
        )

        fun requestCompilerInstall(): CommandResult =
            Shell.run("/usr/bin/xcode-select", "--install", timeout = 15.seconds)

        private fun compilerPresent(): Boolean =
            Shell.run("/usr/bin/xcrun", "--find", "cc", timeout = 5.seconds).ok
    }
}
