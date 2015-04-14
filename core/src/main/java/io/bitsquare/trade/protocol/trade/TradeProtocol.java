/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade;

import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.crypto.MessageWithPubKey;
import io.bitsquare.crypto.PubKeyRing;
import io.bitsquare.p2p.DecryptedMessageHandler;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.Peer;
import io.bitsquare.p2p.listener.GetPeerAddressListener;
import io.bitsquare.trade.BuyerTrade;
import io.bitsquare.trade.SellerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.tasks.shared.SetupPayoutTxLockTimeReachedListener;
import io.bitsquare.trade.states.BuyerTradeState;
import io.bitsquare.trade.states.SellerTradeState;

import org.bitcoinj.utils.Threading;

import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.nonEmptyStringOf;

public abstract class TradeProtocol {
    private static final Logger log = LoggerFactory.getLogger(TradeProtocol.class);
    private static final long TIMEOUT = 10000;

    protected final ProcessModel processModel;
    private DecryptedMessageHandler decryptedMessageHandler;
    protected Timer timeoutTimer;
    protected Trade trade;

    public TradeProtocol(ProcessModel processModel) {
        this.processModel = processModel;

        decryptedMessageHandler = this::handleMessageWithPubKey;

        processModel.getMessageService().addDecryptedMessageHandler(decryptedMessageHandler);
    }

    public void cleanup() {
        log.debug("cleanup " + this);
        stopTimeout();
        if (decryptedMessageHandler != null)
            processModel.getMessageService().removeDecryptedMessageHandler(decryptedMessageHandler);
    }

    public void applyMailboxMessage(MessageWithPubKey messageWithPubKey, Trade trade) {
        log.debug("applyMailboxMessage " + messageWithPubKey.getMessage());
        if (messageWithPubKey.getSignaturePubKey().equals(processModel.tradingPeer.getPubKeyRing().getMsgSignaturePubKey()))
            doApplyMailboxMessage(messageWithPubKey.getMessage(), trade);
        else
            log.error("SignaturePubKey in message does not match the SignaturePubKey we have stored to that trading peer.");
    }

    protected abstract void doApplyMailboxMessage(Message message, Trade trade);

    protected void findPeerAddress(PubKeyRing pubKeyRing, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        try {
            processModel.getAddressService().findPeerAddress(pubKeyRing, new GetPeerAddressListener() {
                @Override
                public void onResult(Peer peer) {
                    trade.setTradingPeer(peer);
                    resultHandler.handleResult();
                }

                @Override
                public void onFailed() {
                    errorMessageHandler.handleErrorMessage("findPeerAddress failed");
                }
            });
        } catch (Throwable t) {
            errorMessageHandler.handleErrorMessage("findPeerAddress failed with error: " + t.getMessage());
        }
    }

    protected void handleMessageWithPubKey(MessageWithPubKey messageWithPubKey, Peer sender) {
        // We check the sig only as soon we have stored the peers pubKeyRing.
        if (processModel.tradingPeer.getPubKeyRing() != null &&
                !messageWithPubKey.getSignaturePubKey().equals(processModel.tradingPeer.getPubKeyRing().getMsgSignaturePubKey())) {
            log.error("Signature used in seal message does not match the one stored with that trade for the trading peer.");
        }
        else {
            Message message = messageWithPubKey.getMessage();
            log.trace("handleNewMessage: message = " + message.getClass().getSimpleName() + " from " + sender);
            if (message instanceof TradeMessage) {
                TradeMessage tradeMessage = (TradeMessage) message;
                nonEmptyStringOf(tradeMessage.tradeId);

                if (tradeMessage.tradeId.equals(processModel.getId())) {
                    doHandleDecryptedMessage(tradeMessage, sender);
                }
            }
        }
    }

    protected abstract void doHandleDecryptedMessage(TradeMessage tradeMessage, Peer sender);

    public void checkPayoutTxTimeLock(Trade trade) {
        this.trade = trade;

        boolean needPayoutTxBroadcast = false;
        if (trade instanceof SellerTrade)
            needPayoutTxBroadcast = trade.processStateProperty().get() == SellerTradeState.ProcessState.PAYOUT_TX_COMMITTED;
        else if (trade instanceof BuyerTrade)
            needPayoutTxBroadcast = trade.processStateProperty().get() == BuyerTradeState.ProcessState.PAYOUT_TX_COMMITTED;

        if (needPayoutTxBroadcast) {
            TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                    () -> {
                        log.debug("taskRunner needPayoutTxBroadcast completed");
                        processModel.onComplete();
                    },
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(SetupPayoutTxLockTimeReachedListener.class);
            taskRunner.run();
        }
    }

    protected void startTimeout() {
        log.debug("startTimeout");
        stopTimeout();

        timeoutTimer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Threading.USER_THREAD.execute(() -> {
                    log.debug("Timeout reached");
                   /* if (trade instanceof SellerTrade)
                        trade.setProcessState(SellerTradeState.ProcessState.TIMEOUT);
                    else if (trade instanceof BuyerTrade)
                        trade.setProcessState(BuyerTradeState.ProcessState.TIMEOUT);*/
                });
            }
        };

        timeoutTimer.schedule(task, TIMEOUT);
    }

    protected void stopTimeout() {
        log.debug("stopTimeout");
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }

    protected void handleTaskRunnerFault(String errorMessage) {
        log.error(errorMessage);
        cleanup();
    }
}
