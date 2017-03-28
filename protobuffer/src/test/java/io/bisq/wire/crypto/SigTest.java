package io.bisq.wire.crypto;

import io.bisq.common.crypto.CryptoException;
import io.bisq.common.crypto.Sig;
import io.bisq.common.storage.FileUtil;
import io.bisq.protobuffer.crypto.KeyRing;
import io.bisq.protobuffer.crypto.KeyStorage;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.util.Random;

import static org.junit.Assert.assertTrue;

public class SigTest {
    private static final Logger log = LoggerFactory.getLogger(SigTest.class);
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


    @Test
    public void testSignature() {
        long ts = System.currentTimeMillis();
        log.trace("start ");
        for (int i = 0; i < 100; i++) {
            String msg = String.valueOf(new Random().nextInt());
            String sig = null;
            try {
                sig = Sig.sign(keyRing.getSignatureKeyPair().getPrivate(), msg);
            } catch (CryptoException e) {
                log.error("sign failed");
                e.printStackTrace();
                assertTrue(false);
            }
            try {
                assertTrue(Sig.verify(keyRing.getSignatureKeyPair().getPublic(), msg, sig));
            } catch (CryptoException e) {
                log.error("verify failed");
                e.printStackTrace();
                assertTrue(false);
            }
        }
        log.trace("took " + (System.currentTimeMillis() - ts) + " ms.");
    }
}


