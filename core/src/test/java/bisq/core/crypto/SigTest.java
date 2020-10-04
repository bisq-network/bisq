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

package bisq.core.crypto;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.crypto.Sig;
import bisq.common.file.FileUtil;

import java.io.File;
import java.io.IOException;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class SigTest {
    private static final Logger log = LoggerFactory.getLogger(SigTest.class);
    private KeyRing keyRing;
    private File dir;

    @Before
    public void setup() throws IOException {

        dir = File.createTempFile("temp_tests", "");
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
        //noinspection ResultOfMethodCallIgnored
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
        log.trace("took {} ms.", System.currentTimeMillis() - ts);
    }
}


