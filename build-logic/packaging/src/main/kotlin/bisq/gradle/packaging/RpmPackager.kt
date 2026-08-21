package bisq.gradle.packaging

import bisq.gradle.packaging.jpackage.JPackageAppConfig
import bisq.gradle.packaging.jpackage.JPackageConfig
import bisq.gradle.packaging.jpackage.PackageFactory
import bisq.gradle.packaging.jpackage.package_formats.JPackagePackageFormatConfigs
import bisq.gradle.packaging.jpackage.package_formats.PackageFormat
import org.gradle.api.GradleException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class RpmPackager(private val jPackagePath: Path, private val jPackageConfig: JPackageConfig) {
    fun build(packageFormatConfigs: JPackagePackageFormatConfigs) {
        val linuxPackager = LinuxPackager(jPackagePath, jPackageConfig, packageFormatConfigs)
        val environment = mutableMapOf(Pair("HOME", createRpmHome().toAbsolutePath().toString()))

        linuxPackager.build(PackageFormat.RPM, environment)
        normalizeRpmPackage()
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

    fun normalizeRpmPackage() {
        val rpmPath = PackageFactory.findSinglePackageArtifact(PackageFormat.RPM, jPackageConfig)
        val repackRoot = jPackageConfig.temporaryDirPath.resolve("rpm-repack")
        val repackRootFile = repackRoot.toFile()
        if (repackRootFile.exists() && !repackRootFile.deleteRecursively()) {
            throw GradleException("Couldn't delete RPM repack directory: ${repackRoot.toAbsolutePath()}")
        }

        val payloadRoot = repackRoot.resolve("payload")
        val topDir = repackRoot.resolve("rpmbuild")
        val specDir = topDir.resolve("SPECS")
        val buildRoot = repackRoot.resolve("buildroot")
        Files.createDirectories(payloadRoot)
        Files.createDirectories(specDir)

        PackageFactory.runProcess(
            ProcessBuilder(
                "sh",
                "-c",
                "cd ${PackageFactory.shellSingleQuote(payloadRoot.toAbsolutePath().toString())} && " +
                        "rpm2cpio ${
                            PackageFactory.shellSingleQuote(
                                rpmPath.toAbsolutePath().toString()
                            )
                        } | cpio -idm --quiet"
            ),
            "extract RPM payload"
        )

        val specPath = specDir.resolve("bisq.spec")
        Files.writeString(specPath, createNormalizedRpmSpec(jPackageConfig.appConfig, payloadRoot))

        PackageFactory.runProcess(
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
        PackageFactory.normalizePathTimestamps(rpmPath)
    }

    private fun createNormalizedRpmSpec(appConfig: JPackageAppConfig, payloadRoot: Path): String {
        val sourceDateEpoch = PackageFactory.sourceDateEpochSeconds()
        val payloadRootPath = PackageFactory.shellSingleQuote(payloadRoot.toAbsolutePath().toString())

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
}
