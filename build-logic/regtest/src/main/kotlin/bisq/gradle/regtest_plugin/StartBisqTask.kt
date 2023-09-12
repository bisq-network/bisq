package bisq.gradle.regtest_plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

abstract class StartBisqTask : DefaultTask() {

    @get:InputDirectory
    abstract val workingDirectory: DirectoryProperty

    @get:InputFile
    abstract val javaExecutable: RegularFileProperty

    @get:InputDirectory
    abstract val libsDir: DirectoryProperty

    @get:Input
    abstract val mainClass: Property<String>

    @get:Input
    abstract val arguments: ListProperty<String>

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
            javaExecutable.asFile.get().absolutePath,

            "-XX:MaxRAM=8g",
            "-Xss1280k",
            "-XX:+UseG1GC",
            "-XX:MaxHeapFreeRatio=10",
            "-XX:MinHeapFreeRatio=5",
            "-XX:+UseStringDeduplication",
            "-Djava.net.preferIPv4Stack=true",

            "-classpath", createClassPath(),
            mainClass.get(),
        )

        processBuilder.command()
            .addAll(arguments.get())

        processBuilder.directory(workingDirectory.asFile.get())
        processBuilder.redirectErrorStream(true)
        processBuilder.redirectOutput(logFile.asFile.get())

        val process = processBuilder.start()
        val pid = process.pid()

        pidFile.asFile
            .get()
            .writeText(pid.toString())
    }

    private fun createClassPath(): String {
        val libsDirFile = libsDir.asFile.get()
        return libsDirFile.listFiles()!!.joinToString(separator = ":") { it.absolutePath }
    }
}
