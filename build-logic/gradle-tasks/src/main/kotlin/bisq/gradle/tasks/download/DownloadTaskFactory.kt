package bisq.gradle.tasks.download

import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import java.net.URL

class DownloadTaskFactory(
    private val project: Project, private val downloadDirectoryPath: String
) {
    fun registerDownloadTask(taskName: String, url: Provider<URL>): TaskProvider<DownloadTask> {
        val outputFileProvider: Provider<Provider<RegularFile>> = url.map {
            // url.file:
            // https://example.org/1.2.3/binary.exe -> 1.2.3/binary.exe
            val fileName = it.file.split("/").last()
            project.layout.buildDirectory.file("$downloadDirectoryPath/$fileName")
        }
        return project.tasks.register<DownloadTask>(taskName) {
            downloadUrl.set(url)
            outputFile.set(outputFileProvider)
        }
    }
}