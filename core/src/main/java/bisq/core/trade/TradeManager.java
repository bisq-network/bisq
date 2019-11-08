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

package bisq.core.trade;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.exceptions.AddressEntryException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.filter.FilterManager;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.availability.OfferAvailabilityModel;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.support.dispute.refund.refundagent.RefundAgentManager;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.failed.FailedTradesManager;
import bisq.core.trade.handlers.TradeResultHandler;
import bisq.core.trade.messages.InputsForDepositTxRequest;
import bisq.core.trade.messages.PeerPublishedDelayedPayoutTxMessage;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;
import bisq.core.util.Validator;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.SendMailboxMessageListener;

import bisq.common.ClockWatcher;
import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.FaultHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import javax.inject.Inject;

import com.google.common.util.concurrent.FutureCallback;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import org.spongycastle.crypto.params.KeyParameter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;
import lombok.Setter;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class TradeManager implements PersistedDataHost {
    private static final Logger log = LoggerFactory.getLogger(TradeManager.class);

    private final User user;
    @Getter
    private final KeyRing keyRing;
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final TradeWalletService tradeWalletService;
    private final OpenOfferManager openOfferManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final FilterManager filterManager;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final ReferralIdService referralIdService;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final ArbitratorManager arbitratorManager;
    private final MediatorManager mediatorManager;
    private final RefundAgentManager refundAgentManager;
    private final DaoFacade daoFacade;
    private final ClockWatcher clockWatcher;

    private final Storage<TradableList<Trade>> tradableListStorage;
    private TradableList<Trade> tradableList;
    private final BooleanProperty pendingTradesInitialized = new SimpleBooleanProperty();
    private List<Trade> tradesForStatistics;
    @Setter
    @Nullable
    private ErrorMessageHandler takeOfferRequestErrorMessageHandler;
    @Getter
    private final LongProperty numPendingTrades = new SimpleLongProperty();
    @Getter
    private final ObservableList<Trade> tradesWithoutDepositTx = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeManager(User user,
                        KeyRing keyRing,
                        BtcWalletService btcWalletService,
                        BsqWalletService bsqWalletService,
                        TradeWalletService tradeWalletService,
                        OpenOfferManager openOfferManager,
                        ClosedTradableManager closedTradableManager,
                        FailedTradesManager failedTradesManager,
                        P2PService p2PService,
                        PriceFeedService priceFeedService,
                        FilterManager filterManager,
                        TradeStatisticsManager tradeStatisticsManager,
                        ReferralIdService referralIdService,
                        AccountAgeWitnessService accountAgeWitnessService,
                        ArbitratorManager arbitratorManager,
                        MediatorManager mediatorManager,
                        RefundAgentManager refundAgentManager,
                        DaoFacade daoFacade,
                        ClockWatcher clockWatcher,
                        Storage<TradableList<Trade>> storage) {
        this.user = user;
        this.keyRing = keyRing;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.tradeWalletService = tradeWalletService;
        this.openOfferManager = openOfferManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.filterManager = filterManager;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.referralIdService = referralIdService;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.arbitratorManager = arbitratorManager;
        this.mediatorManager = mediatorManager;
        this.refundAgentManager = refundAgentManager;
        this.daoFacade = daoFacade;
        this.clockWatcher = clockWatcher;

        tradableListStorage = storage;

        p2PService.addDecryptedDirectMessageListener((decryptedMessageWithPubKey, peerNodeAddress) -> {
            NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();

            // Handler for incoming initial network_messages from taker
            if (networkEnvelope instanceof InputsForDepositTxRequest) {
                handlePayDepositRequest((InputsForDepositTxRequest) networkEnvelope, peerNodeAddress);
            }
        });

        // Might get called at startup after HS is published. Can be before or after initPendingTrades.
        p2PService.addDecryptedMailboxListener((decryptedMessageWithPubKey, senderNodeAddress) -> {
            NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
            if (networkEnvelope instanceof TradeMessage) {
                TradeMessage tradeMessage = (TradeMessage) networkEnvelope;
                String tradeId = tradeMessage.getTradeId();
                Optional<Trade> tradeOptional = tradableList.stream().filter(e -> e.getId().equals(tradeId)).findAny();
                // The mailbox message will be removed inside the tasks after they are processed successfully
                tradeOptional.ifPresent(trade -> trade.addDecryptedMessageWithPubKey(decryptedMessageWithPubKey));
            } else if (networkEnvelope instanceof AckMessage) {
                AckMessage ackMessage = (AckMessage) networkEnvelope;
                if (ackMessage.getSourceType() == AckMessageSourceType.TRADE_MESSAGE) {
                    if (ackMessage.isSuccess()) {
                        log.info("Received AckMessage for {} with tradeId {} and uid {}",
                                ackMessage.getSourceMsgClassName(), ackMessage.getSourceId(), ackMessage.getSourceUid());
                    } else {
                        log.warn("Received AckMessage with error state for {} with tradeId {} and errorMessage={}",
                                ackMessage.getSourceMsgClassName(), ackMessage.getSourceId(), ackMessage.getErrorMessage());
                    }
                    p2PService.removeEntryFromMailbox(decryptedMessageWithPubKey);
                }
            }
        });
    }

    @Override
    public void readPersisted() {
        tradableList = new TradableList<>(tradableListStorage, "PendingTrades");
        tradableList.forEach(trade -> {
            trade.setTransientFields(tradableListStorage, btcWalletService);
            Offer offer = trade.getOffer();
            if (offer != null)
                offer.setPriceFeedService(priceFeedService);
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        if (p2PService.isBootstrapped())
            initPendingTrades();
        else
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onUpdatedDataReceived() {
                    // Get called after onMailboxMessageAdded from initial data request
                    // The mailbox message will be removed inside the tasks after they are processed successfully
                    initPendingTrades();
                }
            });

        tradableList.getList().addListener((ListChangeListener<Trade>) change -> onTradesChanged());
        onTradesChanged();

        getAddressEntriesForAvailableBalanceStream()
                .filter(addressEntry -> addressEntry.getOfferId() != null)
                .forEach(addressEntry -> {
                    log.warn("Swapping pending OFFER_FUNDING entries at startup. offerId={}", addressEntry.getOfferId());
                    btcWalletService.swapTradeEntryToAvailableEntry(addressEntry.getOfferId(), AddressEntry.Context.OFFER_FUNDING);
                });
    }

    public void shutDown() {
    }

    private void initPendingTrades() {
        List<Trade> addTradeToFailedTradesList = new ArrayList<>();
        List<Trade> removePreparedTradeList = new ArrayList<>();
        tradesForStatistics = new ArrayList<>();
        tradableList.forEach(trade -> {
                    if (trade.isDepositPublished() ||
                            (trade.isTakerFeePublished() && !trade.hasFailed())) {
                        initTrade(trade, trade.getProcessModel().isUseSavingsWallet(),
                                trade.getProcessModel().getFundsNeededForTradeAsLong());
                        trade.updateDepositTxFromWallet();
                        tradesForStatistics.add(trade);
                    } else if (trade.isTakerFeePublished() && !trade.isFundsLockedIn()) {
                        addTradeToFailedTradesList.add(trade);
                        trade.appendErrorMessage("Invalid state: trade.isTakerFeePublished() && !trade.isFundsLockedIn()");
                    } else {
                        removePreparedTradeList.add(trade);
                    }

            if (trade.getDepositTx() == null) {
                log.warn("Deposit tx for trader with ID {} is null at initPendingTrades. " +
                                "This can happen for valid transaction in rare cases (e.g. after a SPV resync). " +
                                "We leave it to the user to move the trade to failed trades if the problem persists.",
                        trade.getId());
                tradesWithoutDepositTx.add(trade);
            }
                }
        );

        // If we have a closed trade where the deposit tx is still not confirmed we move it to failed trades as the
        // payout tx cannot be valid as well in this case. As the trade do not progress without confirmation of the
        // deposit tx this should normally not happen. If we detect such a trade at start up (done in BisqSetup)  we
        // show a popup telling the user to do a SPV resync.
        closedTradableManager.getClosedTradables().stream()
                .filter(tradable -> tradable instanceof Trade)
                .map(tradable -> (Trade) tradable)
                .filter(Trade::isFundsLockedIn)
                .forEach(addTradeToFailedTradesList::add);

        addTradeToFailedTradesList.forEach(closedTradableManager::remove);

        addTradeToFailedTradesList.forEach(this::addTradeToFailedTrades);

        removePreparedTradeList.forEach(this::removePreparedTrade);

        cleanUpAddressEntries();

        pendingTradesInitialized.set(true);
    }

    private void onTradesChanged() {
        this.numPendingTrades.set(tradableList.getList().size());
    }

    private void cleanUpAddressEntries() {
        // We check if we have address entries which are not in our pending trades and clean up those entries.
        // They might be either from closed or failed trades or from trades we do not have at all in our data base files.
        Set<String> tradesIdSet = getTradesStreamWithFundsLockedIn()
                .map(Tradable::getId)
                .collect(Collectors.toSet());

        btcWalletService.getAddressEntriesForTrade().stream()
                .filter(e -> !tradesIdSet.contains(e.getOfferId()))
                .forEach(e -> {
                    log.warn("We found an outdated addressEntry for trade {}: entry={}", e.getOfferId(), e);
                    btcWalletService.resetAddressEntriesForPendingTrade(e.getOfferId());
                });
    }

    private void handlePayDepositRequest(InputsForDepositTxRequest inputsForDepositTxRequest, NodeAddress peer) {
        log.info("Received PayDepositRequest from {} with tradeId {} and uid {}",
                peer, inputsForDepositTxRequest.getTradeId(), inputsForDepositTxRequest.getUid());

        try {
            Validator.nonEmptyStringOf(inputsForDepositTxRequest.getTradeId());
        } catch (Throwable t) {
            log.warn("Invalid requestDepositTxInputsMessage " + inputsForDepositTxRequest.toString());
            return;
        }

        Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(inputsForDepositTxRequest.getTradeId());
        if (openOfferOptional.isPresent() && openOfferOptional.get().getState() == OpenOffer.State.AVAILABLE) {
            OpenOffer openOffer = openOfferOptional.get();
            Offer offer = openOffer.getOffer();
            openOfferManager.reserveOpenOffer(openOffer);
            Trade trade;
            if (offer.isBuyOffer())
                trade = new BuyerAsMakerTrade(offer,
                        Coin.valueOf(inputsForDepositTxRequest.getTxFee()),
                        Coin.valueOf(inputsForDepositTxRequest.getTakerFee()),
                        inputsForDepositTxRequest.isCurrencyForTakerFeeBtc(),
                        openOffer.getArbitratorNodeAddress(),
                        openOffer.getMediatorNodeAddress(),
                        openOffer.getRefundAgentNodeAddress(),
                        tradableListStorage,
                        btcWalletService);
            else
                trade = new SellerAsMakerTrade(offer,
                        Coin.valueOf(inputsForDepositTxRequest.getTxFee()),
                        Coin.valueOf(inputsForDepositTxRequest.getTakerFee()),
                        inputsForDepositTxRequest.isCurrencyForTakerFeeBtc(),
                        openOffer.getArbitratorNodeAddress(),
                        openOffer.getMediatorNodeAddress(),
                        openOffer.getRefundAgentNodeAddress(),
                        tradableListStorage,
                        btcWalletService);

            initTrade(trade, trade.getProcessModel().isUseSavingsWallet(), trade.getProcessModel().getFundsNeededForTradeAsLong());
            tradableList.add(trade);
            ((MakerTrade) trade).handleTakeOfferRequest(inputsForDepositTxRequest, peer, errorMessage -> {
                if (takeOfferRequestErrorMessageHandler != null)
                    takeOfferRequestErrorMessageHandler.handleErrorMessage(errorMessage);
            });
        } else {
            // TODO respond
            //(RequestDepositTxInputsMessage)message.
            //  messageService.sendEncryptedMessage(peerAddress,messageWithPubKey.getMessage().);
            log.debug("We received a take offer request but don't have that offer anymore.");
        }
    }

    private void initTrade(Trade trade, boolean useSavingsWallet, Coin fundsNeededForTrade) {
        trade.init(p2PService,
                btcWalletService,
                bsqWalletService,
                tradeWalletService,
                daoFacade,
                this,
                openOfferManager,
                referralIdService,
                user,
                filterManager,
                accountAgeWitnessService,
                tradeStatisticsManager,
                arbitratorManager,
                mediatorManager,
                refundAgentManager,
                keyRing,
                useSavingsWallet,
                fundsNeededForTrade);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from Offerbook when offer gets removed from P2P network
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onOfferRemovedFromRemoteOfferBook(Offer offer) {
        offer.cancelAvailabilityRequest();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Take offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void checkOfferAvailability(Offer offer,
                                       ResultHandler resultHandler,
                                       ErrorMessageHandler errorMessageHandler) {
        offer.checkOfferAvailability(getOfferAvailabilityModel(offer), resultHandler, errorMessageHandler);
    }

    // When closing take offer view, we are not interested in the onCheckOfferAvailability result anymore, so remove from the map
    public void onCancelAvailabilityRequest(Offer offer) {
        offer.cancelAvailabilityRequest();
    }

    // First we check if offer is still available then we create the trade with the protocol
    public void onTakeOffer(Coin amount,
                            Coin txFee,
                            Coin takerFee,
                            boolean isCurrencyForTakerFeeBtc,
                            long tradePrice,
                            Coin fundsNeededForTrade,
                            Offer offer,
                            String paymentAccountId,
                            boolean useSavingsWallet,
                            TradeResultHandler tradeResultHandler,
                            ErrorMessageHandler errorMessageHandler) {
        final OfferAvailabilityModel model = getOfferAvailabilityModel(offer);
        offer.checkOfferAvailability(model,
                () -> {
                    if (offer.getState() == Offer.State.AVAILABLE)
                        createTrade(amount,
                                txFee,
                                takerFee,
                                isCurrencyForTakerFeeBtc,
                                tradePrice,
                                fundsNeededForTrade,
                                offer,
                                paymentAccountId,
                                useSavingsWallet,
                                model,
                                tradeResultHandler);
                },
                errorMessageHandler::handleErrorMessage);
    }

    private void createTrade(Coin amount,
                             Coin txFee,
                             Coin takerFee,
                             boolean isCurrencyForTakerFeeBtc,
                             long tradePrice,
                             Coin fundsNeededForTrade,
                             Offer offer,
                             String paymentAccountId,
                             boolean useSavingsWallet,
                             OfferAvailabilityModel model,
                             TradeResultHandler tradeResultHandler) {
        Trade trade;
        if (offer.isBuyOffer())
            trade = new SellerAsTakerTrade(offer,
                    amount,
                    txFee,
                    takerFee,
                    isCurrencyForTakerFeeBtc,
                    tradePrice,
                    model.getPeerNodeAddress(),
                    model.getSelectedArbitrator(),
                    model.getSelectedMediator(),
                    model.getSelectedRefundAgent(),
                    tradableListStorage,
                    btcWalletService);
        else
            trade = new BuyerAsTakerTrade(offer,
                    amount,
                    txFee,
                    takerFee,
                    isCurrencyForTakerFeeBtc,
                    tradePrice,
                    model.getPeerNodeAddress(),
                    model.getSelectedArbitrator(),
                    model.getSelectedMediator(),
                    model.getSelectedRefundAgent(),
                    tradableListStorage,
                    btcWalletService);

        trade.setTakerPaymentAccountId(paymentAccountId);

        initTrade(trade, useSavingsWallet, fundsNeededForTrade);

        tradableList.add(trade);
        ((TakerTrade) trade).takeAvailableOffer();
        tradeResultHandler.handleResult(trade);
    }

    private OfferAvailabilityModel getOfferAvailabilityModel(Offer offer) {
        return new OfferAvailabilityModel(
                offer,
                keyRing.getPubKeyRing(),
                p2PService,
                user,
                mediatorManager,
                tradeStatisticsManager);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onWithdrawRequest(String toAddress, Coin amount, Coin fee, KeyParameter aesKey,
                                  Trade trade, ResultHandler resultHandler, FaultHandler faultHandler) {
        String fromAddress = btcWalletService.getOrCreateAddressEntry(trade.getId(),
                AddressEntry.Context.TRADE_PAYOUT).getAddressString();
        FutureCallback<Transaction> callback = new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@javax.annotation.Nullable Transaction transaction) {
                if (transaction != null) {
                    log.debug("onWithdraw onSuccess tx ID:" + transaction.getHashAsString());
                    addTradeToClosedTrades(trade);
                    trade.setState(Trade.State.WITHDRAW_COMPLETED);
                    resultHandler.handleResult();
                }
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                t.printStackTrace();
                log.error(t.getMessage());
                faultHandler.handleFault("An exception occurred at requestWithdraw (onFailure).", t);
            }
        };
        try {
            btcWalletService.sendFunds(fromAddress, toAddress, amount, fee, aesKey, AddressEntry.Context.TRADE_PAYOUT, callback);
        } catch (AddressFormatException | InsufficientMoneyException | AddressEntryException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            faultHandler.handleFault("An exception occurred at requestWithdraw.", e);
        }
    }

    // If trade was completed (closed without fault but might be closed by a dispute) we move it to the closed trades
    public void addTradeToClosedTrades(Trade trade) {
        removeTrade(trade);
        closedTradableManager.add(trade);

        cleanUpAddressEntries();
    }

    // If trade is in already in critical state (if taker role: taker fee; both roles: after deposit published)
    // we move the trade to failedTradesManager
    public void addTradeToFailedTrades(Trade trade) {
        removeTrade(trade);
        failedTradesManager.add(trade);

        cleanUpAddressEntries();
    }

    // If trade is in preparation (if taker role: before taker fee is paid; both roles: before deposit published)
    // we just remove the trade from our list. We don't store those trades.
    public void removePreparedTrade(Trade trade) {
        removeTrade(trade);

        cleanUpAddressEntries();
    }

    private void removeTrade(Trade trade) {
        tradableList.remove(trade);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void closeDisputedTrade(String tradeId, Trade.DisputeState disputeState) {
        Optional<Trade> tradeOptional = getTradeById(tradeId);
        if (tradeOptional.isPresent()) {
            Trade trade = tradeOptional.get();
            trade.setDisputeState(disputeState);
            addTradeToClosedTrades(trade);
            btcWalletService.swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Publish delayed payout tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void publishDelayedPayoutTx(String tradeId,
                                       ResultHandler resultHandler,
                                       ErrorMessageHandler errorMessageHandler) {
        getTradeById(tradeId).ifPresent(trade -> {
            Transaction delayedPayoutTx = trade.getDelayedPayoutTx();
            if (delayedPayoutTx != null) {
                // We have spent the funds from the deposit tx with the delayedPayoutTx
                btcWalletService.swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.MULTI_SIG);
                // We might receive funds on AddressEntry.Context.TRADE_PAYOUT so we don't swap that

                Transaction committedDelayedPayoutTx = WalletService.maybeAddSelfTxToWallet(delayedPayoutTx, btcWalletService.getWallet());

                tradeWalletService.broadcastTx(committedDelayedPayoutTx, new TxBroadcaster.Callback() {
                    @Override
                    public void onSuccess(Transaction transaction) {
                        log.info("publishDelayedPayoutTx onSuccess " + transaction);
                        NodeAddress tradingPeerNodeAddress = trade.getTradingPeerNodeAddress();
                        PeerPublishedDelayedPayoutTxMessage msg = new PeerPublishedDelayedPayoutTxMessage(UUID.randomUUID().toString(),
                                tradeId,
                                tradingPeerNodeAddress);
                        p2PService.sendEncryptedMailboxMessage(
                                tradingPeerNodeAddress,
                                trade.getProcessModel().getTradingPeer().getPubKeyRing(),
                                msg,
                                new SendMailboxMessageListener() {
                                    @Override
                                    public void onArrived() {
                                        resultHandler.handleResult();
                                        log.info("SendMailboxMessageListener onArrived tradeId={} at peer {}",
                                                tradeId, tradingPeerNodeAddress);
                                    }

                                    @Override
                                    public void onStoredInMailbox() {
                                        resultHandler.handleResult();
                                        log.info("SendMailboxMessageListener onStoredInMailbox tradeId={} at peer {}",
                                                tradeId, tradingPeerNodeAddress);
                                    }

                                    @Override
                                    public void onFault(String errorMessage) {
                                        log.error("SendMailboxMessageListener onFault tradeId={} at peer {}",
                                                tradeId, tradingPeerNodeAddress);
                                        errorMessageHandler.handleErrorMessage(errorMessage);
                                    }
                                }
                        );
                    }

                    @Override
                    public void onFailure(TxBroadcastException exception) {
                        log.error("publishDelayedPayoutTx onFailure", exception);
                        errorMessageHandler.handleErrorMessage(exception.toString());
                    }
                });
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<Trade> getTradableList() {
        return tradableList.getList();
    }

    public BooleanProperty pendingTradesInitializedProperty() {
        return pendingTradesInitialized;
    }

    public boolean isMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }

    public boolean isBuyer(Offer offer) {
        // If I am the maker, we use the OfferPayload.Direction, otherwise the mirrored direction
        if (isMyOffer(offer))
            return offer.isBuyOffer();
        else
            return offer.getDirection() == OfferPayload.Direction.SELL;
    }

    public Optional<Trade> getTradeById(String tradeId) {
        return tradableList.stream().filter(e -> e.getId().equals(tradeId)).findFirst();
    }

    public Stream<AddressEntry> getAddressEntriesForAvailableBalanceStream() {
        Stream<AddressEntry> availableOrPayout = Stream.concat(btcWalletService.getAddressEntries(AddressEntry.Context.TRADE_PAYOUT)
                .stream(), btcWalletService.getFundedAvailableAddressEntries().stream());
        Stream<AddressEntry> available = Stream.concat(availableOrPayout,
                btcWalletService.getAddressEntries(AddressEntry.Context.ARBITRATOR).stream());
        available = Stream.concat(available, btcWalletService.getAddressEntries(AddressEntry.Context.OFFER_FUNDING).stream());
        return available.filter(addressEntry -> btcWalletService.getBalanceForAddress(addressEntry.getAddress()).isPositive());
    }

    public Stream<Trade> getTradesStreamWithFundsLockedIn() {
        return getTradableList().stream()
                .filter(Trade::isFundsLockedIn);
    }

    public Set<String> getSetOfFailedOrClosedTradeIdsFromLockedInFunds() throws TradeTxException {
        AtomicReference<TradeTxException> tradeTxException = new AtomicReference<>();
        Set<String> tradesIdSet = getTradesStreamWithFundsLockedIn()
                .filter(Trade::hasFailed)
                .map(Trade::getId)
                .collect(Collectors.toSet());
        tradesIdSet.addAll(failedTradesManager.getTradesStreamWithFundsLockedIn()
                .filter(trade -> trade.getDepositTx() != null)
                .map(trade -> {
                    log.warn("We found a failed trade with locked up funds. " +
                            "That should never happen. trade ID=" + trade.getId());
                    return trade.getId();
                })
                .collect(Collectors.toSet()));
        tradesIdSet.addAll(closedTradableManager.getTradesStreamWithFundsLockedIn()
                .map(trade -> {
                    Transaction depositTx = trade.getDepositTx();
                    if (depositTx != null) {
                        TransactionConfidence confidence = btcWalletService.getConfidenceForTxId(depositTx.getHashAsString());
                        if (confidence != null && confidence.getConfidenceType() != TransactionConfidence.ConfidenceType.BUILDING) {
                            tradeTxException.set(new TradeTxException(Res.get("error.closedTradeWithUnconfirmedDepositTx", trade.getShortId())));
                        } else {
                            log.warn("We found a closed trade with locked up funds. " +
                                    "That should never happen. trade ID=" + trade.getId());
                        }
                    } else {
                        tradeTxException.set(new TradeTxException(Res.get("error.closedTradeWithNoDepositTx", trade.getShortId())));
                    }
                    return trade.getId();
                })
                .collect(Collectors.toSet()));

        if (tradeTxException.get() != null)
            throw tradeTxException.get();

        return tradesIdSet;
    }

    public void applyTradePeriodState() {
        updateTradePeriodState();
        clockWatcher.addListener(new ClockWatcher.Listener() {
            @Override
            public void onSecondTick() {
            }

            @Override
            public void onMinuteTick() {
                updateTradePeriodState();
            }
        });
    }

    private void updateTradePeriodState() {
        tradableList.getList().forEach(trade -> {
            if (!trade.isPayoutPublished()) {
                Date maxTradePeriodDate = trade.getMaxTradePeriodDate();
                Date halfTradePeriodDate = trade.getHalfTradePeriodDate();
                if (maxTradePeriodDate != null && halfTradePeriodDate != null) {
                    Date now = new Date();
                    if (now.after(maxTradePeriodDate))
                        trade.setTradePeriodState(Trade.TradePeriodState.TRADE_PERIOD_OVER);
                    else if (now.after(halfTradePeriodDate))
                        trade.setTradePeriodState(Trade.TradePeriodState.SECOND_HALF);
                }
            }
        });
    }

    public void persistTrades() {
        tradableList.persist();
    }
}
