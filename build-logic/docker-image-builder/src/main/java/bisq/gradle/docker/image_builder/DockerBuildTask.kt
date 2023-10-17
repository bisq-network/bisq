package bisq.gradle.docker.image_builder

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

abstract class DockerBuildTask : DefaultTask() {

    @get:InputDirectory
    abstract val dockerDirectory: DirectoryProperty

    @get:Input
    abstract val imageTag: Property<String>

    @TaskAction
    fun build() {
        val processBuilder = ProcessBuilder(
            "docker", "build",
            "--tag", imageTag.get(),
            dockerDirectory.asFile.get().absolutePath
        )

        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()

        // Report Progress
        process.inputStream
            .bufferedReader()
            .use { bufferedReader ->
                while (true) {
                    val line = bufferedReader.readLine() ?: return@use
                    println(line)
                }
            }

        val isSuccess = process.waitFor() == 0
        if (!isSuccess) {
            throw IllegalStateException("Couldn't build docker image.")
        }
    }
}
