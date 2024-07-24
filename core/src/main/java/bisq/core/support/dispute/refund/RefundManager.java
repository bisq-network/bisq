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

package bisq.core.support.dispute.refund;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.mempool.MempoolService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.agent.DisputeAgentLookupMap;
import bisq.core.support.dispute.messages.DisputeResultMessage;
import bisq.core.support.dispute.messages.OpenNewDisputeMessage;
import bisq.core.support.dispute.messages.PeerOpenedDisputeMessage;
import bisq.core.support.messages.ChatMessage;
import bisq.core.support.messages.SupportMessage;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;
import bisq.common.util.Hex;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@Singleton
public final class RefundManager extends DisputeManager<RefundDisputeList> {
    private final DelayedPayoutTxReceiverService delayedPayoutTxReceiverService;
    private final MempoolService mempoolService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public RefundManager(P2PService p2PService,
                         TradeWalletService tradeWalletService,
                         BtcWalletService walletService,
                         WalletsSetup walletsSetup,
                         TradeManager tradeManager,
                         ClosedTradableManager closedTradableManager,
                         FailedTradesManager failedTradesManager,
                         OpenOfferManager openOfferManager,
                         DaoFacade daoFacade,
                         DelayedPayoutTxReceiverService delayedPayoutTxReceiverService,
                         KeyRing keyRing,
                         RefundDisputeListService refundDisputeListService,
                         Config config,
                         PriceFeedService priceFeedService,
                         MempoolService mempoolService) {
        super(p2PService, tradeWalletService, walletService, walletsSetup, tradeManager, closedTradableManager, failedTradesManager,
                openOfferManager, daoFacade, keyRing, refundDisputeListService, config, priceFeedService);
        this.delayedPayoutTxReceiverService = delayedPayoutTxReceiverService;

        this.mempoolService = mempoolService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Implement template methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public SupportType getSupportType() {
        return SupportType.REFUND;
    }

    @Override
    public void onSupportMessage(SupportMessage message) {
        if (canProcessMessage(message)) {
            log.info("Received {} with tradeId {} and uid {}",
                    message.getClass().getSimpleName(), message.getTradeId(), message.getUid());

            if (message instanceof OpenNewDisputeMessage) {
                onOpenNewDisputeMessage((OpenNewDisputeMessage) message);
            } else if (message instanceof PeerOpenedDisputeMessage) {
                onPeerOpenedDisputeMessage((PeerOpenedDisputeMessage) message);
            } else if (message instanceof ChatMessage) {
                onChatMessage((ChatMessage) message);
            } else if (message instanceof DisputeResultMessage) {
                onDisputeResultMessage((DisputeResultMessage) message);
            } else {
                log.warn("Unsupported message at dispatchMessage. message={}", message);
            }
        }
    }

    @Override
    protected Trade.DisputeState getDisputeStateStartedByPeer() {
        return Trade.DisputeState.REFUND_REQUEST_STARTED_BY_PEER;
    }

    @Override
    protected AckMessageSourceType getAckMessageSourceType() {
        return AckMessageSourceType.REFUND_MESSAGE;
    }

    @Override
    public void cleanupDisputes() {
        disputeListService.cleanupDisputes(tradeId -> tradeManager.closeDisputedTrade(tradeId, Trade.DisputeState.REFUND_REQUEST_CLOSED));
    }

    @Override
    protected String getDisputeInfo(Dispute dispute) {
        String role = Res.get("shared.refundAgent").toLowerCase();
        String roleContextMsg = Res.get("support.initialArbitratorMsg",
                DisputeAgentLookupMap.getMatrixLinkForAgent(getAgentNodeAddress(dispute).getFullAddress()));
        String link = "https://bisq.wiki/Dispute_resolution#Level_3:_Arbitration";
        return Res.get("support.initialInfoRefundAgent", role, roleContextMsg, role, link);
    }

    @Override
    protected String getDisputeIntroForPeer(String disputeInfo) {
        return Res.get("support.peerOpenedDispute", disputeInfo, Version.VERSION);
    }

    @Override
    protected String getDisputeIntroForDisputeCreator(String disputeInfo) {
        return Res.get("support.youOpenedDispute", disputeInfo, Version.VERSION);
    }

    @Override
    protected void addPriceInfoMessage(Dispute dispute, int counter) {
        // At refund agent we do not add the option trade price check as the time for dispute opening is not correct.
        // In case of an option trade the mediator adds to the result summary message automatically the system message
        // with the option trade detection info so the refund agent can see that as well.
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Message handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    // We get that message at both peers. The dispute object is in context of the trader
    public void onDisputeResultMessage(DisputeResultMessage disputeResultMessage) {
        DisputeResult disputeResult = disputeResultMessage.getDisputeResult();
        String tradeId = disputeResult.getTradeId();
        ChatMessage chatMessage = disputeResult.getChatMessage();
        checkNotNull(chatMessage, "chatMessage must not be null");
        Optional<Dispute> disputeOptional = findDispute(disputeResult);
        String uid = disputeResultMessage.getUid();
        if (disputeOptional.isEmpty()) {
            log.warn("We got a dispute result msg but we don't have a matching dispute. " +
                    "That might happen when we get the disputeResultMessage before the dispute was created. " +
                    "We try again after 2 sec. to apply the disputeResultMessage. TradeId = " + tradeId);
            if (!delayMsgMap.containsKey(uid)) {
                // We delay 2 sec. to be sure the comm. msg gets added first
                Timer timer = UserThread.runAfter(() -> onDisputeResultMessage(disputeResultMessage), 2);
                delayMsgMap.put(uid, timer);
            } else {
                log.warn("We got a dispute result msg after we already repeated to apply the message after a delay. " +
                        "That should never happen. TradeId = " + tradeId);
            }
            return;
        }

        Dispute dispute = disputeOptional.get();
        cleanupRetryMap(uid);
        if (!dispute.getChatMessages().contains(chatMessage)) {
            dispute.addAndPersistChatMessage(chatMessage);
        } else {
            log.warn("We got a dispute mail msg what we have already stored. TradeId = " + chatMessage.getTradeId());
        }
        dispute.setIsClosed();

        if (dispute.disputeResultProperty().get() != null) {
            log.warn("We got already a dispute result. That should only happen if a dispute needs to be closed " +
                    "again because the first close did not succeed. TradeId = " + tradeId);
        }

        dispute.setDisputeResult(disputeResult);

        Optional<Trade> tradeOptional = tradeManager.getTradeById(tradeId);
        if (tradeOptional.isPresent()) {
            Trade trade = tradeOptional.get();
            if (trade.getDisputeState() == Trade.DisputeState.REFUND_REQUESTED ||
                    trade.getDisputeState() == Trade.DisputeState.REFUND_REQUEST_STARTED_BY_PEER) {
                trade.setDisputeState(Trade.DisputeState.REFUND_REQUEST_CLOSED);
                tradeManager.requestPersistence();
            }
        } else {
            Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(tradeId);
            openOfferOptional.ifPresent(openOffer -> openOfferManager.closeOpenOffer(openOffer.getOffer()));
        }
        sendAckMessage(chatMessage, dispute.getAgentPubKeyRing(), true, null);

        // set state after payout as we call swapTradeEntryToAvailableEntry
        if (tradeManager.getTradeById(tradeId).isPresent()) {
            tradeManager.closeDisputedTrade(tradeId, Trade.DisputeState.REFUND_REQUEST_CLOSED);
        } else {
            Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(tradeId);
            openOfferOptional.ifPresent(openOffer -> openOfferManager.closeOpenOffer(openOffer.getOffer()));
        }

        maybeClearSensitiveData();
        requestPersistence();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    @Override
    public NodeAddress getAgentNodeAddress(Dispute dispute) {
        return dispute.getContract().getRefundAgentNodeAddress();
    }

    public CompletableFuture<List<Transaction>> requestBlockchainTransactions(String makerFeeTxId,
                                                                              String takerFeeTxId,
                                                                              String depositTxId,
                                                                              String delayedPayoutTxId) {
        // in regtest mode, simulate a delay & failure obtaining the blockchain transactions
        // since we cannot request them in regtest anyway.  this is useful for checking failure scenarios
        if (!Config.baseCurrencyNetwork().isMainnet()) {
            CompletableFuture<List<Transaction>> retFuture = new CompletableFuture<>();
            UserThread.runAfter(() -> retFuture.complete(new ArrayList<>()), 5);
            return retFuture;
        }

        NetworkParameters params = btcWalletService.getParams();
        List<Transaction> txs = new ArrayList<>();
        return mempoolService.requestTxAsHex(makerFeeTxId)
                .thenCompose(txAsHex -> {
                    txs.add(new Transaction(params, Hex.decode(txAsHex)));
                    return mempoolService.requestTxAsHex(takerFeeTxId);
                }).thenCompose(txAsHex -> {
                    txs.add(new Transaction(params, Hex.decode(txAsHex)));
                    return mempoolService.requestTxAsHex(depositTxId);
                }).thenCompose(txAsHex -> {
                    txs.add(new Transaction(params, Hex.decode(txAsHex)));
                    return mempoolService.requestTxAsHex(delayedPayoutTxId);
                })
                .thenApply(txAsHex -> {
                    txs.add(new Transaction(params, Hex.decode(txAsHex)));
                    return txs;
                });
    }

    public void verifyTradeTxChain(List<Transaction> txs) {
        Transaction makerFeeTx = txs.get(0);
        Transaction takerFeeTx = txs.get(1);
        Transaction depositTx = txs.get(2);
        Transaction delayedPayoutTx = txs.get(3);

        // The order and number of buyer and seller inputs are not part of the trade protocol consensus.
        // In the current implementation buyer inputs come before seller inputs at depositTx and there is
        // only 1 input per trader, but we do not want to rely on that.
        // So we just check that both fee txs are found in the inputs.
        boolean makerFeeTxFoundAtInputs = false;
        boolean takerFeeTxFoundAtInputs = false;
        for (TransactionInput transactionInput : depositTx.getInputs()) {
            String fundingTxId = transactionInput.getOutpoint().getHash().toString();
            if (!makerFeeTxFoundAtInputs) {
                makerFeeTxFoundAtInputs = fundingTxId.equals(makerFeeTx.getTxId().toString());
            }
            if (!takerFeeTxFoundAtInputs) {
                takerFeeTxFoundAtInputs = fundingTxId.equals(takerFeeTx.getTxId().toString());
            }
        }
        checkArgument(makerFeeTxFoundAtInputs, "makerFeeTx not found at depositTx inputs");
        checkArgument(takerFeeTxFoundAtInputs, "takerFeeTx not found at depositTx inputs");
        checkArgument(depositTx.getInputs().size() >= 2,
                "DepositTx must have at least 2 inputs");
        checkArgument(delayedPayoutTx.getInputs().size() == 1,
                "DelayedPayoutTx must have 1 input");
        TransactionOutPoint delayedPayoutTxInputOutpoint = delayedPayoutTx.getInputs().get(0).getOutpoint();
        String fundingTxId = delayedPayoutTxInputOutpoint.getHash().toString();
        checkArgument(fundingTxId.equals(depositTx.getTxId().toString()),
                "First input at delayedPayoutTx does not connect to depositTx");
    }

    public void verifyDelayedPayoutTxReceivers(Transaction delayedPayoutTx, Dispute dispute) {
        Transaction depositTx = dispute.findDepositTx(btcWalletService).orElseThrow();
        long inputAmount = depositTx.getOutput(0).getValue().value;
        int selectionHeight = dispute.getBurningManSelectionHeight();

        List<Tuple2<Long, String>> delayedPayoutTxReceivers = delayedPayoutTxReceiverService.getReceivers(
                selectionHeight,
                inputAmount,
                dispute.getTradeTxFee(),
                DelayedPayoutTxReceiverService.ReceiverFlag.flagsActivatedBy(dispute.getTradeDate()));
        log.info("Verify delayedPayoutTx using selectionHeight {} and receivers {}", selectionHeight, delayedPayoutTxReceivers);
        checkArgument(delayedPayoutTx.getOutputs().size() == delayedPayoutTxReceivers.size(),
                "Size of outputs and delayedPayoutTxReceivers must be the same");

        NetworkParameters params = btcWalletService.getParams();
        for (int i = 0; i < delayedPayoutTx.getOutputs().size(); i++) {
            TransactionOutput transactionOutput = delayedPayoutTx.getOutputs().get(i);
            Tuple2<Long, String> receiverTuple = delayedPayoutTxReceivers.get(0);
            checkArgument(transactionOutput.getScriptPubKey().getToAddress(params).toString().equals(receiverTuple.second),
                    "output address does not match delayedPayoutTxReceivers address. transactionOutput=" + transactionOutput);
            checkArgument(transactionOutput.getValue().value == receiverTuple.first,
                    "output value does not match delayedPayoutTxReceivers value. transactionOutput=" + transactionOutput);
        }
    }
}
