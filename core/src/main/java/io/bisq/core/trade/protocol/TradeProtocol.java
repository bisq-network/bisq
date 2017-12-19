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

package io.bisq.core.trade.protocol;

import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.core.trade.MakerTrade;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.trade.messages.TradeMessage;
import io.bisq.network.p2p.DecryptedDirectMessageListener;
import io.bisq.network.p2p.DecryptedMessageWithPubKey;
import io.bisq.network.p2p.NodeAddress;
import javafx.beans.value.ChangeListener;
import lombok.extern.slf4j.Slf4j;

import java.security.PublicKey;

import static io.bisq.core.util.Validator.nonEmptyStringOf;

@Slf4j
public abstract class TradeProtocol {
    private static final long TIMEOUT = 120;

    protected final ProcessModel processModel;
    private final DecryptedDirectMessageListener decryptedDirectMessageListener;
    private final ChangeListener<Trade.State> stateChangeListener;
    protected Trade trade;
    private Timer timeoutTimer;

    public TradeProtocol(Trade trade) {
        this.trade = trade;
        this.processModel = trade.getProcessModel();

        decryptedDirectMessageListener = (decryptedMessageWithPubKey, peersNodeAddress) -> {
            // We check the sig only as soon we have stored the peers pubKeyRing.
            PubKeyRing tradingPeerPubKeyRing = processModel.getTradingPeer().getPubKeyRing();
            PublicKey signaturePubKey = decryptedMessageWithPubKey.getSignaturePubKey();
            if (tradingPeerPubKeyRing != null && signaturePubKey.equals(tradingPeerPubKeyRing.getSignaturePubKey())) {
                NetworkEnvelope networkEnvelop = decryptedMessageWithPubKey.getNetworkEnvelope();
                log.trace("handleNewMessage: message = " + networkEnvelop.getClass().getSimpleName() + " from " + peersNodeAddress);
                if (networkEnvelop instanceof TradeMessage) {
                    TradeMessage tradeMessage = (TradeMessage) networkEnvelop;
                    nonEmptyStringOf(tradeMessage.getTradeId());

                    if (tradeMessage.getTradeId().equals(processModel.getOfferId()))
                        doHandleDecryptedMessage(tradeMessage, peersNodeAddress);
                }
            }
        };
        processModel.getP2PService().addDecryptedDirectMessageListener(decryptedDirectMessageListener);

        stateChangeListener = (observable, oldValue, newValue) -> {
            if (newValue.getPhase() == Trade.Phase.TAKER_FEE_PUBLISHED && trade instanceof MakerTrade)
                processModel.getOpenOfferManager().closeOpenOffer(trade.getOffer());
        };
        trade.stateProperty().addListener(stateChangeListener);
    }

    public void completed() {
        cleanup();

        // We only removed earlier the listner here, but then we migth have dangling trades after faults...
        // so lets remove it at cleanup
        //processModel.getP2PService().removeDecryptedDirectMessageListener(decryptedDirectMessageListener);
    }

    private void cleanup() {
        log.debug("cleanup " + this);
        stopTimeout();
        trade.stateProperty().removeListener(stateChangeListener);
        // We removed that from here earlier as it broke the trade process in some non critical error cases.
        // But it should be actually removed...
        processModel.getP2PService().removeDecryptedDirectMessageListener(decryptedDirectMessageListener);
    }

    public void applyMailboxMessage(DecryptedMessageWithPubKey decryptedMessageWithPubKey, Trade trade) {
        log.debug("applyMailboxMessage " + decryptedMessageWithPubKey.getNetworkEnvelope());
        if (processModel.getTradingPeer().getPubKeyRing() != null &&
                decryptedMessageWithPubKey.getSignaturePubKey().equals(processModel.getTradingPeer().getPubKeyRing().getSignaturePubKey())) {
            processModel.setDecryptedMessageWithPubKey(decryptedMessageWithPubKey);
            doApplyMailboxMessage(decryptedMessageWithPubKey.getNetworkEnvelope(), trade);
        } else {
            log.error("SignaturePubKey in message does not match the SignaturePubKey we have stored to that trading peer.");
        }
    }

    protected abstract void doApplyMailboxMessage(NetworkEnvelope networkEnvelop, Trade trade);

    protected abstract void doHandleDecryptedMessage(TradeMessage tradeMessage, NodeAddress peerNodeAddress);

    protected void startTimeout() {
        stopTimeout();

        timeoutTimer = UserThread.runAfter(() -> {
            log.error("Timeout reached. TradeID={}, state={}", trade.getId(), trade.stateProperty().get());
            trade.setErrorMessage("A timeout occurred.");
            cleanupTradableOnFault();
            cleanup();
        }, TIMEOUT);
    }

    protected void stopTimeout() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }

    protected void handleTaskRunnerSuccess(String info) {
        log.debug("handleTaskRunnerSuccess " + info);
    }

    protected void handleTaskRunnerFault(String errorMessage) {
        log.error(errorMessage);
        cleanupTradableOnFault();
        cleanup();
    }

    private void cleanupTradableOnFault() {
        final Trade.State state = trade.getState();
        log.warn("cleanupTradableOnFault tradeState=" + state);
        TradeManager tradeManager = processModel.getTradeManager();
        if (trade.isInPreparation()) {
            // no funds left. we just clean up the trade list
            tradeManager.removePreparedTrade(trade);
        } else if (!trade.isFundsLockedIn()) {
            if (processModel.getPreparedDepositTx() == null) {
                if (trade.isTakerFeePublished())
                    tradeManager.addTradeToFailedTrades(trade);
                else
                    tradeManager.addTradeToClosedTrades(trade);
            } else {
                log.error("We have already sent the prepared deposit tx to the peer but we did not received the reply " +
                        "about the deposit tx nor saw it in the network. tradeId={}, tradeState={}", trade.getId(), trade.getState());
            }
        }
    }
}
