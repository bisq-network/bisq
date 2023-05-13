package bisq.gradle.tasks.signature

import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.security.Security

class SignatureVerifier(
    private val pgpFingerprintToKeyUrl: Map<String, URL>
) {

    fun verifySignature(
        fileToVerify: File,
        signatureFile: File,
    ): Boolean {
        Security.addProvider(BouncyCastleProvider())

        var isSuccess = true
        pgpFingerprintToKeyUrl.forEach { (fingerprint, keyUrl) ->
            val ppgPublicKeyParser = PpgPublicKeyParser(fingerprint, keyUrl)
            ppgPublicKeyParser.parse()

            val isSignedByAnyKey = verifyDetachedSignature(
                potentialSigningKeys = ppgPublicKeyParser.keyById,
                pgpSignatureByteArray = readSignatureFromFile(signatureFile),
                data = fileToVerify.readBytes()
            )

            isSuccess = isSuccess && isSignedByAnyKey
        }

        return isSuccess
    }

    private fun readSignatureFromFile(signatureFile: File): ByteArray {
        val signatureByteArray = signatureFile.readBytes()
        val signatureInputStream = ByteArrayInputStream(signatureByteArray)
        val armoredSignatureInputStream = ArmoredInputStream(signatureInputStream)
        return armoredSignatureInputStream.readBytes()
    }

    private fun verifyDetachedSignature(
        potentialSigningKeys: Map<Long, PGPPublicKey>,
        pgpSignatureByteArray: ByteArray,
        data: ByteArray
    ): Boolean {
        val pgpObjectFactory = JcaPGPObjectFactory(pgpSignatureByteArray)
        val signatureList: PGPSignatureList = pgpObjectFactory.nextObject() as PGPSignatureList

        var pgpSignature: PGPSignature? = null
        var signingKey: PGPPublicKey? = null

        for (s in signatureList) {
            signingKey = potentialSigningKeys[s.keyID]
            if (signingKey != null) {
                pgpSignature = s
                break
            }
        }

        checkNotNull(signingKey) { "signingKey not found" }
        checkNotNull(pgpSignature) { "signature for key not found" }

        pgpSignature.init(
            JcaPGPContentVerifierBuilderProvider().setProvider("BC"),
            signingKey
        )
        pgpSignature.update(data)
        return pgpSignature.verify()
    }
}