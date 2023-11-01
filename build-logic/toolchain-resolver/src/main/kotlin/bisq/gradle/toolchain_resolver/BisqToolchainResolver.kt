package bisq.gradle.toolchain_resolver

import org.gradle.jvm.toolchain.JavaToolchainDownload
import org.gradle.jvm.toolchain.JavaToolchainRequest
import org.gradle.jvm.toolchain.JavaToolchainResolver
import org.gradle.platform.OperatingSystem
import java.net.URI
import java.util.*

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
            else -> null
        }

    private fun getToolchainUrlForMacOs(javaVersion: Int): String? =
        when (javaVersion) {
            11 -> "https://cdn.azul.com/zulu/bin/zulu11.66.15_1-ca-jdk11.0.20-macosx_x64.tar.gz"
            15 -> "https://cdn.azul.com/zulu/bin/zulu15.46.17-ca-jdk15.0.10-macosx_x64.tar.gz"
            17 -> "https://cdn.azul.com/zulu/bin/zulu17.44.15_1-ca-jdk17.0.8-macosx_x64.tar.gz"
            else -> null
        }

    private fun getToolchainUrlForWindows(javaVersion: Int): String? =
        when (javaVersion) {
            11 -> "https://cdn.azul.com/zulu/bin/zulu11.66.15-ca-jdk11.0.20-win_x64.zip"
            17 -> "https://cdn.azul.com/zulu/bin/zulu17.44.15-ca-jdk17.0.8-win_x64.zip"
            else -> null
        }
}
