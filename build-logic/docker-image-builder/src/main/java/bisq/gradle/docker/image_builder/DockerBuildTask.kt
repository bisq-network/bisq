package bisq.gradle.docker.image_builder

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.concurrent.TimeUnit

abstract class DockerBuildTask : DefaultTask() {

    @get:InputDirectory
    abstract val dockerDirectory: Property<File>

    @TaskAction
    fun build() {
        val processBuilder = ProcessBuilder(
            "docker", "build",
            "--tag", "bisq/seednode:latest",
            dockerDirectory.get().absolutePath
        )

        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()

        val isSuccess = process.waitFor(2, TimeUnit.MINUTES) && process.exitValue() == 0
        if (!isSuccess) {
            throw IllegalStateException("Couldn't build docker image.")
        }
    }
}
