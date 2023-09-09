package bisq.gradle.packaging.jpackage.package_formats

import java.nio.file.Path

class LinuxPackages(private val resourcesPath: Path) : JPackagePackageFormatConfigs {
    override val packageFormats = setOf(PackageFormat.DEB, PackageFormat.RPM)

    override fun createArgumentsForJPackage(packageFormat: PackageFormat): List<String> {
        val arguments = mutableListOf(
                "--icon",
                resourcesPath.resolve("icon.png")
                        .toAbsolutePath().toString(),

                "--linux-package-name", "bisq",
                "--linux-app-release", "1",

                "--linux-menu-group", "Network",
                "--linux-shortcut",

                "--linux-deb-maintainer",
                "noreply@bisq.network",
        )

        if (packageFormat == PackageFormat.DEB) {
            arguments.add("--linux-deb-maintainer")
            arguments.add("noreply@bisq.network")
        }

        if (packageFormat == PackageFormat.RPM) {
            arguments.add("--linux-rpm-license-type")
            arguments.add("AGPLv3")
        }

        return arguments
    }
}
