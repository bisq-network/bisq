package bisq.gradle.packaging

import bisq.packager.rpm.SpecMetadata
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class RpmReproducibleTask : DefaultTask() {
    @get:Input
    abstract val appVersion: Property<String>

    @get:InputFile
    abstract val tarballPath: RegularFileProperty

    @get:InputDirectory
    abstract val distDirFile: Property<File>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val tarballFile = tarballPath.asFile.get()
        val specMetadata = SpecMetadata(
            tarballFile.nameWithoutExtension,
            appVersion.get(),
            "Decentralized peer-to-peer Bitcoin exchange network",
            tarballFile.name
        )

        val rpmPackager = bisq.packager.rpm.RpmPackager(
            outputDirectory.asFile.get().toPath(),
            tarballPath.asFile.get().toPath(),
            specMetadata,
            distDirFile.get().toPath()
        )
        rpmPackager.createPackage()
    }
}
