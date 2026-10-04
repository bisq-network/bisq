package bisq.gradle.packaging

import bisq.gradle.packaging.jpackage.package_formats.PackageFormat
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@DisableCachingByDefault(because = "Reading the release file of a locally built package is cheaper than fetching the result from a cache")
abstract class VerifyPackagedJavaRuntimeTask : DefaultTask() {

    companion object {
        // The jlink step of jpackage writes JAVA_VERSION and MODULES into the release file of the bundled runtime.
        private const val RUNTIME_RELEASE_PATH = "./opt/bisq/lib/runtime/release"
        private const val JAVA_VERSION_PREFIX = "JAVA_VERSION="
        private const val TIMEOUT_MINUTES = 15L
    }

    @get:Input
    abstract val packageFormat: Property<PackageFormat>

    @get:Input
    abstract val expectedJavaVersion: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val packageFiles: ConfigurableFileCollection

    // Only for the error message when the package task did not leave a package.
    @get:Internal
    abstract val packageFileName: Property<String>

    @get:Internal
    abstract val packageTaskPath: Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val format = packageFormat.get()
        val expected = expectedJavaVersion.get()
        val packageFile = findSinglePackage()

        val releaseFile = File(temporaryDir, "release")
        val errorFile = File(temporaryDir, "extraction-errors")
        errorFile.delete()
        val exitCode = extractRuntimeReleaseFile(format, packageFile, releaseFile, errorFile)

        val javaVersionLine = releaseFile.readLines().find { it.startsWith(JAVA_VERSION_PREFIX) }
        if (exitCode != 0 || javaVersionLine == null) {
            val errors = if (errorFile.exists()) errorFile.readText().trim() else ""
            throw GradleException("${packageFile.name}: could not read $RUNTIME_RELEASE_PATH: $errors")
        }

        val actual = javaVersionLine.removePrefix(JAVA_VERSION_PREFIX).replace("\"", "")
        if (actual != expected) {
            throw GradleException(
                "Packaged Java runtime verification failed:\n" +
                        "  - ${packageFile.name}: Java version expected $expected but was $actual\n" +
                        "Build the package with the Java $expected toolchain, or pass " +
                        "-PreleaseBuild.javaVersion=$actual for a build that is not a release."
            )
        }

        val report = reportFile.get().asFile
        report.parentFile.mkdirs()
        report.writeText("${packageFile.name}: Java $actual\n")
        logger.lifecycle("Verified packaged Java runtime in ${packageFile.name}: Java $actual.")
    }

    // dpkg-deb and rpm2cpio write the package payload to stdout, tar and cpio read it from stdin.
    // startPipeline connects the two without a shell, so no argument needs quoting.
    private fun extractRuntimeReleaseFile(
        format: PackageFormat,
        packageFile: File,
        releaseFile: File,
        errorFile: File
    ): Int {
        val stages = when (format) {
            PackageFormat.DEB -> listOf(
                ProcessBuilder("dpkg-deb", "--fsys-tarfile", packageFile.absolutePath),
                ProcessBuilder("tar", "-xOf", "-", RUNTIME_RELEASE_PATH)
            )

            PackageFormat.RPM -> listOf(
                ProcessBuilder("rpm2cpio", packageFile.absolutePath),
                ProcessBuilder("cpio", "-i", "--quiet", "--to-stdout", RUNTIME_RELEASE_PATH)
            )

            else -> throw GradleException("Only DEB and RPM packages are supported, not $format.")
        }
        stages.forEach { it.redirectError(ProcessBuilder.Redirect.appendTo(errorFile)) }
        stages.last().redirectOutput(releaseFile)

        val tools = stages.joinToString(", ") { it.command().first() }
        val processes = try {
            ProcessBuilder.startPipeline(stages)
        } catch (e: IOException) {
            throw GradleException(
                "${packageFile.name}: could not start the tools which read $RUNTIME_RELEASE_PATH ($tools): " +
                        "${e.message}. Install the missing tool.", e
            )
        }
        var exitCode = 0
        processes.forEachIndexed { index, process ->
            if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                processes.forEach { it.destroyForcibly() }
                throw GradleException(
                    "Reading $RUNTIME_RELEASE_PATH from ${packageFile.name} timed out after $TIMEOUT_MINUTES minutes: " +
                            stages[index].command().joinToString(" ")
                )
            }
            if (process.exitValue() != 0) {
                exitCode = process.exitValue()
            }
        }
        return exitCode
    }

    private fun findSinglePackage(): File {
        val packages = packageFiles.files.filter { it.isFile }
        if (packages.isEmpty()) {
            throw GradleException(
                "No package matching ${packageFileName.get()} found; " +
                        "if ${packageTaskPath.get()} failed, see its error above."
            )
        }
        if (packages.size != 1) {
            throw GradleException("Expected exactly one jpackage package, found ${packages.size}: " +
                    packages.joinToString(", ") { it.name })
        }
        return packages.single()
    }
}
