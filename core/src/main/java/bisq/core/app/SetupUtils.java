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

package bisq.core.app;

import bisq.core.btc.BaseCurrencyNetwork;

import bisq.network.crypto.DecryptedDataTuple;
import bisq.network.crypto.EncryptionService;
import bisq.network.p2p.peers.keepalive.messages.Ping;
import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.UserThread;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.SealedAndSigned;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.ProtobufferException;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.Date;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

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

                        UserThread.execute(resultHandler::handleResult);
                    } else {
                        errorHandler.accept(new CryptoException("Payload not correct after decryption"));
                    }
                } catch (CryptoException | ProtobufferException e) {
                    log.error(e.toString());
                    e.printStackTrace();
                    errorHandler.accept(e);
                }
            }
        };
        checkCryptoThread.start();
    }

    public static BooleanProperty readFromResources(P2PDataStorage p2PDataStorage) {
        BooleanProperty result = new SimpleBooleanProperty();
        Thread thread = new Thread(() -> {
            Thread.currentThread().setName("readFromResourcesThread");
            // Used to load different files per base currency (EntryMap_BTC_MAINNET, EntryMap_LTC,...)
            final BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();
            final String postFix = "_" + baseCurrencyNetwork.name();
            long ts = new Date().getTime();
            p2PDataStorage.readFromResources(postFix);
            log.info("readFromResources took {} ms", (new Date().getTime() - ts));
            UserThread.execute(() -> result.set(true));
        });
        thread.start();
        return result;
    }
}
