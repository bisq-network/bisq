package bisq.gradle.packaging

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.util.concurrent.TimeUnit

abstract class JPackageTask : DefaultTask() {

    @get:InputDirectory
    abstract val jdkDirectory: DirectoryProperty

    @get:InputDirectory
    abstract val jarsDirectory: DirectoryProperty

    @get:Input
    abstract val mainJar: Property<String>

    @get:Input
    abstract val mainClassName: Property<String>

    @get:InputDirectory
    abstract val runtimeImageDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val jPackagePath = jdkDirectory.asFile.get().toPath().resolve("bin").resolve("jpackage")
        val processBuilder = ProcessBuilder(
                jPackagePath.toAbsolutePath().toString(),
                "--dest", outputDirectory.asFile.get().absolutePath,
                "--name", "Bisq",

                "--input", jarsDirectory.asFile.get().absolutePath,
                "--main-jar", mainJar.get(),
                "--main-class", mainClassName.get(),

                "--runtime-image", runtimeImageDirectory.asFile.get().absolutePath
        )

        processBuilder.inheritIO()
        val process = processBuilder.start()
        process.waitFor(15, TimeUnit.MINUTES)
    }
}
