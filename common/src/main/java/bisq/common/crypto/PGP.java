/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.common.crypto;

import org.bouncycastle.bcpg.BCPGKey;
import org.bouncycastle.bcpg.RSAPublicBCPGKey;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPPublicKeyRingCollection;
import org.bouncycastle.util.encoders.Hex;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Iterator;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

@SuppressWarnings("UnusedAssignment")
@Slf4j
public class PGP {

    // TODO not tested yet, remove Nullable once impl.
    // PEM encoding
    @Nullable
    public static PGPPublicKey getPubKeyFromPem(@Nullable String pem) {
        if (pem != null) {
            InputStream inputStream = new ByteArrayInputStream(pem.getBytes());
            try {
                inputStream = PGPUtil.getDecoderStream(inputStream);
                try {
                    JcaPGPPublicKeyRingCollection ringCollection = new JcaPGPPublicKeyRingCollection(inputStream);
                    Iterator<PGPPublicKeyRing> keyRingsIterator = ringCollection.getKeyRings();
                    while (keyRingsIterator.hasNext()) {
                        PGPPublicKeyRing pgpPublicKeyRing = keyRingsIterator.next();
                        Iterator<PGPPublicKey> pubKeysIterator = pgpPublicKeyRing.getPublicKeys();
                        while (pubKeysIterator.hasNext()) {
                            final PGPPublicKey pgpPublicKey = pubKeysIterator.next();
                            if ((pgpPublicKey).isEncryptionKey()) {
                                log.debug(pgpPublicKey.getClass().getName()
                                        + " KeyID: " + Long.toHexString(pgpPublicKey.getKeyID())
                                        + " type: " + pgpPublicKey.getAlgorithm()
                                        + " fingerprint: " + new String(Hex.encode(pgpPublicKey.getFingerprint())));

                                BCPGKey bcKey = pgpPublicKey.getPublicKeyPacket().getKey();
                                log.debug(bcKey.getClass().getName());
                                if (bcKey instanceof RSAPublicBCPGKey) {
                                    RSAPublicBCPGKey bcRSA = (RSAPublicBCPGKey) bcKey;
                                    RSAPublicKeySpec specRSA = new RSAPublicKeySpec(bcRSA.getModulus(), bcRSA.getPublicExponent());
                                    PublicKey jceKey = KeyFactory.getInstance("RSA").generatePublic(specRSA);
                                    // if you want to use the key in JCE, use jceKey
                                    // if you want to write "X.509" (SPKI) DER format to a file:
                                    //Files.write(new File(pubKeyAsString).toPath(), jceKey.getEncoded());
                                    // if you want to write in PEM, bouncycastle can do that
                                    // or you can just do base64 and add BEGIN/END lines
                                    // return pubKeyAsString; // assume only one key; if need to handle multiple keys
                                    // or select other than the first, specify more clearly
                                }

                                return pgpPublicKey;
                            }
                        }
                    }
                    return null;
                } catch (PGPException | InvalidKeySpecException | NoSuchAlgorithmException e) {
                    log.error("Error creating publicKey from pem. pem={}, error={}", pem, e);
                    e.printStackTrace();
                    throw new KeyConversionException(e);
                }

            } catch (IOException e) {
                log.error("Error creating publicKey from pem. pem={}, error={}", pem, e);
                e.printStackTrace();
                throw new KeyConversionException(e);
            } finally {
                try {
                    inputStream.close();
                } catch (IOException ignore) {
                }
            }
        } else {
            log.warn("Error creating publicKey from pem. pem=null");
            return null;
        }
    }

    // TODO not impl, remove Nullable once impl.
    // PEM encoding
    @SuppressWarnings({"SameReturnValue", "UnusedParameters"})
    @NotNull
    public static String getPEMFromPubKey(@Nullable PGPPublicKey pgpPubKey) {
        // We use empty string as we must not have null in proto file
        return "";
    }

    // TODO not impl, remove Nullable once impl.
    @SuppressWarnings("SameReturnValue")
    @Nullable
    public static PGPKeyPair generateKeyPair() {
        return null;
    }
}
