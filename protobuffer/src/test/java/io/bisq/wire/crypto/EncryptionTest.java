package io.bisq.wire.crypto;

import io.bisq.common.crypto.CryptoException;
import io.bisq.common.storage.FileUtil;
import io.bisq.protobuffer.crypto.KeyRing;
import io.bisq.protobuffer.crypto.KeyStorage;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;

public class EncryptionTest {
    private static final Logger log = LoggerFactory.getLogger(EncryptionTest.class);
    private KeyRing keyRing;
    private File dir;

    @Before
    public void setup() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, CryptoException {
        Security.addProvider(new BouncyCastleProvider());
        dir = File.createTempFile("temp_tests", "");
        dir.delete();
        dir.mkdir();
        KeyStorage keyStorage = new KeyStorage(dir);
        keyRing = new KeyRing(keyStorage);
    }

    @After
    public void tearDown() throws IOException {
        FileUtil.deleteDirectory(dir);
    }


}


