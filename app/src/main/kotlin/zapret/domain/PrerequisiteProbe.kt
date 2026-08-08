package zapret.domain

import kotlin.time.Duration.Companion.seconds

/**
 * Machine readiness the app needs before install/start. Probe only — never prompts for a password.
 */
data class Prerequisites(
    val hasCompiler: Boolean,
    val hasSources: Boolean,
    val hasPrebuiltBinary: Boolean,
    val passwordlessControl: Boolean,
    val wanInterface: String?,
    val zapretInstalled: Boolean,
) {
    /** Packaged apps ship tpws; otherwise Xcode CLT is required to compile on install. */
    val canInstall: Boolean get() = hasSources && (hasPrebuiltBinary || hasCompiler)

    val canStart: Boolean get() = zapretInstalled

    /** True when nothing blocks the user from installing or running. */
    val isReady: Boolean get() = when {
        zapretInstalled -> wanInterface != null
        else -> canInstall
    }

    companion object {
        fun probe(passwordless: Boolean): Prerequisites {
            val sources = ZapretPaths.sourceTree()
            return Prerequisites(
                hasCompiler = compilerPresent(),
                hasSources = sources != null,
                hasPrebuiltBinary = sources?.let(ZapretPaths::hasPrebuiltTpws) == true,
                passwordlessControl = passwordless,
                wanInterface = WanInterface.detect(),
                zapretInstalled = ZapretPaths.isInstalled,
            )
        }

        fun requestCompilerInstall(): CommandResult =
            Shell.run("/usr/bin/xcode-select", "--install", timeout = 15.seconds)

        private fun compilerPresent(): Boolean =
            Shell.run("/usr/bin/xcrun", "--find", "cc", timeout = 5.seconds).ok
    }
}
