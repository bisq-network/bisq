package bisq.gradle.packaging

import org.gradle.api.Plugin
import org.gradle.api.Project
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
import javax.inject.Inject

class PackagingPlugin @Inject constructor(private val javaToolchainService: JavaToolchainService) : Plugin<Project> {

    companion object {
        const val APP_VERSION = "1.9.14"
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
            jvmArgs.set(javaApplicationExtension.applicationDefaultJvmArgs)

            appVersion.set(APP_VERSION)

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
        val javaVersion = if (getOS() == OS.MAC_OS) 15 else 17
        val launcherProvider = javaToolchainService.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(javaVersion))
            vendor.set(JvmVendorSpec.AZUL)
            implementation.set(JvmImplementation.VENDOR_SPECIFIC)
        }
        return launcherProvider.map { it.metadata.installationPath }
    }
}
