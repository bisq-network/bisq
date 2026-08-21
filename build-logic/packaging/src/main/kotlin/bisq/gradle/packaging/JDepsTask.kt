package bisq.gradle.packaging

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit


abstract class JDepsTask : DefaultTask() {

    @get:InputDirectory
    abstract val jdkDirectory: DirectoryProperty

    @get:InputDirectory
    abstract val javaModulesDirectory: DirectoryProperty

    @get:InputFile
    abstract val jarFileToAnalyze: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val process = createAndRunJDepsProcess()
        val futureTask: FutureTask<Set<String>> = createAndStartParseModulesNamesFutureTask(process)

        process.waitFor(1, TimeUnit.MINUTES)
        val moduleNames: Set<String> = futureTask.get(10, TimeUnit.SECONDS)

        val concatenatedModuleNames = moduleNames.joinToString(separator = "\n")
        outputFile.asFile.get().writeText(concatenatedModuleNames)
    }

    private fun createAndRunJDepsProcess(): Process {
        val jDepsPath = jdkDirectory.asFile.get().toPath().resolve("bin").resolve("jdeps")
        val processBuilder = ProcessBuilder(
                jDepsPath.toAbsolutePath().toString(),
                "--module-path", javaModulesDirectory.asFile.get().absolutePath,
                "-s", jarFileToAnalyze.asFile.get().absolutePath
        )

        processBuilder.redirectErrorStream(true)
        return processBuilder.start()
    }

    private fun createAndStartParseModulesNamesFutureTask(process: Process): FutureTask<Set<String>> {
        val futureTask: FutureTask<Set<String>> = FutureTask(Callable { parseModuleNamesFromProcessOutput(process) })
        val parseThread = Thread(futureTask)
        parseThread.start()
        return futureTask
    }

    private fun parseModuleNamesFromProcessOutput(process: Process): Set<String> {
        val moduleNames = mutableSetOf<String>()

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line: String? = reader.readLine()

            while (line != null) {
                parseModuleNamesFromLine(line).forEach { moduleNames.add(it) }
                line = reader.readLine()
            }
        }
        return moduleNames
    }

    // Example: javafx.fxml -> java.base
    private fun parseModuleNamesFromLine(line: String) =
            line.split(" ")
                    .filter { it != "->" }
}
