package bisq.gradle.regtest_plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class StartSeedNodeTask : DefaultTask() {

    @get:InputFile
    abstract val startScriptFile: RegularFileProperty

    @get:Input
    abstract val arguments: ListProperty<String>

    @get:InputDirectory
    abstract val workingDirectory: DirectoryProperty

    @get:Internal
    abstract val logFile: RegularFileProperty

    @TaskAction
    fun run() {
        val processBuilder = ProcessBuilder(
                "bash", startScriptFile.asFile.get().absolutePath, arguments.get().joinToString(" ")
        )

        processBuilder.directory(workingDirectory.asFile.get())
        processBuilder.redirectErrorStream(true)
        processBuilder.redirectOutput(logFile.asFile.get())

        processBuilder.start()
    }
}
