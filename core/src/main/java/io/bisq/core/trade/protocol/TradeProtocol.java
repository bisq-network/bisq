/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol;

import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.core.trade.MakerTrade;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.TradeManager;
import io.bisq.network.p2p.DecryptedDirectMessageListener;
import io.bisq.network.p2p.DecryptedMsgWithPubKey;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.message.trade.TradeMessage;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import io.bisq.vo.crypto.PubKeyRingVO;
import javafx.beans.value.ChangeListener;
import lombok.extern.slf4j.Slf4j;

import java.security.PublicKey;

import static io.bisq.core.util.Validator.nonEmptyStringOf;

@Slf4j
public abstract class TradeProtocol {
    private static final long TIMEOUT_SEC = 75;

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
            PubKeyRingVO tradingPeerPubKeyRingVO = processModel.tradingPeer.getPubKeyRing();
            PublicKey signaturePubKey = decryptedMessageWithPubKey.signaturePubKey;
            if (tradingPeerPubKeyRingVO != null && signaturePubKey.equals(tradingPeerPubKeyRingVO.getSignaturePubKey())) {
                Message message = decryptedMessageWithPubKey.message;
                log.trace("handleNewMessage: message = " + message.getClass().getSimpleName() + " from " + peersNodeAddress);
                if (message instanceof TradeMessage) {
                    TradeMessage tradeMessage = (TradeMessage) message;
                    nonEmptyStringOf(tradeMessage.tradeId);

                    if (tradeMessage.tradeId.equals(processModel.getId()))
                        doHandleDecryptedMessage(tradeMessage, peersNodeAddress);
                }
            } //else {
            //TODO not clear anymore what case is handled here
            // it might be that we received a msg from the arbitrator, we don't handle that here but we don't want to log an error
                /*Optional<Arbitrator> arbitratorOptional = processModel.getArbitratorManager().getArbitratorsObservableMap().values().stream()
                        .filter(e -> e.getArbitratorAddress().equals(trade.getArbitratorAddress())).findFirst();
                PubKeyRing arbitratorPubKeyRing = null;
                if (arbitratorOptional.isPresent())
                    arbitratorPubKeyRing = arbitratorOptional.get().getPubKeyRing();

                if ((arbitratorPubKeyRing != null && !signaturePubKey.equals(arbitratorPubKeyRing.getSignaturePubKey())))
                    log.error("Signature used in seal message does not match the one stored with that trade for the trading peer or arbitrator.");*/
            //}
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

    public void applyMailboxMessage(DecryptedMsgWithPubKey decryptedMsgWithPubKey, Trade trade) {
        log.debug("applyMailboxMessage " + decryptedMsgWithPubKey.message);
        if (decryptedMsgWithPubKey.signaturePubKey.equals(processModel.tradingPeer.getPubKeyRing().getSignaturePubKey()))
            doApplyMailboxMessage(decryptedMsgWithPubKey.message, trade);
        else
            log.error("SignaturePubKey in message does not match the SignaturePubKey we have stored to that trading peer.");
    }

    protected abstract void doApplyMailboxMessage(Message message, Trade trade);

    protected abstract void doHandleDecryptedMessage(TradeMessage tradeMessage, NodeAddress peerNodeAddress);

    protected void startTimeout() {
        stopTimeout();

        timeoutTimer = UserThread.runAfter(() -> {
            log.error("Timeout reached. TradeID=" + trade.getId());
            trade.setErrorMessage("A timeout occurred.");
            cleanupTradableOnFault();
            cleanup();
        }, TIMEOUT_SEC);
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
        log.debug("cleanupTradable tradeState=" + state);
        TradeManager tradeManager = processModel.getTradeManager();
        final Trade.Phase phase = state.getPhase();

        if (trade.isInPreparation()) {
            // no funds left. we just clean up the trade list
            tradeManager.removePreparedTrade(trade);
        } else {
            // we have either as taker the fee paid or as maker the publishDepositTx request sent,
            // so the maker has his offer closed and therefor its for both a failed trade
            if (trade.isTakerFeePublished() && !trade.isWithdrawn())
                tradeManager.addTradeToFailedTrades(trade);

            // if we have not the deposit already published we swap reserved funds to available funds
            if (!trade.isDepositPublished())
                processModel.getWalletService().swapAnyTradeEntryContextToAvailableEntry(trade.getId());
        }
    }
}
