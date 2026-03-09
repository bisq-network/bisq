package bisq.gradle.app_start_plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.toolchain.*
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import java.io.File
import javax.inject.Inject

class AppStartPlugin @Inject constructor(private val javaToolchainService: JavaToolchainService) : Plugin<Project> {

    override fun apply(project: Project) {
        val installDistTask: TaskProvider<Sync> = project.tasks.named("installDist", Sync::class.java)

        val javaApplicationExtension = project.extensions.findByType<JavaApplication>()
        checkNotNull(javaApplicationExtension) { "Can't find JavaApplication extension." }

        project.tasks.register<JavaExec>("startBisqApp") {
            dependsOn(installDistTask)
            javaLauncher.set(getJavaLauncher(project))

            classpath = installDistTask.map {
                val appLibsDir = File(it.destinationDir, "lib")
                val allFiles = appLibsDir.listFiles()
                project.files(allFiles)
            }.get()

            jvmArgs.addAll(javaApplicationExtension.applicationDefaultJvmArgs)

            workingDir = project.projectDir.parentFile
            mainClass.set(javaApplicationExtension.mainClass)
        }
    }

    private fun getJavaLauncher(project: Project): Provider<JavaLauncher> {
        val javaExtension = project.extensions.findByType<JavaPluginExtension>()
        checkNotNull(javaExtension) { "Can't find JavaPluginExtension extension." }

        val toolchain = javaExtension.toolchain
        return javaToolchainService.launcherFor(toolchain)
    }
}
