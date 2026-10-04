package bisq.gradle.packaging

import bisq.gradle.packaging.jpackage.PackageFactory
import bisq.gradle.packaging.jpackage.package_formats.PackageFormat
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.concurrent.TimeUnit

@DisableCachingByDefault(because = "Reads the package which the packaging task has just built")
abstract class VerifyPackagedJavaRuntimeTask : DefaultTask() {

    companion object {
        // The jlink step of jpackage writes JAVA_VERSION and MODULES into the release file of the bundled runtime.
        private const val RUNTIME_RELEASE_PATH = "./opt/bisq/lib/runtime/release"
    }

    @get:Input
    abstract val packageFormat: Property<PackageFormat>

    @get:Input
    abstract val expectedJavaVersion: Property<String>

    @get:Internal
    abstract val packagingDirectory: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun verify() {
        val format = packageFormat.get()
        val expected = expectedJavaVersion.get()
        val packageFile = findSinglePackage(packagingDirectory.get().asFile, format)
        val packagePath = PackageFactory.shellSingleQuote(packageFile.absolutePath)
        val extractCommand = when (format) {
            PackageFormat.DEB -> "dpkg-deb --fsys-tarfile $packagePath | tar -xOf - $RUNTIME_RELEASE_PATH"
            PackageFormat.RPM -> "rpm2cpio $packagePath | cpio -i --quiet --to-stdout $RUNTIME_RELEASE_PATH"
            else -> throw GradleException("Only DEB and RPM packages are supported, not $format.")
        }

        val outputFile = File(temporaryDir, "release")
        val process = ProcessBuilder("sh", "-c", extractCommand)
            .redirectErrorStream(true)
            .redirectOutput(outputFile)
            .start()
        if (!process.waitFor(15, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw GradleException("Reading $RUNTIME_RELEASE_PATH from ${packageFile.name} timed out after 15 minutes.")
        }
        val output = outputFile.readText()
        val javaVersionLine = output.lines().find { it.startsWith("JAVA_VERSION=") }
        if (process.exitValue() != 0 || javaVersionLine == null) {
            throw GradleException("${packageFile.name}: could not read JAVA_VERSION from $RUNTIME_RELEASE_PATH: ${output.trim()}")
        }

        val actual = javaVersionLine.removePrefix("JAVA_VERSION=").replace("\"", "")
        if (actual != expected) {
            throw GradleException(
                "Packaged Java runtime verification failed:\n" +
                        "  - ${packageFile.name}: Java version expected $expected but was $actual\n" +
                        "Build the package with the Java $expected toolchain, or pass " +
                        "-PreleaseBuild.javaVersion=$actual for a build that is not a release."
            )
        }
        logger.lifecycle("Verified packaged Java runtime in ${packageFile.name}: Java $actual.")
    }

    private fun findSinglePackage(directory: File, format: PackageFormat): File {
        val extension = ".${format.fileExtension}"
        val packages = directory.listFiles()?.filter { it.isFile && it.name.endsWith(extension) } ?: emptyList()
        if (packages.size != 1) {
            throw GradleException("Expected exactly one $extension package in $directory, found ${packages.size}: " +
                    packages.joinToString(", ") { it.name })
        }
        return packages.single()
    }
}
