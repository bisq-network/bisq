package bisq.gradle.packaging.jpackage

import bisq.gradle.packaging.jpackage.package_formats.PackageFormat
import org.gradle.api.GradleException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.Year
import java.util.Comparator
import java.util.concurrent.TimeUnit

class PackageFactory(private val jPackagePath: Path, private val jPackageConfig: JPackageConfig) {

    companion object {
        private const val SOURCE_DATE_EPOCH = "SOURCE_DATE_EPOCH"
        private const val DEFAULT_SOURCE_DATE_EPOCH = "0"
    }

    fun createPackages() {
        val jPackageCommonArgs: List<String> = createCommonArguments(jPackageConfig.appConfig)

        val packageFormatConfigs = jPackageConfig.packageFormatConfigs
        val perPackageCommand = packageFormatConfigs.packageFormats
                .map { packageFormat ->
                    packageFormat to packageFormatConfigs.createArgumentsForJPackage(packageFormat) + listOf("--type", packageFormat.fileExtension)
                }

        perPackageCommand.forEach { (packageFormat, customCommands) ->
            val processBuilder = ProcessBuilder(jPackagePath.toAbsolutePath().toString())
                    .inheritIO()
            processBuilder.environment().putIfAbsent(SOURCE_DATE_EPOCH, DEFAULT_SOURCE_DATE_EPOCH)
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

            if (packageFormat == PackageFormat.RPM) {
                normalizeRpmPackage(jPackageConfig.appConfig)
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

        processBuilder.environment()["HOME"] = createRpmHome().toAbsolutePath().toString()
    }

    private fun createRpmHome(): Path {
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
        return rpmHomePath
    }

    private fun normalizeRpmPackage(appConfig: JPackageAppConfig) {
        val rpmPath = findSinglePackageArtifact(PackageFormat.RPM)
        val repackRoot = jPackageConfig.temporaryDirPath.resolve("rpm-repack")
        repackRoot.toFile().deleteRecursively()

        val payloadRoot = repackRoot.resolve("payload")
        val topDir = repackRoot.resolve("rpmbuild")
        val specDir = topDir.resolve("SPECS")
        val buildRoot = repackRoot.resolve("buildroot")
        Files.createDirectories(payloadRoot)
        Files.createDirectories(specDir)

        runProcess(
                ProcessBuilder(
                        "sh",
                        "-c",
                        "cd ${shellSingleQuote(payloadRoot.toAbsolutePath().toString())} && " +
                                "rpm2cpio ${shellSingleQuote(rpmPath.toAbsolutePath().toString())} | cpio -idm --quiet"
                ),
                "extract RPM payload"
        )

        val specPath = specDir.resolve("bisq.spec")
        Files.writeString(specPath, createNormalizedRpmSpec(appConfig, payloadRoot))

        runProcess(
                ProcessBuilder(
                        "rpmbuild",
                        "-bb",
                        "--buildroot", buildRoot.toAbsolutePath().toString(),
                        "--define", "_topdir ${topDir.toAbsolutePath()}",
                        "--define", "_buildhost bisq-release-builder",
                        "--define", "use_source_date_epoch_as_buildtime 1",
                        "--define", "clamp_mtime_to_source_date_epoch 1",
                        "--define", "source_date_epoch_from_changelog 0",
                        "--define", "_binary_payload w9.gzdio",
                        "--define", "_build_id_links none",
                        "--define", "__os_install_post %{nil}",
                        specPath.toAbsolutePath().toString()
                ),
                "rebuild normalized RPM"
        )

        val rebuiltRpmPath = topDir
                .resolve("RPMS")
                .resolve("x86_64")
                .resolve(rpmPath.fileName)
        if (!Files.isRegularFile(rebuiltRpmPath)) {
            throw GradleException("rpmbuild did not create expected RPM: ${rebuiltRpmPath.toAbsolutePath()}")
        }

        Files.move(rebuiltRpmPath, rpmPath, StandardCopyOption.REPLACE_EXISTING)
        normalizePathTimestamps(rpmPath)
    }

    private fun createNormalizedRpmSpec(appConfig: JPackageAppConfig, payloadRoot: Path): String {
        val sourceDateEpoch = sourceDateEpochSeconds()
        val payloadRootPath = shellSingleQuote(payloadRoot.toAbsolutePath().toString())

        return """
            Name: bisq
            Version: ${appConfig.appVersion}
            Release: 1
            Summary: Bisq
            License: AGPLv3
            Group: Unspecified
            Vendor: Bisq
            Prefix: /opt
            BuildArch: x86_64
            AutoReqProv: no
            Requires: xdg-utils
            Provides: bisq

            %description
            A decentralized bitcoin exchange network.

            %prep

            %build

            %install
            rm -rf "%{buildroot}"
            mkdir -p "%{buildroot}"
            cp -a ${payloadRootPath}/. "%{buildroot}/"
            find "%{buildroot}" -exec touch -h -d @${sourceDateEpoch} {} +

            %pre
            package_type=rpm

            if [ "${'$'}1" = 2 ]; then
              true;
            fi

            %post
            package_type=rpm

            xdg-desktop-menu install /opt/bisq/lib/bisq-Bisq.desktop

            %preun
            package_type=rpm

            xdg-desktop-menu uninstall /opt/bisq/lib/bisq-Bisq.desktop

            %files
            %defattr(-,root,root,-)
            /opt
        """.trimIndent() + "\n"
    }

    private fun findSinglePackageArtifact(packageFormat: PackageFormat): Path {
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

    private fun runProcess(processBuilder: ProcessBuilder, description: String) {
        processBuilder.inheritIO()
        processBuilder.environment().putIfAbsent(SOURCE_DATE_EPOCH, DEFAULT_SOURCE_DATE_EPOCH)
        processBuilder.environment()["HOME"] = createRpmHome().toAbsolutePath().toString()

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

    private fun normalizePathTimestamps(path: Path) {
        val timestamp = FileTime.from(Instant.ofEpochSecond(sourceDateEpochSeconds()))
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { entry ->
                Files.setLastModifiedTime(entry, timestamp)
            }
        }
    }

    private fun sourceDateEpochSeconds(): Long {
        val sourceDateEpoch = System.getenv(SOURCE_DATE_EPOCH) ?: DEFAULT_SOURCE_DATE_EPOCH
        return sourceDateEpoch.toLongOrNull() ?: DEFAULT_SOURCE_DATE_EPOCH.toLong()
    }

    private fun shellSingleQuote(value: String): String {
        return "'${value.replace("'", "'\"'\"'")}'"
    }
}
