package bisq.gradle.tasks.signature

import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.jcajce.JcaPGPPublicKeyRingCollection
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider
import org.bouncycastle.util.encoders.Hex
import org.gradle.api.GradleException
import java.io.ByteArrayInputStream
import java.net.URL

class PpgPublicKeyParser(
    private val primaryKeyFingerprint: String,
    private val keyUrl: URL
) {

    private var masterKey: PGPPublicKey? = null
    private val subKeys: MutableList<PGPPublicKey> = ArrayList()

    val keyById: Map<Long, PGPPublicKey>
        get() {
            val keyByIdMap = HashMap<Long, PGPPublicKey>()
            keyByIdMap[masterKey!!.keyID] = masterKey!!
            subKeys.forEach { key -> keyByIdMap[key.keyID] = key }
            return keyByIdMap
        }

    fun parse() {
        val publicKey: ByteArray = keyUrl.readBytes()
        val byteArrayInputStream = ByteArrayInputStream(publicKey)
        PGPUtil.getDecoderStream(byteArrayInputStream)
            .use { decoderInputStream ->
                val publicKeyRingCollection = JcaPGPPublicKeyRingCollection(decoderInputStream)
                parseMasterAndSubKeys(publicKeyRingCollection)

                checkNotNull(masterKey) { "Couldn't find master key." }
                verifyMasterKeyFingerprint()

                if (subKeys.isNotEmpty()) {
                    verifySubKeySignatures()
                }
            }
    }

    private fun parseMasterAndSubKeys(publicKeyRingCollection: JcaPGPPublicKeyRingCollection) {
        val rIt: Iterator<PGPPublicKeyRing> = publicKeyRingCollection.keyRings
        while (rIt.hasNext()) {
            val kRing = rIt.next()
            val kIt = kRing.publicKeys
            while (kIt.hasNext()) {
                val k = kIt.next()

                if (k.isMasterKey) {
                    if (masterKey == null) {
                        masterKey = k
                    } else {
                        throw GradleException("Found multiple find master keys.")
                    }

                } else {
                    subKeys.add(k)
                }
            }
        }
    }

    private fun verifyMasterKeyFingerprint() {
        val fingerprint = Hex.toHexString(masterKey!!.fingerprint)
        if (fingerprint != primaryKeyFingerprint) {
            throw GradleException("$keyUrl has invalid fingerprint.")
        }
    }

    private fun verifySubKeySignatures() {
        subKeys.forEach { subKey ->
            var hasValidSignature = false
            subKey.keySignatures.forEach { signature ->
                signature.init(
                    JcaPGPContentVerifierBuilderProvider().setProvider("BC"),
                    masterKey!!
                )
                val isSubKeySignedByMasterKey = signature.verifyCertification(masterKey!!, subKey)

                if (isSubKeySignedByMasterKey) {
                    hasValidSignature = true
                }
            }

            if (!hasValidSignature) {
                throw GradleException("Subkey `$subKey` is not signed by masterkey `$masterKey`")
            }
        }
    }
}