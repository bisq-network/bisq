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
import bisq.common.file.FileUtil;

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.After;
import org.junit.Before;

public class EncryptionTest {
    private static final Logger log = LoggerFactory.getLogger(EncryptionTest.class);
    private KeyRing keyRing;
    private File dir;

    @Before
    public void setup() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, CryptoException {

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


}
