package bisq.gradle.tasks.download

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels

abstract class DownloadTask : DefaultTask() {

    @get:Input
    abstract val downloadUrl: Property<URL>

    @get:OutputFile
    abstract val outputFile: Property<Provider<RegularFile>>

    @TaskAction
    fun download() {
        downloadFile()
    }

    private fun downloadFile() {
        val url = downloadUrl.get()
        url.openStream().use { inputStream ->
            Channels.newChannel(inputStream).use { readableByteChannel ->
                println("Downloading: $url")

                FileOutputStream(outputFile.get().get().asFile).use { fileOutputStream ->
                    fileOutputStream.channel
                        .transferFrom(readableByteChannel, 0, Long.MAX_VALUE)
                }
            }
        }
    }
}