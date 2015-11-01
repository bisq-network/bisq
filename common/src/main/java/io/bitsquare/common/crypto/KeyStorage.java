/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.common.crypto;

import com.google.inject.Inject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class KeyStorage {
    private static final Logger log = LoggerFactory.getLogger(KeyStorage.class);

    public static final String DIR_KEY = "key.storage.dir";

    public enum Key {
        STORAGE_SIGNATURE("storageSignature", Sig.KEY_ALGO),
        MSG_SIGNATURE("msgSignature", Sig.KEY_ALGO),
        MSG_ENCRYPTION("msgEncryption", Encryption.ENCR_KEY_ALGO);

        private final String fileName;
        private final String algorithm;

        Key(String fileName, String algorithm) {
            this.fileName = fileName;
            this.algorithm = algorithm;
        }

        public String getFileName() {
            return fileName;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        @NotNull
        @Override
        public String toString() {
            return "Key{" +
                    "fileName='" + fileName + '\'' +
                    ", algorithm='" + algorithm + '\'' +
                    '}';
        }
    }

    private final File storageDir;

    @Inject
    public KeyStorage(@Named(DIR_KEY) File storageDir) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        this.storageDir = storageDir;
    }

    public boolean allKeyFilesExist() {
        return fileExists(KeyStorage.Key.STORAGE_SIGNATURE) && fileExists(KeyStorage.Key.MSG_SIGNATURE) && fileExists(KeyStorage.Key.MSG_ENCRYPTION);
    }

    private boolean fileExists(Key key) {
        return new File(storageDir + "/" + key.getFileName() + "Pub.key").exists();
    }

    public KeyPair loadKeyPair(Key key) {
        // long now = System.currentTimeMillis();
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(key.getAlgorithm());
            PublicKey publicKey;
            PrivateKey privateKey;

            File filePublicKey = new File(storageDir + "/" + key.getFileName() + "Pub.key");
            try (FileInputStream fis = new FileInputStream(filePublicKey.getPath())) {
                byte[] encodedPublicKey = new byte[(int) filePublicKey.length()];
                fis.read(encodedPublicKey);

                X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(encodedPublicKey);
                publicKey = keyFactory.generatePublic(publicKeySpec);
            } catch (InvalidKeySpecException | IOException e) {
                e.printStackTrace();
                log.error(e.getMessage());
                throw new RuntimeException("Could not load key " + key.toString(), e);
            }

            File filePrivateKey = new File(storageDir + "/" + key.getFileName() + "Priv.key");
            try (FileInputStream fis = new FileInputStream(filePrivateKey.getPath())) {
                byte[] encodedPrivateKey = new byte[(int) filePrivateKey.length()];
                fis.read(encodedPrivateKey);

                PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(encodedPrivateKey);
                privateKey = keyFactory.generatePrivate(privateKeySpec);
            } catch (InvalidKeySpecException | IOException e) {
                e.printStackTrace();
                log.error(e.getMessage());
                throw new RuntimeException("Could not load key " + key.toString(), e);
            }
            //log.info("load completed in {} msec", System.currentTimeMillis() - now);
            return new KeyPair(publicKey, privateKey);

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            throw new RuntimeException("Could not load key " + key.toString(), e);
        }
    }

    public void saveKeyRing(KeyRing keyRing) {
        saveKeyPair(keyRing.getStorageSignatureKeyPair(), Key.STORAGE_SIGNATURE.getFileName());
        saveKeyPair(keyRing.getSignatureKeyPair(), Key.MSG_SIGNATURE.getFileName());
        saveKeyPair(keyRing.getEncryptionKeyPair(), Key.MSG_ENCRYPTION.getFileName());
    }

    public void saveKeyPair(KeyPair keyPair, String name) {
        if (!storageDir.exists())
            storageDir.mkdir();

        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(keyPair.getPublic().getEncoded());
        try (FileOutputStream fos = new FileOutputStream(storageDir + "/" + name + "Pub.key")) {
            fos.write(x509EncodedKeySpec.getEncoded());
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            throw new RuntimeException("Could not save key " + name, e);
        }

        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(keyPair.getPrivate().getEncoded());
        try (FileOutputStream fos = new FileOutputStream(storageDir + "/" + name + "Priv.key")) {
            fos.write(pkcs8EncodedKeySpec.getEncoded());
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            throw new RuntimeException("Could not save key " + name, e);
        }
    }
}
