package bisq.gradle.packaging

import bisq.gradle.packaging.jpackage.JPackageConfig
import bisq.gradle.packaging.jpackage.PackageFactory
import bisq.gradle.packaging.jpackage.package_formats.JPackagePackageFormatConfigs
import bisq.gradle.packaging.jpackage.package_formats.PackageFormat
import org.gradle.api.GradleException
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.collections.plus

class LinuxPackager(
    private val jPackagePath: Path,
    private val jPackageConfig: JPackageConfig,
    private val packageFormatConfigs: JPackagePackageFormatConfigs
) {
    fun build(packageFormat: PackageFormat) {
        build(packageFormat, emptyMap())
    }

    fun build(packageFormat: PackageFormat, environment: Map<String, String>) {
        val linuxPackager = LinuxPackager(jPackagePath, jPackageConfig, packageFormatConfigs)

        val processBuilder: ProcessBuilder = linuxPackager.preparePackageProcess()
        processBuilder.environment()
            .putAll(environment)

        val commands = linuxPackager.createCommandsForPackageFormat(packageFormat)
        processBuilder.command()
            .addAll(commands)

        // jpackage fails if temp directory is not empty
        val jPackageTempDir = jPackageConfig.jPackageTempDirPath.toFile()
        if (jPackageTempDir.exists()) {
            val isSuccess = jPackageTempDir.deleteRecursively()
            if (!isSuccess) {
                throw GradleException("Couldn't delete jpackage temp directory: ${jPackageTempDir.absolutePath}")
            }
        }

        val process: Process = processBuilder.start()
        val finished = process.waitFor(15, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
            throw GradleException("jpackage timed out after 15 minutes: ${commands.joinToString(" ")}")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            throw GradleException("jpackage failed with exit code $exitCode: ${commands.joinToString(" ")}")
        }
    }

    fun createCommandsForPackageFormat(
        packageFormat: PackageFormat
    ): List<String> {
        val commonArgs: List<String> =
            PackageFactory.createCommonArguments(jPackageConfig.appConfig, jPackageConfig)
        val customCommands = createCustomCommandsForPackageFormat(packageFormatConfigs, packageFormat)
        return commonArgs + customCommands
    }

    fun preparePackageProcess(): ProcessBuilder {
        val processBuilder = ProcessBuilder(jPackagePath.toAbsolutePath().toString())
            .inheritIO()
        processBuilder.environment()[PackageFactory.SOURCE_DATE_EPOCH] = PackageFactory.DEFAULT_SOURCE_DATE_EPOCH
        return processBuilder
    }

    private fun createCustomCommandsForPackageFormat(
        packageFormatConfigs: JPackagePackageFormatConfigs,
        packageFormat: PackageFormat
    ): List<String> =
        packageFormatConfigs.createArgumentsForJPackage(packageFormat) + listOf(
            "--type",
            packageFormat.fileExtension
        )
}
