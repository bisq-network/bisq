package bisq.gradle.packaging

import bisq.gradle.packaging.jpackage.JPackageConfig
import bisq.gradle.packaging.jpackage.PackageFactory
import bisq.gradle.packaging.jpackage.package_formats.JPackagePackageFormatConfigs
import bisq.gradle.packaging.jpackage.package_formats.PackageFormat
import org.gradle.api.GradleException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class DebPackager(private val jPackagePath: Path, private val jPackageConfig: JPackageConfig) {
    fun build(packageFormatConfigs: JPackagePackageFormatConfigs) {
        val linuxPackager = LinuxPackager(jPackagePath, jPackageConfig, packageFormatConfigs)
        linuxPackager.build(PackageFormat.DEB)
        relaxDebT64Dependencies(jPackageConfig)
    }

    // Debian/Ubuntu 24.04 renamed many libraries during the 64-bit time_t transition,
    // appending a `t64` suffix (e.g. libasound2 -> libasound2t64). jpackage inherits
    // whatever names dpkg-shlibdeps picks up on the build host, so a .deb produced on
    // a t64-transitioned system pins `libasound2t64` and refuses to install on Ubuntu
    // 22.04 where only the un-suffixed name exists. Rewrite the Depends field so each
    // `<pkg>t64` entry becomes `<pkg>t64 | <pkg>`, satisfying both old and new releases.
    fun relaxDebT64Dependencies(jPackageConfig: JPackageConfig) {
        val debPath = PackageFactory.findSinglePackageArtifact(PackageFormat.DEB, jPackageConfig)
        val repackRoot = jPackageConfig.temporaryDirPath.resolve("deb-repack")
        val repackRootFile = repackRoot.toFile()
        if (repackRootFile.exists() && !repackRootFile.deleteRecursively()) {
            throw GradleException("Couldn't delete DEB repack directory: ${repackRoot.toAbsolutePath()}")
        }

        val payloadRoot = repackRoot.resolve("payload")
        Files.createDirectories(repackRoot)

        PackageFactory.runProcess(
            ProcessBuilder(
                "dpkg-deb", "-R",
                debPath.toAbsolutePath().toString(),
                payloadRoot.toAbsolutePath().toString()
            ),
            "extract DEB payload"
        )

        val controlFile = payloadRoot.resolve("DEBIAN").resolve("control")
        if (!Files.isRegularFile(controlFile)) {
            throw GradleException("DEB control file not found after extraction: ${controlFile.toAbsolutePath()}")
        }
        val original = Files.readString(controlFile)
        val updated = relaxT64Dependencies(original)
        if (updated == original) {
            return
        }
        Files.writeString(controlFile, updated)

        val rebuiltDebPath = repackRoot.resolve(debPath.fileName)
        PackageFactory.runProcess(
            ProcessBuilder(
                "dpkg-deb",
                "--root-owner-group",
                "--build",
                payloadRoot.toAbsolutePath().toString(),
                rebuiltDebPath.toAbsolutePath().toString()
            ),
            "rebuild DEB with relaxed dependencies"
        )

        Files.move(rebuiltDebPath, debPath, StandardCopyOption.REPLACE_EXISTING)
        PackageFactory.normalizePathTimestamps(debPath)
    }


    private fun relaxT64Dependencies(controlFileContent: String): String {
        val lines = controlFileContent.split("\n")
        val result = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("Depends:", ignoreCase = true)) {
                val colonIdx = line.indexOf(':')
                val deps = StringBuilder(line.substring(colonIdx + 1).trim())
                var j = i + 1
                while (j < lines.size && lines[j].isNotEmpty() && (lines[j][0] == ' ' || lines[j][0] == '\t')) {
                    if (deps.isNotEmpty()) deps.append(' ')
                    deps.append(lines[j].trim())
                    j++
                }
                result.append(line.substring(0, colonIdx + 1))
                    .append(' ')
                    .append(relaxDepsList(deps.toString()))
                i = j
            } else {
                result.append(line)
                i++
            }
            if (i < lines.size) {
                result.append('\n')
            }
        }
        return result.toString()
    }

    private fun relaxDepsList(deps: String): String {
        if (deps.isBlank()) return deps
        return deps.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ") { relaxT64Dependency(it) }
    }

    private fun relaxT64Dependency(dep: String): String {
        if (dep.contains("|")) {
            return dep
        }
        val nameEndIdx = dep.indexOfAny(charArrayOf(' ', '\t', ':', '('))
        val pkgName = if (nameEndIdx == -1) dep else dep.substring(0, nameEndIdx)
        if (!pkgName.endsWith("t64") || pkgName.length <= 3) {
            return dep
        }
        val baseName = pkgName.dropLast(3)
        val suffix = dep.removePrefix(pkgName)
        return "$dep | $baseName$suffix"
    }
}
