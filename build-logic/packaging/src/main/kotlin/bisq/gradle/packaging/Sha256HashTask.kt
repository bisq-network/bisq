package bisq.gradle.packaging

import org.apache.commons.codec.binary.Hex
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest

abstract class Sha256HashTask : DefaultTask() {

    @get:InputDirectory
    abstract val inputDirFile: Property<File>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val fileHashes = inputDirFile.get().listFiles()!!
            .filter { it.isFile }
            .sortedBy { it.name }
            .map { file ->
                Pair(file.name, sha256(file))
            }

        // linux:file_name:sha256_hash
        val osName = getOsName()
        val lines = fileHashes.map { nameAndHash -> "$osName:${nameAndHash.first}:${nameAndHash.second}" }

        outputFile.asFile.get()
            .writeText(lines.joinToString(separator = "\n") + "\n")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return Hex.encodeHexString(digest.digest())
    }

    private fun getOsName(): String =
        when (getOS()) {
            OS.LINUX -> "linux"
            OS.MAC_OS -> "macOS"
            OS.WINDOWS -> "windows"
        }
}
