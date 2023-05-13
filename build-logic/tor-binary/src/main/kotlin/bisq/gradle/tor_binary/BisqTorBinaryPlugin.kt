package bisq.gradle.tor_binary

import bisq.gradle.tasks.PgpFingerprint
import bisq.gradle.tasks.download.SignedBinaryDownloader
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.create

class BisqTorBinaryPlugin : Plugin<Project> {
    companion object {
        const val DOWNLOADS_DIR = "tor_binary/downloads"
    }

    override fun apply(project: Project) {
        val extension = project.extensions.create<BisqTorBinaryPluginExtension>("tor")

        val torBinaryDownloader = SignedBinaryDownloader(
            project = project,
            binaryName = "Tor",
            version = extension.version,

            perOsUrlProvider = { version -> TorBinaryUrlProvider(version) },
            downloadDirectory = DOWNLOADS_DIR,

            pgpFingerprintToKeyUrlMap = mapOf(
                Pair(
                    PgpFingerprint.normalize("EF6E 286D DA85 EA2A 4BA7  DE68 4E2C 6E87 9329 8290"),
                    this::class.java.getResource("/Tor_Browser_Developers_(signing_key).asc")!!,
                )
            )
        )
        torBinaryDownloader.registerTasks()

        val binaryTarFile: Provider<Property<Provider<RegularFile>>> =
            torBinaryDownloader.verifySignatureTask.map { it.fileToVerify }
        val torBinaryPackager = TorBinaryPackager(project)
        torBinaryPackager.registerTasks(binaryTarFile)
    }
}