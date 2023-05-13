package bisq.gradle.tasks.download

import bisq.gradle.tasks.PerOsUrlProvider
import bisq.gradle.tasks.signature.SignatureVerificationTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import java.net.URL

class SignedBinaryDownloader(
    private val project: Project,
    private val binaryName: String,
    private val version: Property<String>,

    private val perOsUrlProvider: (String) -> PerOsUrlProvider,
    private val downloadDirectory: String,

    private val pgpFingerprintToKeyUrlMap: Map<String, URL>
) {
    lateinit var verifySignatureTask: TaskProvider<SignatureVerificationTask>
    private val downloadTaskFactory = DownloadTaskFactory(project, downloadDirectory)

    fun registerTasks() {
        val binaryDownloadUrl: Provider<URL> = version.map { perOsUrlProvider(it).url }
        val binaryDownloadTask =
            downloadTaskFactory.registerDownloadTask("download${binaryName}Binary", binaryDownloadUrl)

        val signatureDownloadUrl: Provider<URL> = binaryDownloadUrl.map { URL("$it.asc") }
        val signatureDownloadTask =
            downloadTaskFactory.registerDownloadTask("download${binaryName}Signature", signatureDownloadUrl)

        verifySignatureTask = project.tasks.register<SignatureVerificationTask>("verify${binaryName}Binary") {
            dependsOn(binaryDownloadTask)

            fileToVerify.set(binaryDownloadTask.flatMap { it.outputFile })
            detachedSignatureFile.set(signatureDownloadTask.flatMap { it.outputFile })
            pgpFingerprintToKeyUrl.set(pgpFingerprintToKeyUrlMap)

            resultFile.set(project.layout.buildDirectory.file("${downloadDirectory}/sha256.result"))
        }
    }
}