package bisq.gradle.packaging

import java.util.Locale

enum class Architecture(val installerClassifier: String) {
    X86_64("x86_64"),
    AARCH64("aarch64")
}

fun getArchitecture(): Architecture {
    val architecture = System.getProperty("os.arch").lowercase(Locale.US)
    if (architecture == "aarch64" || architecture == "arm64") {
        return Architecture.AARCH64
    }
    if (architecture == "x86_64" || architecture == "amd64" || architecture == "x64" ||
            architecture == "x86" || architecture == "i386" || architecture == "i686") {
        return Architecture.X86_64
    }

    throw IllegalStateException("Running on unsupported Architecture: $architecture")
}
