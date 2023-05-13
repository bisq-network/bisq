package bisq.gradle.tor_binary

import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.register

class TorBinaryPackager(private val project: Project) {

    companion object {
        private const val ARCHIVE_EXTRACTION_DIR = "${BisqTorBinaryPlugin.DOWNLOADS_DIR}/extracted"
        private const val PROCESSED_DIR = "${BisqTorBinaryPlugin.DOWNLOADS_DIR}/processed"
    }

    fun registerTasks(tarFile: Provider<Property<Provider<RegularFile>>>) {
        val unpackTarTask: TaskProvider<Copy> = project.tasks.register<Copy>("unpackTorBinaryTar") {
            from(
                tarFile.map {
                    project.tarTree(it.get().get().asFile.absolutePath)
                }
            )

            into(project.layout.buildDirectory.dir(ARCHIVE_EXTRACTION_DIR))
        }

        val processUnpackedTorBinaryTar: TaskProvider<Copy> =
            project.tasks.register<Copy>("processUnpackedTorBinaryTar") {
                dependsOn(unpackTarTask)

                from(project.layout.buildDirectory.dir("${ARCHIVE_EXTRACTION_DIR}/data"))
                from(project.layout.buildDirectory.dir("${ARCHIVE_EXTRACTION_DIR}/tor"))
                into(project.layout.buildDirectory.dir(PROCESSED_DIR))
            }

        val packageTorBinary: TaskProvider<Zip> =
            project.tasks.register<Zip>("packageTorBinary") {
                dependsOn(processUnpackedTorBinaryTar)

                archiveFileName.set("tor.zip")
                destinationDirectory.set(project.layout.buildDirectory.dir("generated/src/main/resources"))
                from(project.layout.buildDirectory.dir(PROCESSED_DIR))
            }

        val processResourcesTask = project.tasks.named("processResources")
        processResourcesTask.configure {
            dependsOn(packageTorBinary)
        }
    }
}