package bisq.gradle.docker.image_builder

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Tar
import org.gradle.kotlin.dsl.register

class DockerImageBuilderPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val distTarTask: TaskProvider<Tar> = project.tasks.named("distTar", Tar::class.java)

        val copyTask = project.tasks.register<Copy>("copyDistTar") {
            from(distTarTask.flatMap { it.archiveFile })
            into(project.layout.buildDirectory.dir("docker"))
        }

        project.tasks.register<CreateDockerfileTask>("generateDockerfile") {
            archiveFileName.set(distTarTask.flatMap { it.archiveFileName })
            outputFile.set(project.layout.buildDirectory.file("docker/Dockerfile"))
        }

        project.tasks.register<DockerBuildTask>("dockerImage") {
            dockerDirectory.set(copyTask.map { it.destinationDir })
        }
    }
}
