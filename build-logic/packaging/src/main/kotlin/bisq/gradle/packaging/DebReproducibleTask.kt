package bisq.gradle.packaging

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class DebReproducibleTask : DefaultTask() {
    @get:Input
    abstract val appVersion: Property<String>

    @get:InputDirectory
    abstract val distDirFile: Property<File>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val debPackager = bisq.deb_packager.DebPackager(
            appVersion.get(),
            distDirFile.get().toPath(),
            outputDirectory.asFile.get().toPath()
        );
        debPackager.createPackage();
    }
}
