package bisq.gradle.packaging.jpackage

import org.gradle.api.GradleException
import java.nio.file.Path
import java.time.Year
import java.util.concurrent.TimeUnit

class PackageFactory(private val jPackagePath: Path, private val jPackageConfig: JPackageConfig) {

    fun createPackages() {
        val jPackageCommonArgs: List<String> = createCommonArguments(jPackageConfig.appConfig)

        val packageFormatConfigs = jPackageConfig.packageFormatConfigs
        val perPackageCommand = packageFormatConfigs.packageFormats
                .map { packageFormatConfigs.createArgumentsForJPackage(it) + listOf("--type", it.fileExtension) }

        val absoluteBinaryPath = jPackagePath.toAbsolutePath().toString()
        perPackageCommand.forEach { customCommands ->
            val processBuilder = ProcessBuilder(absoluteBinaryPath)
                    .inheritIO()

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
}
