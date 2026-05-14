package bisq.gradle.packaging

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class PackagingPlugin @Inject constructor(private val javaToolchainService: JavaToolchainService) : Plugin<Project> {

    companion object {
        const val APP_VERSION = "1.10.0"
        const val OUTPUT_DIR_PATH = "packaging/jpackage/packages"
    }

    override fun apply(project: Project) {
        val installDistTask: TaskProvider<Sync> = project.tasks.named("installDist", Sync::class.java)

        val generateHashesTask = project.tasks.register<Sha256HashTask>("generateHashes") {
            inputDirFile.set(installDistTask.map { File(it.destinationDir, "lib") })
            outputFile.set(getHashFileForOs(project))
        }

        val jarTask: TaskProvider<Jar> = project.tasks.named("jar", Jar::class.java)

        val javaApplicationExtension = project.extensions.findByType<JavaApplication>()
        checkNotNull(javaApplicationExtension) { "Can't find JavaApplication extension." }

        project.tasks.register<JPackageTask>("generateInstallers") {
            dependsOn(generateHashesTask)

            jdkDirectory.set(getJPackageJdkDirectory())

            distDirFile.set(installDistTask.map { it.destinationDir })
            mainJarFile.set(jarTask.flatMap { it.archiveFile })

            mainClassName.set(javaApplicationExtension.mainClass)
            jvmArgs.set(project.provider { javaApplicationExtension.applicationDefaultJvmArgs.toList() })

            appVersion.set(APP_VERSION)
            val sourceDateEpochOverride = project.providers.environmentVariable("SOURCE_DATE_EPOCH")
            sourceDateEpochSeconds.set(project.provider {
                releaseSourceDateEpochSeconds(project.rootDir, sourceDateEpochOverride.orNull)
            })

            val packageResourcesDirFile = File(project.projectDir, "package")
            packageResourcesDir.set(packageResourcesDirFile)

            runtimeImageDirectory.set(
                if (getOS() == OS.MAC_OS) getJPackageJdkDirectory()
                else getProjectJdkDirectory(project)
            )

            outputDirectory.set(project.layout.buildDirectory.dir("packaging/jpackage/packages"))
        }
    }

    private fun getHashFileForOs(project: Project): Provider<RegularFile> {
        val osName = when (getOS()) {
            OS.LINUX -> "linux"
            OS.MAC_OS -> "mac"
            OS.WINDOWS -> "win"
        }

        return project.layout.buildDirectory.file("$OUTPUT_DIR_PATH/desktop-$APP_VERSION-all-$osName.jar.SHA-256")
    }

    private fun getProjectJdkDirectory(project: Project): Provider<Directory> {
        val javaExtension = project.extensions.findByType<JavaPluginExtension>()
        checkNotNull(javaExtension) { "Can't find JavaPluginExtension extension." }

        val toolchain = javaExtension.toolchain
        val projectLauncherProvider = javaToolchainService.launcherFor(toolchain)
        return projectLauncherProvider.map { it.metadata.installationPath }
    }

    private fun getJPackageJdkDirectory(): Provider<Directory> {
        val launcherProvider = javaToolchainService.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.AZUL)
            implementation.set(JvmImplementation.VENDOR_SPECIFIC)
        }
        return launcherProvider.map { it.metadata.installationPath }
    }

    private fun releaseSourceDateEpochSeconds(rootDir: File, sourceDateEpochOverride: String?): Long {
        val environmentValue = sourceDateEpochOverride?.trim()
        if (!environmentValue.isNullOrEmpty()) {
            val parsed = environmentValue.toLongOrNull()
                    ?: throw GradleException("SOURCE_DATE_EPOCH must be an integer Unix timestamp: ${environmentValue}")
            if (parsed > 0) {
                return parsed
            }
        }

        val process = ProcessBuilder(
                "git",
                "-C", rootDir.absolutePath,
                "log",
                "-1",
                "--format=%ct",
                "HEAD"
        )
                .redirectErrorStream(true)
                .start()

        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw GradleException("Timed out while reading Git commit timestamp for SOURCE_DATE_EPOCH")
        }

        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (process.exitValue() != 0) {
            throw GradleException("Failed to read Git commit timestamp for SOURCE_DATE_EPOCH: ${output}")
        }

        return output.toLongOrNull()?.takeIf { it > 0 }
                ?: throw GradleException("Git returned an invalid commit timestamp for SOURCE_DATE_EPOCH: ${output}")
    }
}
