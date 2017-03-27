package io.bisq.common.crypto;

import io.bisq.common.storage.FileUtil;
import io.bisq.vo.crypto.KeyRingVO;
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
    private KeyRingVO keyRingVO;
    private File dir;

    @Before
    public void setup() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, CryptoException {
        Security.addProvider(new BouncyCastleProvider());
        dir = File.createTempFile("temp_tests", "");
        dir.delete();
        dir.mkdir();
        KeyStorage keyStorage = new KeyStorage(dir);
        keyRingVO = new KeyRingVO(keyStorage);
    }

    @After
    public void tearDown() throws IOException {
        FileUtil.deleteDirectory(dir);
    }


}


