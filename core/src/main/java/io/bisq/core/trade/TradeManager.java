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

package io.bisq.core.trade;

import com.google.common.util.concurrent.FutureCallback;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.FaultHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.storage.Storage;
import io.bisq.core.arbitration.ArbitratorManager;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.AddressEntryException;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.TradeWalletService;
import io.bisq.core.filter.FilterManager;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.offer.availability.OfferAvailabilityModel;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.trade.closed.ClosedTradableManager;
import io.bisq.core.trade.failed.FailedTradesManager;
import io.bisq.core.trade.handlers.TradeResultHandler;
import io.bisq.core.trade.statistics.TradeStatisticsManager;
import io.bisq.core.user.User;
import io.bisq.core.util.Validator;
import io.bisq.network.p2p.BootstrapListener;
import io.bisq.network.p2p.DecryptedDirectMessageListener;
import io.bisq.network.p2p.DecryptedMsgWithPubKey;
import io.bisq.network.p2p.messaging.DecryptedMailboxListener;
import io.bisq.network.p2p.storage.P2PService;
import io.bisq.protobuffer.crypto.KeyRing;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.message.trade.PayDepositRequest;
import io.bisq.protobuffer.message.trade.TradeMessage;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import io.bisq.protobuffer.payload.trade.statistics.TradeStatistics;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

public class TradeManager {
    private static final Logger log = LoggerFactory.getLogger(TradeManager.class);

    private final User user;
    private final KeyRing keyRing;
    private final BtcWalletService walletService;
    private final TradeWalletService tradeWalletService;
    private final OpenOfferManager openOfferManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final ArbitratorManager arbitratorManager;
    private final P2PService p2PService;
    private final FilterManager filterManager;
    private final TradeStatisticsManager tradeStatisticsManager;

