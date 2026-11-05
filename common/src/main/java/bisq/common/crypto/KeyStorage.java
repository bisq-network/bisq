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

import bisq.common.config.Config;
import bisq.common.file.FileUtil;

import com.google.inject.Inject;

import javax.inject.Named;
import javax.inject.Singleton;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.math.BigInteger;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.NotNull;

import static bisq.common.util.Preconditions.checkDir;

@Singleton
public class KeyStorage {
    private static final Logger log = LoggerFactory.getLogger(KeyStorage.class);

    public enum KeyEntry {
        MSG_SIGNATURE("sig", Sig.KEY_ALGO),
        MSG_ENCRYPTION("enc", Encryption.ASYM_KEY_ALGO);

        private final String fileName;
        private final String algorithm;

        KeyEntry(String fileName, String algorithm) {
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

    // On a file system with POSIX permissions only the owner may read the private keys, as for the key of the onion
    // service. The directory is owner-only too: it also holds the rolling backups of the keys, which
    // FileUtil.rollingBackup copies with the default mode. A key ring of an earlier version gets the modes when it is
    // loaded. Windows keeps its access control lists.
    private static final boolean SUPPORTS_POSIX_PERMISSIONS =
            FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    private final File storageDir;

    @Inject
    public KeyStorage(@Named(Config.KEY_STORAGE_DIR) File storageDir) {
        this.storageDir = checkDir(storageDir);
    }

    public boolean allKeyFilesExist() {
        return fileExists(KeyEntry.MSG_SIGNATURE) && fileExists(KeyEntry.MSG_ENCRYPTION);
    }

    private boolean fileExists(KeyEntry keyEntry) {
        return getKeyFile(keyEntry.getFileName()).exists();
    }

    public KeyPair loadKeyPair(KeyEntry keyEntry) {
        restrictToOwner(storageDir.toPath(), "rwx------");
        restrictToOwner(getKeyFile(keyEntry.getFileName()).toPath(), "rw-------");
        FileUtil.rollingBackup(storageDir, keyEntry.getFileName() + ".key", 20);
        // long now = System.currentTimeMillis();
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(keyEntry.getAlgorithm());
            PublicKey publicKey;
            PrivateKey privateKey;

            File filePrivateKey = getKeyFile(keyEntry.getFileName());
            try (FileInputStream fis = new FileInputStream(filePrivateKey.getPath())) {
                byte[] encodedPrivateKey = new byte[(int) filePrivateKey.length()];
                //noinspection ResultOfMethodCallIgnored
                fis.read(encodedPrivateKey);

                PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(encodedPrivateKey);
                privateKey = keyFactory.generatePrivate(privateKeySpec);
            } catch (InvalidKeySpecException | IOException e) {
                log.error("Could not load key " + keyEntry.toString(), e.getMessage());
                throw new RuntimeException("Could not load key " + keyEntry.toString(), e);
            }

            if (privateKey instanceof RSAPrivateCrtKey) {
                RSAPrivateCrtKey rsaPrivateKey = (RSAPrivateCrtKey) privateKey;
                RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(rsaPrivateKey.getModulus(), rsaPrivateKey.getPublicExponent());
                publicKey = keyFactory.generatePublic(publicKeySpec);
            } else if (privateKey instanceof DSAPrivateKey) {
                DSAPrivateKey dsaPrivateKey = (DSAPrivateKey) privateKey;
                DSAParams dsaParams = dsaPrivateKey.getParams();
                BigInteger p = dsaParams.getP();
                BigInteger q = dsaParams.getQ();
                BigInteger g = dsaParams.getG();
                BigInteger y = g.modPow(dsaPrivateKey.getX(), p);
                KeySpec publicKeySpec = new DSAPublicKeySpec(y, p, q, g);
                publicKey = keyFactory.generatePublic(publicKeySpec);
            } else {
                throw new RuntimeException("Unsupported key algo" + keyEntry.getAlgorithm());
            }

            log.debug("load completed in {} msec", System.currentTimeMillis() - new Date().getTime());
            return new KeyPair(publicKey, privateKey);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.error("Could not load key " + keyEntry.toString(), e);
            throw new RuntimeException("Could not load key " + keyEntry.toString(), e);
        }
    }

    public void saveKeyRing(KeyRing keyRing) {
        savePrivateKey(keyRing.getSignatureKeyPair().getPrivate(), KeyEntry.MSG_SIGNATURE.getFileName());
        savePrivateKey(keyRing.getEncryptionKeyPair().getPrivate(), KeyEntry.MSG_ENCRYPTION.getFileName());
    }

    private void savePrivateKey(PrivateKey privateKey, String name) {
        if (!storageDir.exists())
            //noinspection ResultOfMethodCallIgnored
            storageDir.mkdir();
        // Before the key is written, so that no other user can open the new file before it is owner-only
        restrictToOwner(storageDir.toPath(), "rwx------");

        File keyFile = getKeyFile(name);
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
        try (FileOutputStream fos = new FileOutputStream(keyFile)) {
            fos.write(pkcs8EncodedKeySpec.getEncoded());
        } catch (IOException e) {
            log.error("Could not save key " + name, e);
            throw new RuntimeException("Could not save key " + name, e);
        }
        restrictToOwner(keyFile.toPath(), "rw-------");
    }

    private File getKeyFile(String name) {
        return new File(storageDir, name + ".key");
    }

    // The keys work without it, as before, for example in a data directory which another user owns
    private static void restrictToOwner(Path path, String permissions) {
        if (!SUPPORTS_POSIX_PERMISSIONS) {
            return;
        }
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        } catch (IOException | UnsupportedOperationException e) {
            log.warn("Could not make {} accessible to its owner only: {}", path, e.toString());
        }
    }
}
