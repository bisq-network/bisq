package bisq.gradle.packaging

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.util.concurrent.TimeUnit

abstract class JLinkTask : DefaultTask() {

    @get:InputDirectory
    abstract val jdkDirectory: DirectoryProperty

    @get:InputDirectory
    abstract val javaModulesDirectory: DirectoryProperty

    @get:InputFile
    abstract val jDepsOutputFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        // jlink expects non-existent output directory
        val outputDirectoryFile = outputDirectory.asFile.get()
        outputDirectoryFile.deleteRecursively()

        val jLinkPath = jdkDirectory.asFile.get().toPath().resolve("bin").resolve("jlink")
        val processBuilder = ProcessBuilder(
                jLinkPath.toAbsolutePath().toString(),

                "--module-path", javaModulesDirectory.asFile.get().absolutePath,
                "--add-modules", parseUsedJavaModulesFromJDepsOutput(),

                "--strip-native-commands",
                "--no-header-files",
                "--no-man-pages",
                "--strip-debug",

                "--output", outputDirectoryFile.absolutePath
        )
        processBuilder.inheritIO()

        val process = processBuilder.start()
        process.waitFor(2, TimeUnit.MINUTES)

        val isSuccess = process.exitValue() == 0
        if (!isSuccess) {
            throw IllegalStateException("jlink couldn't create custom runtime.")
        }
    }

    private fun parseUsedJavaModulesFromJDepsOutput(): String {
        val readLines = jDepsOutputFile.asFile.get().readLines()
        return readLines.joinToString(",")
    }
}