    private final Storage<TradableList<Trade>> tradableListStorage;
    private final TradableList<Trade> trades;
    private final BooleanProperty pendingTradesInitialized = new SimpleBooleanProperty();
    private boolean stopped;
    private List<Trade> tradesForStatistics;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeManager(User user,
                        KeyRing keyRing,
                        BtcWalletService walletService,
                        TradeWalletService tradeWalletService,
                        OpenOfferManager openOfferManager,
                        ClosedTradableManager closedTradableManager,
                        FailedTradesManager failedTradesManager,
                        ArbitratorManager arbitratorManager,
                        P2PService p2PService,
                        PriceFeedService priceFeedService,
                        FilterManager filterManager,
                        TradeStatisticsManager tradeStatisticsManager,
                        @Named(Storage.DIR_KEY) File storageDir) {
        this.user = user;
        this.keyRing = keyRing;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.openOfferManager = openOfferManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.arbitratorManager = arbitratorManager;
        this.p2PService = p2PService;
        this.filterManager = filterManager;
        this.tradeStatisticsManager = tradeStatisticsManager;

        tradableListStorage = new Storage<>(storageDir);
        trades = new TradableList<>(tradableListStorage, "PendingTrades");
        trades.forEach(e -> e.getOffer().setPriceFeedService(priceFeedService));

        p2PService.addDecryptedDirectMessageListener(new DecryptedDirectMessageListener() {
            @Override
            public void onDirectMessage(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress peerNodeAddress) {
                Message message = decryptedMsgWithPubKey.message;

                // Handler for incoming initial network_messages from taker
                if (message instanceof PayDepositRequest) {
                    log.trace("Received PayDepositRequest: " + message);
                    handleInitialTakeOfferRequest((PayDepositRequest) message, peerNodeAddress);
                }
            }
        });
        p2PService.addDecryptedMailboxListener(new DecryptedMailboxListener() {
            @Override
            public void onMailboxMessageAdded(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress senderNodeAddress) {
                log.trace("onMailboxMessageAdded decryptedMessageWithPubKey: " + decryptedMsgWithPubKey);
                log.trace("onMailboxMessageAdded senderAddress: " + senderNodeAddress);
                Message message = decryptedMsgWithPubKey.message;
                if (message instanceof PayDepositRequest) {
                    PayDepositRequest payDepositRequest = (PayDepositRequest) message;
                    log.trace("Received payDepositRequest: " + payDepositRequest);
                    if (payDepositRequest.getSenderNodeAddress().equals(senderNodeAddress))
                        handleInitialTakeOfferRequest(payDepositRequest, senderNodeAddress);
                    else
                        log.warn("Peer address not matching for payDepositRequest");
                } else if (message instanceof TradeMessage) {
                    log.trace("Received TradeMessage: " + message);
                    String tradeId = ((TradeMessage) message).tradeId;
                    Optional<Trade> tradeOptional = trades.stream().filter(e -> e.getId().equals(tradeId)).findAny();
                    // The mailbox message will be removed inside the tasks after they are processed successfully
                    if (tradeOptional.isPresent())
                        tradeOptional.get().setMailboxMessage(decryptedMsgWithPubKey);
                }
            }
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
        stopped = true;
    }

    private void initPendingTrades() {
        Log.traceCall();

        List<Trade> addTradeToFailedTradesList = new ArrayList<>();
        List<Trade> removePreparedTradeList = new ArrayList<>();
        tradesForStatistics = new ArrayList<>();
        for (Trade trade : trades) {
            trade.setStorage(tradableListStorage);

            if (trade.isDepositPaid() || (trade.isTakerFeePaid() && trade.errorMessageProperty().get() == null)) {
                initTrade(trade, trade.getProcessModel().getUseSavingsWallet(), trade.getProcessModel().getFundsNeededForTrade());
                trade.updateDepositTxFromWallet();
                tradesForStatistics.add(trade);
            } else if (trade.isTakerFeePaid()) {
                addTradeToFailedTradesList.add(trade);
            } else {
                removePreparedTradeList.add(trade);
            }
        }

        for (Trade trade : addTradeToFailedTradesList)
            addTradeToFailedTrades(trade);

        for (Trade trade : removePreparedTradeList)
            removePreparedTrade(trade);

        tradesForStatistics.addAll(closedTradableManager.getClosedTrades().stream()
                .filter(tradable -> tradable instanceof Trade).map(tradable -> (Trade) tradable)
                .collect(Collectors.toList()));

        // We start later to have better connectivity to the network
        UserThread.runAfter(() -> publishTradeStatistics(tradesForStatistics),
                90, TimeUnit.SECONDS);

        pendingTradesInitialized.set(true);
    }

    private void publishTradeStatistics(List<Trade> trades) {
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            TradeStatistics tradeStatistics = new TradeStatistics(trade.getOffer().getOfferPayload(),
                    trade.getTradePrice(),
                    trade.getTradeAmount(),
                    trade.getDate(),
                    (trade.getDepositTx() != null ? trade.getDepositTx().getHashAsString() : ""),
                    keyRing.getPubKeyRing());
            tradeStatisticsManager.add(tradeStatistics, true);

            // We only republish trades from last 10 days
            // TODO check if needed at all. Don't want to remove it atm to not risk anything.
            // But we could check which tradeStatistics we received from the seed nodes and 
            // only re-publish in case tradeStatistics are missing.
            if ((new Date().getTime() - trade.getDate().getTime()) < TimeUnit.DAYS.toMillis(10)) {
                long delay = 5000;
                final long minDelay = (i + 1) * delay;
                final long maxDelay = (i + 2) * delay;
                UserThread.runAfterRandomDelay(() -> {
                    if (!stopped)
                        p2PService.addData(tradeStatistics, true);
                }, minDelay, maxDelay, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void handleInitialTakeOfferRequest(TradeMessage message, NodeAddress peerNodeAddress) {
        log.trace("handleNewMessage: message = " + message.getClass().getSimpleName() + " from " + peerNodeAddress);
        try {
            Validator.nonEmptyStringOf(message.tradeId);
        } catch (Throwable t) {
            log.warn("Invalid requestDepositTxInputsMessage " + message.toString());
            return;
        }

        Optional<OpenOffer> openOfferOptional = openOfferManager.findOpenOffer(message.tradeId);
        if (openOfferOptional.isPresent() && openOfferOptional.get().getState() == OpenOffer.State.AVAILABLE) {
            Offer offer = openOfferOptional.get().getOffer();
            openOfferManager.reserveOpenOffer(openOfferOptional.get());

            checkArgument(message instanceof PayDepositRequest, "message must be PayDepositRequest");
            PayDepositRequest payDepositRequest = (PayDepositRequest) message;
            Trade trade;
            if (offer.isBuyOffer())
                trade = new BuyerAsOffererTrade(offer, payDepositRequest.txFee, payDepositRequest.takeOfferFee, tradableListStorage);
            else
                trade = new SellerAsOffererTrade(offer, payDepositRequest.txFee, payDepositRequest.takeOfferFee, tradableListStorage);

            trade.setStorage(tradableListStorage);
            initTrade(trade, trade.getProcessModel().getUseSavingsWallet(), trade.getProcessModel().getFundsNeededForTrade());
            trades.add(trade);
            ((OffererTrade) trade).handleTakeOfferRequest(message, peerNodeAddress);
        } else {
            // TODO respond
            //(RequestDepositTxInputsMessage)message.
            //  messageService.sendEncryptedMessage(peerAddress,messageWithPubKey.getMessage().);
            log.debug("We received a take offer request but don't have that offer anymore.");
        }
    }

    private void initTrade(Trade trade, boolean useSavingsWallet, Coin fundsNeededForTrade) {
        trade.init(p2PService,
                walletService,
                tradeWalletService,
                arbitratorManager,
                this,
                openOfferManager,
                user,
                filterManager,
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
                            Coin takeOfferFee,
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
                        createTrade(amount, txFee, takeOfferFee, tradePrice, fundsNeededForTrade, offer, paymentAccountId, useSavingsWallet, model, tradeResultHandler);
                },
                errorMessageHandler::handleErrorMessage);
    }

    private void createTrade(Coin amount,
                             Coin txFee,
                             Coin takeOfferFee,
                             long tradePrice,
                             Coin fundsNeededForTrade,
                             Offer offer,
                             String paymentAccountId,
                             boolean useSavingsWallet,
                             OfferAvailabilityModel model,
                             TradeResultHandler tradeResultHandler) {
        Trade trade;
        if (offer.isBuyOffer())
            trade = new SellerAsTakerTrade(offer, amount, txFee, takeOfferFee, tradePrice, model.getPeerNodeAddress(), tradableListStorage);
        else
            trade = new BuyerAsTakerTrade(offer, amount, txFee, takeOfferFee, tradePrice, model.getPeerNodeAddress(), tradableListStorage);

        trade.setTakerPaymentAccountId(paymentAccountId);

        initTrade(trade, useSavingsWallet, fundsNeededForTrade);

        trades.add(trade);
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

    public void onWithdrawRequest(String toAddress, Coin amount, Coin fee, KeyParameter aesKey, Trade trade, ResultHandler resultHandler, FaultHandler faultHandler) {
        String fromAddress = walletService.getOrCreateAddressEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT).getAddressString();
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
            walletService.sendFunds(fromAddress, toAddress, amount, fee, aesKey, AddressEntry.Context.TRADE_PAYOUT, callback);
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
    }

    // If trade is in already in critical state (if taker role: taker fee; both roles: after deposit published)
    // we move the trade to failedTradesManager
    public void addTradeToFailedTrades(Trade trade) {
        removeTrade(trade);
        failedTradesManager.add(trade);
    }

    // If trade is in preparation (if taker role: before taker fee is paid; both roles: before deposit published)
    // we just remove the trade from our list. We don't store those trades.
    public void removePreparedTrade(Trade trade) {
        removeTrade(trade);
    }

    private void removeTrade(Trade trade) {
        trades.remove(trade);
        if (!openOfferManager.findOpenOffer(trade.getId()).isPresent())
            walletService.swapAnyTradeEntryContextToAvailableEntry(trade.getId());
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
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<Trade> getTrades() {
        return trades.getObservableList();
    }

    public BooleanProperty pendingTradesInitializedProperty() {
        return pendingTradesInitialized;
    }

    public boolean isMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }

    public boolean isBuyer(Offer offer) {
        // If I am the offerer, we use the offer direction, otherwise the mirrored direction
        if (isMyOffer(offer))
            return offer.isBuyOffer();
        else
            return offer.getDirection() == Offer.Direction.SELL;
    }

    public Optional<Trade> getTradeById(String tradeId) {
        return trades.stream().filter(e -> e.getId().equals(tradeId)).findFirst();
    }

    public Stream<AddressEntry> getAddressEntriesForAvailableBalanceStream() {
        Stream<AddressEntry> availableOrPayout = Stream.concat(walletService.getAddressEntries(AddressEntry.Context.TRADE_PAYOUT).stream(), walletService.getFundedAvailableAddressEntries().stream());
        Stream<AddressEntry> available = Stream.concat(availableOrPayout, walletService.getAddressEntries(AddressEntry.Context.ARBITRATOR).stream());
        available = Stream.concat(available, walletService.getAddressEntries(AddressEntry.Context.OFFER_FUNDING).stream());
        return available
                .filter(addressEntry -> walletService.getBalanceForAddress(addressEntry.getAddress()).isPositive());
    }

    public Stream<Trade> getLockedTradeStream() {
        return getTrades().stream()
                .filter(trade -> trade.getState().getPhase().ordinal() >= Trade.Phase.DEPOSIT_PAID.ordinal() &&
                        trade.getState().getPhase().ordinal() < Trade.Phase.PAYOUT_PAID.ordinal());
    }
}