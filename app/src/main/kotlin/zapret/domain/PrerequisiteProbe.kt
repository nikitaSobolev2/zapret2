package zapret.domain

import kotlin.time.Duration.Companion.seconds

data class Prerequisites(
    val hasCompiler: Boolean,
    val hasSources: Boolean,
    val hasPrebuiltBinary: Boolean,
    val passwordlessControl: Boolean,
    val wanInterface: String?,
    val zapretInstalled: Boolean,
) {
    val canInstall: Boolean get() = hasSources && hasPrebuiltBinary

    val canStart: Boolean get() = zapretInstalled || canInstall

    val isReady: Boolean get() = when {
        zapretInstalled -> wanInterface != null
        else -> canInstall
    }

    companion object {
        fun probe(passwordless: Boolean): Prerequisites {
            val payload = ZapretPaths.enginePayload()
            return Prerequisites(
                hasCompiler = compilerPresent(),
                hasSources = payload != null,
                hasPrebuiltBinary = payload?.let(ZapretPaths::hasPrebuiltUtunws) == true,
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
