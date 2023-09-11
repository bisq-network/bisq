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

abstract class StartBisqTask : DefaultTask() {

    @get:InputFile
    abstract val startScriptFile: RegularFileProperty

    @get:Input
    abstract val arguments: ListProperty<String>

    @get:InputDirectory
    abstract val workingDirectory: DirectoryProperty

    @get:Internal
    abstract val logFile: RegularFileProperty

    @get:Internal
    abstract val pidFile: RegularFileProperty

    @TaskAction
    fun run() {
        ProcessKiller(pidFile.asFile.get())
                .kill()

        // Wait until process stopped
        Thread.sleep(5000)

        val processBuilder = ProcessBuilder(
                "bash", startScriptFile.asFile.get().absolutePath, arguments.get().joinToString(" ")
        )

        processBuilder.directory(workingDirectory.asFile.get())
        processBuilder.redirectErrorStream(true)
        processBuilder.redirectOutput(logFile.asFile.get())

        val process = processBuilder.start()
        val pid = process.pid()

        pidFile.asFile
                .get()
                .writeText(pid.toString())
    }
}
