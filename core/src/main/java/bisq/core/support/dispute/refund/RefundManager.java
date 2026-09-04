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
import bisq.core.btc.wallet.Restrictions;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.mempool.MempoolService;
import bisq.core.provider.mempool.MempoolTxStatus;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.DisputeValidation;
import bisq.core.support.dispute.agent.DisputeAgentLookupMap;
import bisq.core.support.dispute.messages.DisputeResultMessage;
import bisq.core.support.dispute.messages.OpenNewDisputeMessage;
import bisq.core.support.dispute.messages.PeerOpenedDisputeMessage;
import bisq.core.support.messages.ChatMessage;
import bisq.core.support.messages.SupportMessage;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.util.Hex;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.google.common.annotations.VisibleForTesting;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.core.trade.validation.DelayedPayoutTxValidation.checkDelayedPayoutTx;
import static bisq.core.trade.validation.DelayedPayoutTxValidation.checkDelayedPayoutTxInput;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@Singleton
public final class RefundManager extends DisputeManager<RefundDisputeList> {
    private static final int MIN_REFUND_TX_CONFIRMATIONS = 1;
    private final DelayedPayoutTxReceiverService delayedPayoutTxReceiverService;
    private final MempoolService mempoolService;
    private final RefundPayoutReceiptService refundPayoutReceiptService;


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
                         MempoolService mempoolService,
                         RefundPayoutReceiptService refundPayoutReceiptService) {
        super(p2PService, tradeWalletService, walletService, walletsSetup, tradeManager, closedTradableManager, failedTradesManager,
                openOfferManager, daoFacade, keyRing, refundDisputeListService, config, priceFeedService);
        this.delayedPayoutTxReceiverService = delayedPayoutTxReceiverService;

        this.mempoolService = mempoolService;
        this.refundPayoutReceiptService = refundPayoutReceiptService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Implement template methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public SupportType getSupportType() {
        return SupportType.REFUND;
    }

    @Override
    public void onSupportMessage(SupportMessage message, PublicKey senderSignaturePubKey) {
        if (canProcessMessage(message)) {
            log.info("Received {} with tradeId {} and uid {}",
                    message.getClass().getSimpleName(), message.getTradeId(), message.getUid());

            if (message instanceof OpenNewDisputeMessage openNewDisputeMessage) {
                onOpenNewDisputeMessage(openNewDisputeMessage, senderSignaturePubKey);
            } else if (message instanceof PeerOpenedDisputeMessage peerOpenedDisputeMessage) {
                onPeerOpenedDisputeMessage(peerOpenedDisputeMessage, senderSignaturePubKey);
            } else if (message instanceof ChatMessage chatMessage) {
                onChatMessage(chatMessage, senderSignaturePubKey);
            } else if (message instanceof DisputeResultMessage disputeResultMessage) {
                onDisputeResultMessage(disputeResultMessage, senderSignaturePubKey);
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
    public void onDisputeResultMessage(DisputeResultMessage disputeResultMessage, PublicKey senderSignaturePubKey) {
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
                Timer timer = UserThread.runAfter(() -> onDisputeResultMessage(disputeResultMessage, senderSignaturePubKey), 2);
                delayMsgMap.put(uid, timer);
            } else {
                log.warn("We got a dispute result msg after we already repeated to apply the message after a delay. " +
                        "That should never happen. TradeId = " + tradeId);
            }
            return;
        }

        Dispute dispute = disputeOptional.get();
        if (!isDisputeAgentSignaturePubKeyValid(dispute,
                senderSignaturePubKey,
                disputeResultMessage.getClass().getSimpleName())) {
            return;
        }

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

    @Nullable
    @Override
    protected PubKeyRing getExpectedAgentPubKeyRing(Trade trade) {
        return trade.getRefundAgentPubKeyRing();
    }

    public CompletableFuture<RefundTransactionChain> requestBlockchainTransactions(String makerFeeTxId,
                                                                                    String takerFeeTxId,
                                                                                    String depositTxId,
                                                                                    String delayedPayoutTxId) {
        // in regtest mode, simulate a delay & failure obtaining the blockchain transactions
        // since we cannot request them in regtest anyway.  this is useful for checking failure scenarios
        if (!Config.baseCurrencyNetwork().isMainnet()) {
            CompletableFuture<RefundTransactionChain> retFuture = new CompletableFuture<>();
            UserThread.runAfter(() -> retFuture.completeExceptionally(
                    new IllegalStateException("Refund transaction verification is only available on mainnet")), 5);
            return retFuture;
        }

        NetworkParameters params = btcWalletService.getParams();
        List<Transaction> txs = new ArrayList<>();
        try {
            return mempoolService.requestTxAsHex(makerFeeTxId)
                    .thenCompose(txAsHex -> {
                        txs.add(parseRequestedTransaction(params, makerFeeTxId, txAsHex));
                        return mempoolService.requestTxAsHex(takerFeeTxId);
                    }).thenCompose(txAsHex -> {
                        txs.add(parseRequestedTransaction(params, takerFeeTxId, txAsHex));
                        return mempoolService.requestTxAsHex(depositTxId);
                    }).thenCompose(txAsHex -> {
                        txs.add(parseRequestedTransaction(params, depositTxId, txAsHex));
                        return mempoolService.requestTxAsHex(delayedPayoutTxId);
                    }).thenCompose(txAsHex -> {
                        txs.add(parseRequestedTransaction(params, delayedPayoutTxId, txAsHex));
                        return mempoolService.requestTxStatus(depositTxId);
                    }).thenCompose(depositStatus -> mempoolService.requestTxStatus(delayedPayoutTxId)
                            .thenApply(delayedPayoutStatus -> new RefundTransactionChain(
                                    txs.get(0),
                                    txs.get(1),
                                    txs.get(2),
                                    txs.get(3),
                                    depositStatus,
                                    delayedPayoutStatus)));
        } catch (RuntimeException exception) {
            // The first request validates its transaction ID before the asynchronous call is created. A malformed
            // ID must reach the caller as a failed future, so the close dialog can report it and recover.
            return CompletableFuture.failedFuture(exception);
        }
    }

    @VisibleForTesting
    static Transaction parseRequestedTransaction(NetworkParameters params, String requestedTxId, String txAsHex) {
        Transaction transaction = new Transaction(checkNotNull(params, "params must not be null"),
                Hex.decode(checkNotNull(txAsHex, "txAsHex must not be null")));
        Sha256Hash expectedTxId = Sha256Hash.wrap(checkNotNull(requestedTxId,
                "requestedTxId must not be null"));
        checkArgument(transaction.getTxId().equals(expectedTxId),
                "Returned transaction ID %s does not match requested ID %s",
                transaction.getTxId(),
                expectedTxId);
        return transaction;
    }

    public void verifyTradeTxChain(List<Transaction> txs) {
        checkArgument(txs.size() == 4, "Expected exactly 4 trade transactions");
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
        checkDelayedPayoutTxInput(delayedPayoutTx, depositTx);
    }

    public void verifyTradeTxChain(RefundTransactionChain txChain, Dispute dispute) {
        RefundTransactionChain checkedTxChain = checkNotNull(txChain, "txChain must not be null");
        Dispute checkedDispute = checkNotNull(dispute, "dispute must not be null");
        Contract contract = checkNotNull(checkedDispute.getContract(), "dispute contract must not be null");

        checkTransactionId(checkedTxChain.makerFeeTx(),
                contract.getOfferPayload().getOfferFeePaymentTxId(),
                "maker fee");
        checkTransactionId(checkedTxChain.takerFeeTx(), contract.getTakerFeeTxID(), "taker fee");
        checkTransactionId(checkedTxChain.depositTx(), checkedDispute.getDepositTxId(), "deposit");
        checkTransactionId(checkedTxChain.delayedPayoutTx(),
                checkedDispute.getDelayedPayoutTxId(),
                "delayed payout");
        verifyTradeTxChain(checkedTxChain.transactions());
        checkDelayedPayoutTx(checkedTxChain.delayedPayoutTx(), contract.getLockTime());

        long localChainHeight = daoFacade.getChainHeight();
        checkArgument(localChainHeight > 0, "Local DAO chain height must be positive");
        long depositBlockHeight = checkConfirmed(checkedTxChain.depositStatus(), localChainHeight, "deposit");
        long delayedPayoutBlockHeight = checkConfirmed(checkedTxChain.delayedPayoutStatus(),
                localChainHeight,
                "delayed payout");
        checkArgument(delayedPayoutBlockHeight >= depositBlockHeight,
                "Delayed payout transaction must not confirm before the deposit transaction");
        checkArgument(delayedPayoutBlockHeight > contract.getLockTime(),
                "Delayed payout transaction must confirm after its contract lock time");

        long tradeStartHeight = getTradeStartHeight(contract);
        checkArgument(depositBlockHeight >= tradeStartHeight,
                "Deposit transaction confirmed before the contract-derived trade start height");
        checkArgument(depositBlockHeight <= contract.getLockTime(),
                "Deposit transaction confirmed after the contract lock time");
        verifyBurningManSelectionHeight(checkedDispute, tradeStartHeight, depositBlockHeight);
    }

    private static void checkTransactionId(Transaction transaction, String expectedTxId, String label) {
        Sha256Hash expectedHash = Sha256Hash.wrap(checkNotNull(expectedTxId,
                "%s transaction ID must not be null",
                label));
        checkArgument(transaction.getTxId().equals(expectedHash),
                "%s transaction ID %s does not match expected ID %s",
                label,
                transaction.getTxId(),
                expectedHash);
    }

    private static long checkConfirmed(MempoolTxStatus status,
                                       long localChainHeight,
                                       String label) {
        checkArgument(status.confirmed(), "%s transaction must be confirmed", label);
        checkArgument(status.blockHeight() <= localChainHeight,
                "%s transaction block height %s is ahead of local chain height %s",
                label,
                status.blockHeight(),
                localChainHeight);
        long confirmations = localChainHeight - status.blockHeight() + 1;
        checkArgument(confirmations >= MIN_REFUND_TX_CONFIRMATIONS,
                "%s transaction must have at least %s confirmation(s)",
                label,
                MIN_REFUND_TX_CONFIRMATIONS);
        return status.blockHeight();
    }

    private void verifyBurningManSelectionHeight(Dispute dispute,
                                                 long tradeStartHeight,
                                                 long depositBlockHeight) {
        if (dispute.isUsingLegacyBurningMan()) {
            checkArgument(tradeStartHeight < DelayedPayoutTxReceiverService.MIN_SNAPSHOT_HEIGHT,
                    "Legacy Burning Man is only valid for trades predating the minimum snapshot height");
            return;
        }

        checkArgument(tradeStartHeight >= DelayedPayoutTxReceiverService.MIN_SNAPSHOT_HEIGHT,
                "Burning Man receiver selection requires a trade at or after the minimum snapshot height");
        int selectionHeight = dispute.getBurningManSelectionHeight();
        checkArgument(selectionHeight > 0, "Burning Man selection height must be positive");
        int expectedSelectionHeight = delayedPayoutTxReceiverService.getBurningManSelectionHeight(
                Math.toIntExact(tradeStartHeight));
        long selectionDifference = Math.abs((long) selectionHeight - expectedSelectionHeight);
        checkArgument(selectionDifference == 0 ||
                        selectionDifference == DelayedPayoutTxReceiverService.SNAPSHOT_SELECTION_GRID_SIZE,
                "Burning Man selection height %s must match contract-derived height %s or differ by one snapshot grid",
                selectionHeight,
                expectedSelectionHeight);

        int depositSelectionHeight = delayedPayoutTxReceiverService.getBurningManSelectionHeight(
                Math.toIntExact(depositBlockHeight));
        checkArgument(selectionHeight <= depositSelectionHeight,
                "Burning Man selection height must not be later than the deposit confirmation snapshot");
    }

    private static long getTradeStartHeight(Contract contract) {
        String paymentMethodId = checkNotNull(contract.getPaymentMethodId(),
                "contract payment method ID must not be null");
        boolean isBlockchainPayment = PaymentMethod.BLOCK_CHAINS_ID.equals(paymentMethodId) ||
                PaymentMethod.BLOCK_CHAINS_INSTANT_ID.equals(paymentMethodId);
        long tradeStartHeight = Math.subtractExact(contract.getLockTime(),
                Restrictions.getLockTime(isBlockchainPayment));
        checkArgument(tradeStartHeight > 0, "Contract-derived trade start height must be positive");
        return tradeStartHeight;
    }

    public long verifyDepositTx(Transaction depositTx, Dispute dispute) {
        DisputeValidation.validateRefundDepositTx(dispute, depositTx);
        Coin declaredPot = getDeclaredTradePot(dispute.getContract());
        Coin verifiedTradeTxFee = depositTx.getOutput(0).getValue().subtract(declaredPot);
        checkArgument(verifiedTradeTxFee.isPositive(), "Verified trade tx fee must be positive");
        checkArgument(verifiedTradeTxFee.value == dispute.getTradeTxFee(),
                "Trade tx fee does not match the fee derived from deposit output 0. actual=%s, expected=%s",
                dispute.getTradeTxFee(),
                verifiedTradeTxFee.value);
        return verifiedTradeTxFee.value;
    }

    public void verifyDelayedPayoutTxReceivers(Transaction depositTx,
                                                Transaction delayedPayoutTx,
                                                Dispute dispute) {
        long verifiedTradeTxFee = verifyDepositTx(depositTx, dispute);
        long inputAmount = depositTx.getOutput(0).getValue().value;
        int selectionHeight = dispute.getBurningManSelectionHeight();

        int burningManAddressListVersion = dispute.getContract().getBurningManAddressListVersion();
        List<Tuple2<Long, String>> delayedPayoutTxReceivers = delayedPayoutTxReceiverService.getReceivers(
                selectionHeight,
                inputAmount,
                verifiedTradeTxFee,
                burningManAddressListVersion);
        delayedPayoutTxReceiverService.validateDelayedPayoutTxReceivers(
                delayedPayoutTxReceivers,
                burningManAddressListVersion);
        log.info("Verify delayedPayoutTx using selectionHeight {}, BM address list version {} and receivers {}",
                selectionHeight, burningManAddressListVersion, delayedPayoutTxReceivers);
        checkArgument(delayedPayoutTx.getOutputs().size() == delayedPayoutTxReceivers.size(),
                "Size of outputs and delayedPayoutTxReceivers must be the same");

        NetworkParameters params = btcWalletService.getParams();
        for (int i = 0; i < delayedPayoutTx.getOutputs().size(); i++) {
            TransactionOutput transactionOutput = delayedPayoutTx.getOutputs().get(i);
            Tuple2<Long, String> receiverTuple = delayedPayoutTxReceivers.get(i);
            Address address = transactionOutput.getScriptPubKey().getToAddress(params);
            Address receiverAddress = Address.fromString(params, receiverTuple.second);
            checkArgument(address.equals(receiverAddress),
                    "output address does not match delayedPayoutTxReceivers address. transactionOutput=" + transactionOutput);
            checkArgument(transactionOutput.getValue().value == receiverTuple.first,
                    "output value does not match delayedPayoutTxReceivers value. transactionOutput=" + transactionOutput);
        }
    }

    public RefundValidationResult verifyRefundPayoutAmount(Transaction depositTx,
                                                           Transaction delayedPayoutTx,
                                                           Dispute dispute,
                                                           Coin buyerPayoutAmount,
                                                           Coin sellerPayoutAmount) {
        long verifiedTradeTxFee = verifyDepositTx(depositTx, dispute);
        checkArgument(depositTx.getTxId().toString().equals(dispute.getDepositTxId()),
                "Fetched deposit tx ID does not match the dispute deposit tx ID");
        checkArgument(delayedPayoutTx.getTxId().toString().equals(dispute.getDelayedPayoutTxId()),
                "Fetched delayed payout tx ID does not match the dispute delayed payout tx ID");
        Coin checkedBuyerPayoutAmount = checkNotNull(buyerPayoutAmount,
                "buyerPayoutAmount must not be null");
        Coin checkedSellerPayoutAmount = checkNotNull(sellerPayoutAmount,
                "sellerPayoutAmount must not be null");
        checkArgument(!checkedBuyerPayoutAmount.isNegative(), "buyerPayoutAmount must not be negative");
        checkArgument(!checkedSellerPayoutAmount.isNegative(), "sellerPayoutAmount must not be negative");

        Coin proposedRefund = checkedBuyerPayoutAmount.add(checkedSellerPayoutAmount);
        Coin declaredPot = getDeclaredTradePot(dispute.getContract());
        Coin depositOutputValue = depositTx.getOutput(0).getValue();
        Coin validatedReceiverOutputSum = delayedPayoutTx.getOutputs().stream()
                .map(TransactionOutput::getValue)
                .reduce(Coin.ZERO, Coin::add);
        // The validated deposit output is the escrow evidence. Its value minus the verified trade fee equals the
        // contract pot, which is also the limit the close dialog offers before the delayed payout transaction has
        // been fetched (RefundPayoutReceiptService.getMaximumPayoutAmount). The delayed payout outputs are smaller
        // by the DPT miner fee; they are recorded for the binding below but do not bound the refund.
        Coin verifiedMaximum = RefundPayoutReceiptService.calculateMaximumPayoutAmount(declaredPot,
                depositOutputValue,
                verifiedTradeTxFee);
        checkArgument(!proposedRefund.isGreaterThan(verifiedMaximum),
                "Proposed refund amount %s exceeds verified maximum %s",
                proposedRefund,
                verifiedMaximum);
        return new RefundValidationResult(
                Hex.encode(checkNotNull(dispute.getContractHash(), "dispute contractHash must not be null")),
                depositTx.getTxId().toString(),
                delayedPayoutTx.getTxId().toString(),
                depositOutputValue.value,
                validatedReceiverOutputSum.value,
                verifiedMaximum.value,
                dispute.getTradeTxFee(),
                dispute.getBurningManSelectionHeight(),
                dispute.getDonationAddressOfDelayedPayoutTx(),
                checkedBuyerPayoutAmount.value,
                checkedSellerPayoutAmount.value);
    }

    private static Coin getDeclaredTradePot(Contract contract) {
        checkNotNull(contract, "contract must not be null");
        return contract.getTradeAmount()
                .add(Coin.valueOf(contract.getOfferPayload().getBuyerSecurityDeposit()))
                .add(Coin.valueOf(contract.getOfferPayload().getSellerSecurityDeposit()));
    }

    // Trades created before the Burning Man receivers paid the whole escrow, minus the miner fee, to a single DAO
    // donation address. The dispute-carried burningManSelectionHeight selects this branch, so it must be as strict
    // as verifyDelayedPayoutTxReceivers: the fetched transaction itself has to pay to a DAO donation address.
    // Checking only the dispute-carried address string would not prove where the escrow went.
    public void verifyLegacyDelayedPayoutTx(Transaction delayedPayoutTx, Dispute dispute)
            throws DisputeValidation.AddressException {
        checkArgument(delayedPayoutTx.getOutputs().size() == 1,
                "Legacy delayedPayoutTx must have exactly 1 output");
        DisputeValidation.validateDonationAddress(dispute, delayedPayoutTx, btcWalletService.getParams());
        DisputeValidation.validateDonationAddressMatchesAnyPastParamValues(dispute,
                dispute.getDonationAddressOfDelayedPayoutTx(),
                daoFacade);
    }

    public Optional<String> findRefundPayoutTxId(Dispute dispute) {
        return refundPayoutReceiptService.findPayoutTxId(dispute);
    }

    public Coin getMaximumRefundPayoutAmount(Dispute dispute) {
        return refundPayoutReceiptService.getMaximumPayoutAmount(dispute);
    }

    public void persistRefundPayoutReservation(Dispute dispute,
                                               Transaction payoutTx,
                                               Runnable completeHandler,
                                               Consumer<Throwable> errorHandler) {
        refundPayoutReceiptService.persistPayoutReservation(dispute,
                payoutTx,
                completeHandler,
                errorHandler);
    }
}
