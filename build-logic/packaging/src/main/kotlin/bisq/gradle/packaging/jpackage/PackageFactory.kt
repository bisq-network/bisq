package bisq.gradle.packaging.jpackage

import bisq.gradle.packaging.OS
import bisq.gradle.packaging.getArchitecture
import bisq.gradle.packaging.getOS
import bisq.gradle.packaging.jpackage.package_formats.PackageFormat
import org.gradle.api.GradleException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.Year
import java.time.ZoneOffset
import java.util.Comparator
import java.util.concurrent.TimeUnit

class PackageFactory(private val jPackagePath: Path, private val jPackageConfig: JPackageConfig) {

    companion object {
        const val SOURCE_DATE_EPOCH = "SOURCE_DATE_EPOCH"
        const val DEFAULT_SOURCE_DATE_EPOCH = "0"

        fun createCommonArguments(appConfig: JPackageAppConfig, jPackageConfig: JPackageConfig): List<String> {
            val currentYear = Instant.ofEpochSecond(sourceDateEpochSeconds())
                .atZone(ZoneOffset.UTC)
                .year

            return mutableListOf(
                "--temp", jPackageConfig.jPackageTempDirPath.toAbsolutePath().toString(),
                "--dest", jPackageConfig.outputDirPath.toAbsolutePath().toString(),

                "--name", "Bisq",
                "--description", "A decentralized bitcoin exchange network.",
                "--copyright", "Copyright © 2013-$currentYear - The Bisq developers",
                "--vendor", "Bisq",

                "--app-version", appConfig.appVersion,

                "--input", jPackageConfig.inputDirPath.toAbsolutePath().toString(),
                "--main-jar", appConfig.mainJarFileName,

                "--main-class", appConfig.mainClassName,
                "--java-options", appConfig.jvmArgs.joinToString(separator = " "),
            )
        }

        fun findSinglePackageArtifact(packageFormat: PackageFormat, jPackageConfig: JPackageConfig): Path {
            val extension = ".${packageFormat.fileExtension}"
            val matchingArtifacts = Files.list(jPackageConfig.outputDirPath).use { files ->
                files.filter { path ->
                    Files.isRegularFile(path) && path.fileName.toString().endsWith(extension)
                }.toList()
            }

            if (matchingArtifacts.size != 1) {
                throw GradleException(
                    "Expected exactly one ${extension} package in ${jPackageConfig.outputDirPath}, " +
                            "found ${matchingArtifacts.size}: ${matchingArtifacts.joinToString(", ")}"
                )
            }

            return matchingArtifacts.single()
        }

        fun runProcess(processBuilder: ProcessBuilder, description: String) {
            processBuilder.inheritIO()
            processBuilder.environment().putIfAbsent(SOURCE_DATE_EPOCH, DEFAULT_SOURCE_DATE_EPOCH)

            val allCommands = processBuilder.command()
            val process = processBuilder.start()
            val finished = process.waitFor(15, TimeUnit.MINUTES)
            if (!finished) {
                process.destroyForcibly()
                throw GradleException("${description} timed out after 15 minutes: ${allCommands.joinToString(" ")}")
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                throw GradleException("${description} failed with exit code ${exitCode}: ${allCommands.joinToString(" ")}")
            }
        }

        fun shellSingleQuote(value: String): String {
            return "'${value.replace("'", "'\"'\"'")}'"
        }

        fun normalizePathTimestamps(path: Path) {
            val timestamp = FileTime.from(Instant.ofEpochSecond(sourceDateEpochSeconds()))
            Files.walk(path).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { entry ->
                    Files.setLastModifiedTime(entry, timestamp)
                }
            }
        }

        fun sourceDateEpochSeconds(): Long {
            val sourceDateEpoch = System.getenv(SOURCE_DATE_EPOCH) ?: PackageFactory.DEFAULT_SOURCE_DATE_EPOCH
            return sourceDateEpoch.toLongOrNull() ?: DEFAULT_SOURCE_DATE_EPOCH.toLong()
        }
    }

    fun createPackages() {
        val jPackageCommonArgs: List<String> = createCommonArguments(jPackageConfig.appConfig, jPackageConfig)

        val packageFormatConfigs = jPackageConfig.packageFormatConfigs
        val perPackageCommand = packageFormatConfigs.packageFormats
            .map { packageFormat ->
                if (packageFormat == PackageFormat.DEB || packageFormat == PackageFormat.RPM) {
                    throw GradleException(
                        "This Gradle task doesn't support the packaging formats " +
                                "${PackageFormat.DEB} and ${PackageFormat.RPM}."
                    )
                }

                packageFormat to packageFormatConfigs.createArgumentsForJPackage(packageFormat) + listOf(
                    "--type",
                    packageFormat.fileExtension
                )
            }

        perPackageCommand.forEach { (packageFormat, customCommands) ->
            val processBuilder = ProcessBuilder(jPackagePath.toAbsolutePath().toString())
                .inheritIO()
            processBuilder.environment().putIfAbsent(SOURCE_DATE_EPOCH, DEFAULT_SOURCE_DATE_EPOCH)

            val allCommands = processBuilder.command()
            allCommands.addAll(jPackageCommonArgs)
            allCommands.addAll(customCommands)

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
                throw GradleException("jpackage timed out after 15 minutes: ${allCommands.joinToString(" ")}")
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                throw GradleException("jpackage failed with exit code $exitCode: ${allCommands.joinToString(" ")}")
            }

            if (packageFormat == PackageFormat.DMG && getOS() == OS.MAC_OS) {
                renameMacOsDmgPackage(jPackageConfig.appConfig)
            }
        }
    }

    private fun renameMacOsDmgPackage(appConfig: JPackageAppConfig) {
        val sourcePath = jPackageConfig.outputDirPath.resolve("Bisq-${appConfig.appVersion}.dmg")
        val targetPath =
            jPackageConfig.outputDirPath.resolve("Bisq-${getArchitecture().installerClassifier}-${appConfig.appVersion}.dmg")
        if (!Files.exists(sourcePath) && Files.exists(targetPath)) {
            return
        }

        if (!Files.exists(sourcePath)) {
            throw GradleException("Expected macOS DMG not found: ${sourcePath.toAbsolutePath()}")
        }

        Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
    }
}
