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

import bisq.common.UserThread;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.wallet.Protos;

import org.bouncycastle.crypto.params.KeyParameter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO: Borrowed form BitcoinJ/Lighthouse. Remove Protos dependency, check complete code logic.
public class ScryptUtil {
    private static final Logger log = LoggerFactory.getLogger(ScryptUtil.class);

    public interface DeriveKeyResultHandler {
        void handleResult(KeyParameter aesKey);
    }

    public static KeyCrypterScrypt getKeyCrypterScrypt() {
        Protos.ScryptParameters scryptParameters = Protos.ScryptParameters.newBuilder()
                .setP(6)
                .setR(8)
                .setN(32768)
                .setSalt(ByteString.copyFrom(KeyCrypterScrypt.randomSalt()))
                .build();
        return new KeyCrypterScrypt(scryptParameters);
    }

    public static void deriveKeyWithScrypt(KeyCrypterScrypt keyCrypterScrypt, String password, DeriveKeyResultHandler resultHandler) {
        Utilities.getThreadPoolExecutor("ScryptUtil:deriveKeyWithScrypt-%d", 1, 2, 5L).submit(() -> {
            try {
                log.debug("Doing key derivation");
                long start = System.currentTimeMillis();
                KeyParameter aesKey = keyCrypterScrypt.deriveKey(password);
                long duration = System.currentTimeMillis() - start;
                log.debug("Key derivation took {} msec", duration);
                UserThread.execute(() -> {
                    try {
                        resultHandler.handleResult(aesKey);
                    } catch (Throwable t) {
                        t.printStackTrace();
                        log.error("Executing task failed. " + t.getMessage());
                        throw t;
                    }
                });
            } catch (Throwable t) {
                t.printStackTrace();
                log.error("Executing task failed. " + t.getMessage());
                throw t;
            }
        });
    }
}
