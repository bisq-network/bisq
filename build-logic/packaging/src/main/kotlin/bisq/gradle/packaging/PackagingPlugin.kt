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
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.util.PatternSet
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import java.io.File
import javax.inject.Inject

@Suppress("unused")
class PackagingPlugin @Inject constructor(private val javaToolchainService: JavaToolchainService) : Plugin<Project> {

    companion object {
        const val APP_VERSION = "1.10.10"
    }

    override fun apply(project: Project) {
        val installDistTask: TaskProvider<Sync> = project.tasks.named("installDist", Sync::class.java)
        val jarTask: TaskProvider<Jar> = project.tasks.named("jar", Jar::class.java)

        val javaApplicationExtension = project.extensions.findByType<JavaApplication>()
        checkNotNull(javaApplicationExtension) { "Can't find JavaApplication extension." }

        val jPackageTaskConfiguration = jPackageTaskConfiguration(installDistTask, jarTask, javaApplicationExtension)

        val debTask = project.tasks.register<DebJpackageTask>("deb", jPackageTaskConfiguration)
        val rpmTask = project.tasks.register<RpmJpackageTask>("rpm", jPackageTaskConfiguration)
        val verifyDebTask = registerJavaRuntimeCheck(
            project, "verifyDebJavaRuntime", PackageFormat.DEB, debTask, "bisq_$APP_VERSION-1_*.deb"
        )
        val verifyRpmTask = registerJavaRuntimeCheck(
            project, "verifyRpmJavaRuntime", PackageFormat.RPM, rpmTask, "bisq-$APP_VERSION-1.*.rpm"
        )
        if (getOS() == OS.LINUX) {
            // On Linux the deb and rpm tasks build the packages, so generateInstallers runs both
            // and the checks of their Java runtime
            project.tasks.register("generateInstallers") {
                group = "distribution"
                description = "Builds the DEB and RPM packages."
                dependsOn(debTask, rpmTask, verifyDebTask, verifyRpmTask)
            }
        } else {
            project.tasks.register<JPackageTask>("generateInstallers", jPackageTaskConfiguration)
        }
        // deb and rpm both declare build/packaging as their output directory, and the DEB check reads
        // from it, so Gradle needs the check ordered before rpm writes into that directory.
        rpmTask.configure { mustRunAfter(verifyDebTask) }

        project.tasks.register<DebReproducibleTask>("reproducibleDeb") {
            appVersion.set(APP_VERSION)
            distDirFile.set(installDistTask.map { it.destinationDir })
            outputDirectory.set(project.layout.buildDirectory.dir("packaging"))
            // the reproducible tasks write into the directory which both checks read
            mustRunAfter(verifyDebTask, verifyRpmTask)
        }

        val distTarTask: TaskProvider<Tar> = project.tasks.named("distTar", Tar::class.java)
        project.tasks.register<RpmReproducibleTask>("reproducibleRpm") {
            appVersion.set(APP_VERSION)
            tarballPath.set(distTarTask.flatMap { it.archiveFile })
            distDirFile.set(installDistTask.map { it.destinationDir })
            outputDirectory.set(project.layout.buildDirectory.dir("packaging"))
            mustRunAfter(verifyDebTask, verifyRpmTask)
        }
    }

    private fun registerJavaRuntimeCheck(
        project: Project,
        name: String,
        format: PackageFormat,
        packageTask: TaskProvider<out JPackageTask>,
        jPackageFileName: String
    ): TaskProvider<VerifyPackagedJavaRuntimeTask> {
        // The package built by packageTask is the only input, so the check re-runs when that package
        // changes and is UP-TO-DATE when it does not. Going through the task provider also carries
        // the dependency on packageTask, without the task having to know the packaging directory.
        // reproducibleDeb writes its package into the same directory, so the file name selects the
        // one which jpackage named.
        val packagePattern = PatternSet().include(jPackageFileName)
        val packageProvider = packageTask.map { task -> task.outputDirectory.asFileTree.matching(packagePattern) }
        val check = project.tasks.register<VerifyPackagedJavaRuntimeTask>(name) {
            group = "verification"
            description = "Verifies that the Java runtime in the ${format.name} package matches releaseBuild.javaVersion."
            packageFormat.set(format)
            expectedJavaVersion.set(project.providers.gradleProperty("releaseBuild.javaVersion"))
            packageFiles.from(packageProvider)
            packageFileName.set(jPackageFileName)
            packageTaskPath.set(packageTask.map { it.path })
            reportFile.set(project.layout.buildDirectory.file("reports/packaging/$name.txt"))
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
