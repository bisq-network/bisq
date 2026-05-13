package bisq.gradle.packaging.jpackage

import bisq.gradle.packaging.jpackage.package_formats.PackageFormat
import org.gradle.api.GradleException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Year
import java.util.concurrent.TimeUnit

class PackageFactory(private val jPackagePath: Path, private val jPackageConfig: JPackageConfig) {

    fun createPackages() {
        val jPackageCommonArgs: List<String> = createCommonArguments(jPackageConfig.appConfig)

        val packageFormatConfigs = jPackageConfig.packageFormatConfigs
        val perPackageCommand = packageFormatConfigs.packageFormats
                .map { packageFormat ->
                    packageFormat to packageFormatConfigs.createArgumentsForJPackage(packageFormat) + listOf("--type", packageFormat.fileExtension)
                }

        val absoluteBinaryPath = jPackagePath.toAbsolutePath().toString()
        perPackageCommand.forEach { (packageFormat, customCommands) ->
            val processBuilder = ProcessBuilder(absoluteBinaryPath)
                    .inheritIO()
            configureReproducibleRpmEnvironment(processBuilder, packageFormat)

            val allCommands = processBuilder.command()
            allCommands.addAll(jPackageCommonArgs)
            allCommands.addAll(customCommands)

            val process: Process = processBuilder.start()
            val finished = process.waitFor(15, TimeUnit.MINUTES)
            if (!finished) {
                process.destroyForcibly()
                throw GradleException("jpackage timed out after 15 minutes: ${allCommands.joinToString(" ")}")
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                throw GradleException("jpackage failed with exit code $exitCode: ${allCommands.joinToString(" ")}")
            }
        }
    }

    private fun createCommonArguments(appConfig: JPackageAppConfig): List<String> =
            mutableListOf(
                    "--dest", jPackageConfig.outputDirPath.toAbsolutePath().toString(),

                    "--name", "Bisq",
                    "--description", "A decentralized bitcoin exchange network.",
                    "--copyright", "Copyright © 2013-${Year.now()} - The Bisq developers",
                    "--vendor", "Bisq",

                    "--app-version", appConfig.appVersion,

                    "--input", jPackageConfig.inputDirPath.toAbsolutePath().toString(),
                    "--main-jar", appConfig.mainJarFileName,

                    "--main-class", appConfig.mainClassName,
                    "--java-options", appConfig.jvmArgs.joinToString(separator = " "),

                    "--runtime-image", jPackageConfig.runtimeImageDirPath.toAbsolutePath().toString()
            )

    private fun configureReproducibleRpmEnvironment(processBuilder: ProcessBuilder, packageFormat: PackageFormat) {
        if (packageFormat != PackageFormat.RPM) {
            return
        }

        val rpmHomePath = jPackageConfig.temporaryDirPath.resolve("rpm-home")
        Files.createDirectories(rpmHomePath)
        Files.writeString(
                rpmHomePath.resolve(".rpmmacros"),
                """
                    %use_source_date_epoch_as_buildtime 1
                    %clamp_mtime_to_source_date_epoch 1
                    %_buildhost bisq-release-builder
                """.trimIndent() + "\n"
        )

        processBuilder.environment()["HOME"] = rpmHomePath.toAbsolutePath().toString()
        processBuilder.environment().putIfAbsent("SOURCE_DATE_EPOCH", "0")
    }
}
