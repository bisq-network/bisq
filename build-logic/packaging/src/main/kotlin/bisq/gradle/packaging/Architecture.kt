package bisq.gradle.packaging

import java.util.Locale

enum class Architecture(val installerClassifier: String) {
    X86_64("x86_64"),
    AARCH64("aarch64")
}

private const val OS_ARCH_PROPERTY = "os.arch"

fun getArchitecture(): Architecture {
    val osArch = getOsArch()
    if (osArch.isBlank()) {
        throw IllegalStateException("Running on unsupported Architecture: os.arch is missing or blank (value='$osArch')")
    }

    val architecture = osArch.trim().lowercase(Locale.US)
    if (architecture == "aarch64" || architecture == "arm64") {
        return Architecture.AARCH64
    }
    if (architecture == "x86_64" || architecture == "amd64" || architecture == "x64" ||
            architecture == "x86" || architecture == "i386" || architecture == "i686") {
        return Architecture.X86_64
    }

    throw IllegalStateException("Running on unsupported Architecture: os.arch='$osArch'")
}

private fun getOsArch(): String {
    return try {
        System.getProperty(OS_ARCH_PROPERTY, "")
    } catch (e: SecurityException) {
        throw IllegalStateException("Cannot determine Architecture because os.arch cannot be read.", e)
    }
}
