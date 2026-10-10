package bisq.gradle.packaging

import bisq.gradle.packaging.jpackage.package_formats.PackageFormat
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import java.io.File
import javax.inject.Inject

@Suppress("unused")
class PackagingPlugin @Inject constructor(private val javaToolchainService: JavaToolchainService) : Plugin<Project> {

    companion object {
        const val APP_VERSION = "1.10.9"
    }

    override fun apply(project: Project) {
        val installDistTask: TaskProvider<Sync> = project.tasks.named("installDist", Sync::class.java)
        val jarTask: TaskProvider<Jar> = project.tasks.named("jar", Jar::class.java)

        val javaApplicationExtension = project.extensions.findByType<JavaApplication>()
        checkNotNull(javaApplicationExtension) { "Can't find JavaApplication extension." }

        val jPackageTaskConfiguration = jPackageTaskConfiguration(installDistTask, jarTask, javaApplicationExtension)

        project.tasks.register<JPackageTask>("generateInstallers", jPackageTaskConfiguration)
        val debTask = project.tasks.register<DebJpackageTask>("deb", jPackageTaskConfiguration)
        val rpmTask = project.tasks.register<RpmJpackageTask>("rpm", jPackageTaskConfiguration)

        val verifyDebTask = registerJavaRuntimeCheck(project, "verifyDebJavaRuntime", PackageFormat.DEB, debTask)
        registerJavaRuntimeCheck(project, "verifyRpmJavaRuntime", PackageFormat.RPM, rpmTask)
        // rpm deletes every installer artifact before it builds, so the DEB check has to run first
        rpmTask.configure { mustRunAfter(verifyDebTask) }

        project.tasks.register<DebReproducibleTask>("reproducibleDeb") {
            appVersion.set(APP_VERSION)
            distDirFile.set(installDistTask.map { it.destinationDir })
            outputDirectory.set(project.layout.buildDirectory.dir("packaging"))
        }

    }

    private fun registerJavaRuntimeCheck(
        project: Project,
        name: String,
        format: PackageFormat,
        packageTask: TaskProvider<out JPackageTask>
    ): TaskProvider<VerifyPackagedJavaRuntimeTask> {
        val check = project.tasks.register<VerifyPackagedJavaRuntimeTask>(name) {
            group = "verification"
            description = "Verifies that the Java runtime in the ${format.name} package matches releaseBuild.javaVersion."
            packageFormat.set(format)
            expectedJavaVersion.set(project.providers.gradleProperty("releaseBuild.javaVersion"))
            packagingDirectory.set(project.layout.buildDirectory.dir("packaging"))
            // a failed package task skips the check instead of adding a second, misleading error
            dependsOn(packageTask)
        }
        packageTask.configure { finalizedBy(check) }
        return check
    }

    private fun jPackageTaskConfiguration(
        installDistTask: TaskProvider<Sync>,
        jarTask: TaskProvider<Jar>,
        javaApplicationExtension: JavaApplication
    ): JPackageTask.() -> Unit = {
        jdkDirectory.set(getProjectJdkDirectory(project))
        distDirFile.set(installDistTask.map { it.destinationDir })
        mainJarFile.set(jarTask.flatMap { it.archiveFile })

        mainClassName.set(javaApplicationExtension.mainClass)
        jvmArgs.set(javaApplicationExtension.applicationDefaultJvmArgs)
        appVersion.set(APP_VERSION)

        val packageResourcesDirFile = File(project.projectDir, "package")
        packageResourcesDir.set(packageResourcesDirFile)

        outputDirectory.set(project.layout.buildDirectory.dir("packaging"))
    }

    private fun getProjectJdkDirectory(project: Project): Provider<Directory> {
        val javaExtension = project.extensions.findByType<JavaPluginExtension>()
        checkNotNull(javaExtension) { "Can't find JavaPluginExtension extension." }

        val toolchain = javaExtension.toolchain
        val projectLauncherProvider = javaToolchainService.launcherFor(toolchain)
        return projectLauncherProvider.map { it.metadata.installationPath }
    }
}
