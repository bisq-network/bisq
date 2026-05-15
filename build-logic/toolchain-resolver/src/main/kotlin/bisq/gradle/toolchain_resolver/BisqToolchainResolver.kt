package bisq.gradle.toolchain_resolver

import org.gradle.jvm.toolchain.JavaToolchainDownload
import org.gradle.jvm.toolchain.JavaToolchainRequest
import org.gradle.jvm.toolchain.JavaToolchainResolver
import org.gradle.platform.OperatingSystem
import java.net.URI
import java.util.*

private const val OS_ARCH_PROPERTY = "os.arch"

@Suppress("UnstableApiUsage")
abstract class BisqToolchainResolver : JavaToolchainResolver {
    override fun resolve(toolchainRequest: JavaToolchainRequest): Optional<JavaToolchainDownload> {
        val operatingSystem = toolchainRequest.buildPlatform.operatingSystem
        val javaVersion = toolchainRequest.javaToolchainSpec.languageVersion.get().asInt()

        val toolchainUrl: String = when (operatingSystem) {
            OperatingSystem.LINUX -> getToolchainUrlForLinux(javaVersion)
            OperatingSystem.MAC_OS -> getToolchainUrlForMacOs(javaVersion)
            OperatingSystem.WINDOWS -> getToolchainUrlForWindows(javaVersion)
            else -> null

        } ?: return Optional.empty()

        val uri = URI(toolchainUrl)
        return Optional.of(
            JavaToolchainDownload.fromUri(uri)
        )
    }

    private fun getToolchainUrlForLinux(javaVersion: Int): String? =
        when (javaVersion) {
            11 -> "https://cdn.azul.com/zulu/bin/zulu11.66.15-ca-jdk11.0.20-linux_x64.zip"
            17 -> "https://cdn.azul.com/zulu/bin/zulu17.44.15-ca-jdk17.0.8-linux_x64.zip"
            21 -> "https://cdn.azul.com/zulu/bin/zulu21.48.15-ca-jdk21.0.10-linux_x64.zip"
            else -> null
        }

    private fun getToolchainUrlForMacOs(javaVersion: Int): String? =
        when (javaVersion) {
            11 -> "https://cdn.azul.com/zulu/bin/zulu11.66.15_1-ca-jdk11.0.20-macosx_x64.tar.gz"
            17 -> "https://cdn.azul.com/zulu/bin/zulu17.44.15_1-ca-jdk17.0.8-macosx_x64.tar.gz"
            21 -> "https://cdn.azul.com/zulu/bin/zulu21.48.15-ca-jdk21.0.10-macosx_${getMacOsArchitectureClassifier()}.tar.gz"
            else -> null
        }

    private fun getToolchainUrlForWindows(javaVersion: Int): String? =
        when (javaVersion) {
            11 -> "https://cdn.azul.com/zulu/bin/zulu11.66.15-ca-jdk11.0.20-win_x64.zip"
            17 -> "https://cdn.azul.com/zulu/bin/zulu17.44.15-ca-jdk17.0.8-win_x64.zip"
            21 -> "https://cdn.azul.com/zulu/bin/zulu21.48.15-ca-jdk21.0.10-win_x64.zip"
            else -> null
        }

    private fun getMacOsArchitectureClassifier(): String {
        val osArch = getOsArch()
        if (osArch.isBlank()) {
            throw IllegalStateException("Cannot choose macOS JDK toolchain because os.arch is missing or blank (value='$osArch').")
        }

        val architecture = osArch.trim().lowercase(Locale.US)
        if (architecture == "aarch64" || architecture == "arm64") {
            return "aarch64"
        }
        if (architecture == "x86_64" || architecture == "amd64" || architecture == "x64" ||
            architecture == "x86" || architecture == "i386" || architecture == "i686") {
            return "x64"
        }

        throw IllegalStateException("Cannot choose macOS JDK toolchain for unsupported os.arch value: '$osArch'.")
    }

    private fun getOsArch(): String {
        return try {
            System.getProperty(OS_ARCH_PROPERTY, "")
        } catch (e: SecurityException) {
            throw IllegalStateException("Cannot choose macOS JDK toolchain because os.arch cannot be read.", e)
        }
    }
}
