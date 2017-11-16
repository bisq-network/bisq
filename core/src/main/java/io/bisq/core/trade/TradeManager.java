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

package io.bisq.core.trade;

import com.google.common.util.concurrent.FutureCallback;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.FaultHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.AddressEntryException;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.TradeWalletService;
import io.bisq.core.filter.FilterManager;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.offer.availability.OfferAvailabilityModel;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.trade.closed.ClosedTradableManager;
import io.bisq.core.trade.failed.FailedTradesManager;
import io.bisq.core.trade.handlers.TradeResultHandler;
import io.bisq.core.trade.messages.PayDepositRequest;
import io.bisq.core.trade.messages.TradeMessage;
import io.bisq.core.trade.statistics.TradeStatisticsManager;
import io.bisq.core.user.User;
import io.bisq.core.util.Validator;
import io.bisq.network.p2p.*;
import io.bisq.network.p2p.messaging.DecryptedMailboxListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import lombok.Setter;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

public class TradeManager implements PersistedDataHost {
    private static final Logger log = LoggerFactory.getLogger(TradeManager.class);

    private final User user;
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
    private final AccountAgeWitnessService accountAgeWitnessService;

    private final Storage<TradableList<Trade>> tradableListStorage;
    private TradableList<Trade> tradableList;
    private final BooleanProperty pendingTradesInitialized = new SimpleBooleanProperty();
    private List<Trade> tradesForStatistics;
    @Setter
    @Nullable
    private ErrorMessageHandler takeOfferRequestErrorMessageHandler;


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
                        PersistenceProtoResolver persistenceProtoResolver,
                        AccountAgeWitnessService accountAgeWitnessService,
                        @Named(Storage.STORAGE_DIR) File storageDir) {
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
        this.accountAgeWitnessService = accountAgeWitnessService;

        tradableListStorage = new Storage<>(storageDir, persistenceProtoResolver);

        p2PService.addDecryptedDirectMessageListener(new DecryptedDirectMessageListener() {
            @Override
            public void onDirectMessage(DecryptedMessageWithPubKey decryptedMessageWithPubKey, NodeAddress peerNodeAddress) {
                NetworkEnvelope networkEnvelop = decryptedMessageWithPubKey.getNetworkEnvelope();

                // Handler for incoming initial network_messages from taker
                if (networkEnvelop instanceof PayDepositRequest) {
                    log.trace("Received PayDepositRequest: " + networkEnvelop);
                    handleInitialTakeOfferRequest((PayDepositRequest) networkEnvelop, peerNodeAddress);
                }
            }
        });

        // Might get called at startup after HS is published. Can be before or after initPendingTrades.
        p2PService.addDecryptedMailboxListener(new DecryptedMailboxListener() {
            @Override
            public void onMailboxMessageAdded(DecryptedMessageWithPubKey decryptedMessageWithPubKey, NodeAddress senderNodeAddress) {
                log.debug("onMailboxMessageAdded decryptedMessageWithPubKey: " + decryptedMessageWithPubKey);
                log.trace("onMailboxMessageAdded senderAddress: " + senderNodeAddress);
                NetworkEnvelope networkEnvelop = decryptedMessageWithPubKey.getNetworkEnvelope();
                if (networkEnvelop instanceof TradeMessage) {
                    log.trace("Received TradeMessage: " + networkEnvelop);
                    String tradeId = ((TradeMessage) networkEnvelop).getTradeId();
                    Optional<Trade> tradeOptional = tradableList.stream().filter(e -> e.getId().equals(tradeId)).findAny();
                    // The mailbox message will be removed inside the tasks after they are processed successfully
                    if (tradeOptional.isPresent())
                        tradeOptional.get().addDecryptedMessageWithPubKey(decryptedMessageWithPubKey);
                }
            }
        });
    }

    @Override
    public void readPersisted() {
        tradableList = new TradableList<>(tradableListStorage, "PendingTrades");
        tradableList.forEach(trade -> {
            trade.setTransientFields(tradableListStorage, btcWalletService);
            trade.getOffer().setPriceFeedService(priceFeedService);
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        Log.traceCall();
        if (p2PService.isBootstrapped())
            initPendingTrades();
        else
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onBootstrapComplete() {
                    // Get called after onMailboxMessageAdded from initial data request
                    // The mailbox message will be removed inside the tasks after they are processed successfully
                    initPendingTrades();
                }
            });
    }

    public void shutDown() {
    }

    private void initPendingTrades() {
        Log.traceCall();

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
                    } else {
                        removePreparedTradeList.add(trade);
                    }
                }
        );

        addTradeToFailedTradesList.forEach(this::addTradeToFailedTrades);

        removePreparedTradeList.forEach(this::removePreparedTrade);

        cleanUpAddressEntries();

        // TODO remove once we support Taker side publishing at take offer process
        // We start later to have better connectivity to the network
        UserThread.runAfter(() -> tradeStatisticsManager.publishTradeStatistics(tradesForStatistics),
                30, TimeUnit.SECONDS);

        pendingTradesInitialized.set(true);
    }

    public void cleanUpAddressEntries() {
        Set<String> tradesIdSet = getLockedTradesStream()
                .map(Trade::getId)
                .collect(Collectors.toSet());

        tradesIdSet.addAll(failedTradesManager.getLockedTradesStream()
                .map(Trade::getId)
                .collect(Collectors.toSet()));

        tradesIdSet.addAll(closedTradableManager.getLockedTradesStream()
                .map(Tradable::getId)
                .collect(Collectors.toSet()));

        btcWalletService.getAddressEntriesForTrade().stream()
                .filter(e -> !tradesIdSet.contains(e.getOfferId()))
                .forEach(e -> {
                    log.warn("We found an outdated addressEntry for trade {}", e.getOfferId());
                    btcWalletService.resetAddressEntriesForPendingTrade(e.getOfferId());
                });
    }

    private void handleInitialTakeOfferRequest(TradeMessage message, NodeAddress peerNodeAddress) {
        log.trace("handleNewMessage: message = " + message.getClass().getSimpleName() + " from " + peerNodeAddress);
        try {
            Validator.nonEmptyStringOf(message.getTradeId());
        } catch (Throwable t) {
            log.warn("Invalid requestDepositTxInputsMessage " + message.toString());
            return;
        }

        Optional<OpenOffer> openOfferOptional = openOfferManager.findOpenOffer(message.getTradeId());
        if (openOfferOptional.isPresent() && openOfferOptional.get().getState() == OpenOffer.State.AVAILABLE) {
            Offer offer = openOfferOptional.get().getOffer();
            openOfferManager.reserveOpenOffer(openOfferOptional.get());

            checkArgument(message instanceof PayDepositRequest, "message must be PayDepositRequest");
            PayDepositRequest payDepositRequest = (PayDepositRequest) message;
            Trade trade;
            if (offer.isBuyOffer())
                trade = new BuyerAsMakerTrade(offer,
                        Coin.valueOf(payDepositRequest.getTxFee()),
                        Coin.valueOf(payDepositRequest.getTakerFee()),
                        payDepositRequest.isCurrencyForTakerFeeBtc(),
                        tradableListStorage,
                        btcWalletService);
            else
                trade = new SellerAsMakerTrade(offer,
                        Coin.valueOf(payDepositRequest.getTxFee()),
                        Coin.valueOf(payDepositRequest.getTakerFee()),
                        payDepositRequest.isCurrencyForTakerFeeBtc(),
                        tradableListStorage,
                        btcWalletService);

            initTrade(trade, trade.getProcessModel().isUseSavingsWallet(), trade.getProcessModel().getFundsNeededForTradeAsLong());
            tradableList.add(trade);
            ((MakerTrade) trade).handleTakeOfferRequest(message, peerNodeAddress, errorMessage -> {
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
                this,
                openOfferManager,
                user,
                filterManager,
                accountAgeWitnessService,
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
                p2PService);
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

    public void closeDisputedTrade(String tradeId) {
        Optional<Trade> tradeOptional = getTradeById(tradeId);
        if (tradeOptional.isPresent()) {
            Trade trade = tradeOptional.get();
            trade.setDisputeState(Trade.DisputeState.DISPUTE_CLOSED);
            addTradeToClosedTrades(trade);
            btcWalletService.swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT);
        }
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

    public Stream<Trade> getLockedTradesStream() {
        return getTradableList().stream()
                .filter(Trade::isFundsLockedIn);
    }
}
