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

package io.bisq.core.app;

import io.bisq.common.UserThread;
import io.bisq.common.crypto.CryptoException;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.crypto.SealedAndSigned;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.core.btc.BaseCurrencyNetwork;
import io.bisq.network.crypto.DecryptedDataTuple;
import io.bisq.network.crypto.EncryptionService;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.peers.keepalive.messages.Ping;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.extern.slf4j.Slf4j;

import java.security.Security;
import java.util.Date;
import java.util.function.Consumer;

@Slf4j
public class SetupUtils {

    public static void checkCryptoSetup(KeyRing keyRing, EncryptionService encryptionService,
                                        ResultHandler resultHandler, Consumer<Throwable> errorHandler) {
        // We want to test if the client is compiled with the correct crypto provider (BountyCastle)
        // and if the unlimited Strength for cryptographic keys is set.
        // If users compile themselves they might miss that step and then would get an exception in the trade.
        // To avoid that we add here at startup a sample encryption and signing to see if it don't causes an exception.
        // See: https://github.com/bisq-network/exchange/blob/master/doc/build.md#7-enable-unlimited-strength-for-cryptographic-keys
        Thread checkCryptoThread = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.currentThread().setName("checkCryptoThread");
                    log.trace("Run crypto test");
                    // just use any simple dummy msg
                    Ping payload = new Ping(1, 1);
                    SealedAndSigned sealedAndSigned = EncryptionService.encryptHybridWithSignature(payload,
                            keyRing.getSignatureKeyPair(), keyRing.getPubKeyRing().getEncryptionPubKey());
                    DecryptedDataTuple tuple = encryptionService.decryptHybridWithSignature(sealedAndSigned,
                            keyRing.getEncryptionKeyPair().getPrivate());
                    if (tuple.getNetworkEnvelope() instanceof Ping &&
                            ((Ping) tuple.getNetworkEnvelope()).getNonce() == payload.getNonce() &&
                            ((Ping) tuple.getNetworkEnvelope()).getLastRoundTripTime() == payload.getLastRoundTripTime()) {
                        log.debug("Crypto test succeeded");

                        if (Security.getProvider("BC") != null) {
                            UserThread.execute(resultHandler::handleResult);
                        } else {
                            errorHandler.accept(new CryptoException("Security provider BountyCastle is not available."));
                        }
                    } else {
                        errorHandler.accept(new CryptoException("Payload not correct after decryption"));
                    }
                } catch (CryptoException e) {
                    log.error(e.toString());
                    e.printStackTrace();
                    errorHandler.accept(e);
                }
            }
        };
        checkCryptoThread.start();
    }

    public static BooleanProperty readFromResources(P2PService p2PService) {
        BooleanProperty result = new SimpleBooleanProperty();
        Thread thread = new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("readFromResourcesThread");
                // Used to load different files per base currency (EntryMap_BTC_MAINNET, EntryMap_LTC,...)
                final BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();
                final String storageFileName = "PersistableNetworkPayloadMap_"
                        + baseCurrencyNetwork.getCurrencyCode() + "_"
                        + baseCurrencyNetwork.getNetwork();
                long ts = new Date().getTime();
                p2PService.readFromResources(storageFileName);
                log.info("readPersistableNetworkPayloadMapFromResources took {} ms", (new Date().getTime() - ts));
                UserThread.execute(() -> result.set(true));
            }
        };
        thread.start();
        return result;
    }
}
