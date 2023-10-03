package bisq.gradle.docker.image_builder

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Tar
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import java.io.File

class DockerImageBuilderPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val javaApplicationExtension = project.extensions.findByType<JavaApplication>()
        checkNotNull(javaApplicationExtension) { "Can't find JavaApplication extension." }

        val installDistTask: TaskProvider<Sync> = project.tasks.named("installDist", Sync::class.java)
        val distTarTask: TaskProvider<Tar> = project.tasks.named("distTar", Tar::class.java)

        val copyTask = project.tasks.register<Copy>("copyDistTar") {
            from(distTarTask.flatMap { it.archiveFile })
            into(project.layout.buildDirectory.dir("docker"))
        }

        project.tasks.register<CreateDockerfileTask>("generateDockerfile") {
            archiveFileName.set(distTarTask.flatMap { it.archiveFileName })

            val classpathFiles: Provider<List<String>> = installDistTask.map { syncTask ->
                val appLibsDir = File(syncTask.destinationDir, "lib")
                appLibsDir.listFiles()!!.map { it.name }
            }
            classpathFileNames.set(classpathFiles)

            mainClassName.set(javaApplicationExtension.mainClass)
            outputFile.set(project.layout.buildDirectory.file("docker/Dockerfile"))
        }

        project.tasks.register<DockerBuildTask>("dockerImage") {
            dockerDirectory.set(copyTask.map { it.destinationDir })
        }
    }
}
