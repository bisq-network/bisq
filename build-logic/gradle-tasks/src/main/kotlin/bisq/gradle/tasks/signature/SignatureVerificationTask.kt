package bisq.gradle.tasks.signature

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.URL

abstract class SignatureVerificationTask : DefaultTask() {

    @get:InputFile
    abstract val fileToVerify: Property<Provider<RegularFile>>

    @get:InputFile
    abstract val detachedSignatureFile: Property<Provider<RegularFile>>

    @get:Input
    abstract val pgpFingerprintToKeyUrl: MapProperty<String, URL>

    @get:OutputFile
    abstract val resultFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val signatureVerifier = SignatureVerifier(pgpFingerprintToKeyUrl.get())
        val isSignatureValid = signatureVerifier.verifySignature(
            signatureFile = detachedSignatureFile.get().get().asFile,
            fileToVerify = fileToVerify.get().get().asFile
        )

        resultFile.get().asFile.writeText("$isSignatureValid")

        if (!isSignatureValid) {
            throw GradleException(
                "Signature verification failed for ${fileToVerify.get().get().asFile.absolutePath}."
            )
        }
    }
}