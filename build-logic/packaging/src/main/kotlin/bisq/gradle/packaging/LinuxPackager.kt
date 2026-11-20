package bisq.gradle.packaging

import bisq.gradle.packaging.jpackage.JPackageConfig
import bisq.gradle.packaging.jpackage.PackageFactory
import bisq.gradle.packaging.jpackage.package_formats.JPackagePackageFormatConfigs
import bisq.gradle.packaging.jpackage.package_formats.PackageFormat
import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class LinuxPackager(
    private val jPackagePath: Path,
    private val jPackageConfig: JPackageConfig,
    private val packageFormatConfigs: JPackagePackageFormatConfigs
) {
    companion object {
        // The subset of our common options which jpackage accepts together with --app-image
        private val PACKAGE_OPTIONS = setOf(
            "--temp", "--dest", "--name", "--description", "--copyright", "--vendor", "--app-version"
        )
    }

    fun build(packageFormat: PackageFormat) {
        build(packageFormat, emptyMap())
    }

    // A DEB or RPM built directly by jpackage misses a random part of the runtime's legal/ symlinks, so
    // two builds of the same commit give different packages. The app image is complete, so the package
    // is built from it.
    fun build(packageFormat: PackageFormat, environment: Map<String, String>) {
        val appImageDirPath = jPackageConfig.temporaryDirPath.resolve("app-image")
        deleteDirectory(appImageDirPath.toFile())
        runJPackage(createAppImageArguments(packageFormat, appImageDirPath), environment)

        val appImagePath = appImageDirPath.resolve(PackageFactory.APP_NAME)
        runJPackage(createPackageArguments(packageFormat, appImagePath), environment)
    }

    private fun createAppImageArguments(packageFormat: PackageFormat, appImageDirPath: Path): List<String> {
        val commonArgs = createCommonOptions()
            .map { (option, value) ->
                if (option == "--dest") listOf(option, appImageDirPath.toAbsolutePath().toString())
                else listOf(option, value)
            }
            .flatten()
        val formatArgs = packageFormatConfigs.createArgumentsForJPackage(packageFormat)
        val iconIndex = formatArgs.indexOf("--icon")
        val iconArgs = if (iconIndex >= 0) formatArgs.subList(iconIndex, iconIndex + 2) else emptyList()
        return commonArgs + iconArgs + listOf("--type", "app-image")
    }

    private fun createPackageArguments(packageFormat: PackageFormat, appImagePath: Path): List<String> {
        val commonArgs = createCommonOptions()
            .filter { (option, _) -> option in PACKAGE_OPTIONS }
            .flatten()
        return commonArgs +
                listOf("--app-image", appImagePath.toAbsolutePath().toString()) +
                packageFormatConfigs.createArgumentsForJPackage(packageFormat) +
                listOf("--type", packageFormat.fileExtension)
    }

    // The common arguments are option/value pairs, which lets us drop or replace single options.
    private fun createCommonOptions(): List<List<String>> {
        val commonArgs = PackageFactory.createCommonArguments(jPackageConfig.appConfig, jPackageConfig)
        val options = commonArgs.chunked(2)
        if (options.any { it.size != 2 || !it[0].startsWith("--") }) {
            throw GradleException("Expected option/value pairs in the common jpackage arguments: $commonArgs")
        }
        return options
    }

    private fun runJPackage(arguments: List<String>, environment: Map<String, String>) {
        val processBuilder = ProcessBuilder(listOf(jPackagePath.toAbsolutePath().toString()) + arguments)
            .inheritIO()
        processBuilder.environment()[PackageFactory.SOURCE_DATE_EPOCH] = PackageFactory.DEFAULT_SOURCE_DATE_EPOCH
        processBuilder.environment().putAll(environment)

        // jpackage fails if temp directory is not empty
        deleteDirectory(jPackageConfig.jPackageTempDirPath.toFile())

        val process: Process = processBuilder.start()
        val finished = process.waitFor(15, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
            throw GradleException("jpackage timed out after 15 minutes: ${arguments.joinToString(" ")}")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            throw GradleException("jpackage failed with exit code $exitCode: ${arguments.joinToString(" ")}")
        }
    }

    private fun deleteDirectory(directory: File) {
        if (directory.exists() && !directory.deleteRecursively()) {
            throw GradleException("Couldn't delete directory: ${directory.absolutePath}")
        }
    }
}
