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

package bisq.core.trade.model;

import bisq.core.btc.exceptions.AddressEntryException;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayloadBase;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.availability.OfferAvailabilityModel;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.trade.messages.bsqswap.CreateAtomicTxRequest;
import bisq.core.trade.messages.trade.InputsForDepositTxRequest;
import bisq.core.trade.misc.ClosedTradableManager;
import bisq.core.trade.misc.DumpDelayedPayoutTx;
import bisq.core.trade.misc.FailedTradesManager;
import bisq.core.trade.misc.TradeResultHandler;
import bisq.core.trade.misc.TradeTxException;
import bisq.core.trade.misc.TradeUtil;
import bisq.core.trade.model.bsqswap.BsqSwapMakerTrade;
import bisq.core.trade.model.bsqswap.BsqSwapTakerTrade;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.model.bsqswap.BsqSwapTradeManager;
import bisq.core.trade.model.trade.BuyerAsMakerTrade;
import bisq.core.trade.model.trade.BuyerAsTakerTrade;
import bisq.core.trade.model.trade.SellerAsMakerTrade;
import bisq.core.trade.model.trade.SellerAsTakerTrade;
import bisq.core.trade.model.trade.Trade;
import bisq.core.trade.protocol.MakerProtocol;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.TakerProtocol;
import bisq.core.trade.protocol.TradeProtocol;
import bisq.core.trade.protocol.bsqswap.BsqSwapMakerProtocol;
import bisq.core.trade.protocol.bsqswap.BsqSwapProtocolModel;
import bisq.core.trade.protocol.trade.ProcessModel;
import bisq.core.trade.protocol.trade.TradeProtocolFactory;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;
import bisq.core.util.Validator;

import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.DecryptedDirectMessageListener;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.TorNetworkNode;

import bisq.common.ClockWatcher;
import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.FaultHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.persistable.PersistedDataHost;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.util.concurrent.FutureCallback;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import org.bouncycastle.crypto.params.KeyParameter;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class TradeManager implements PersistedDataHost, DecryptedDirectMessageListener {
    private static final Logger log = LoggerFactory.getLogger(TradeManager.class);

    private final User user;
    @Getter
    private final KeyRing keyRing;
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final OpenOfferManager openOfferManager;
    private final ClosedTradableManager closedTradableManager;
    private final BsqSwapTradeManager bsqSwapTradeManager;
    private final FailedTradesManager failedTradesManager;
    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final TradeUtil tradeUtil;
    @Getter
    private final ArbitratorManager arbitratorManager;
    private final MediatorManager mediatorManager;
    private final Provider provider;
    private final ClockWatcher clockWatcher;

    private final Map<String, TradeProtocol> tradeProtocolByTradeId = new HashMap<>();
    private final PersistenceManager<TradableList<TradeModel>> persistenceManager;
    private final TradableList<TradeModel> tradableList = new TradableList<>();
    @Getter
    private final BooleanProperty persistedTradesInitialized = new SimpleBooleanProperty();
    @Setter
    @Nullable
    private ErrorMessageHandler takeOfferRequestErrorMessageHandler;
    @Getter
    private final LongProperty numPendingTrades = new SimpleLongProperty();
    private final ReferralIdService referralIdService;
    private final DumpDelayedPayoutTx dumpDelayedPayoutTx;
    @Getter
    private final boolean allowFaultyDelayedTxs;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeManager(User user,
                        KeyRing keyRing,
                        BtcWalletService btcWalletService,
                        BsqWalletService bsqWalletService,
                        OpenOfferManager openOfferManager,
                        ClosedTradableManager closedTradableManager,
                        BsqSwapTradeManager bsqSwapTradeManager,
                        FailedTradesManager failedTradesManager,
                        P2PService p2PService,
                        PriceFeedService priceFeedService,
                        TradeStatisticsManager tradeStatisticsManager,
                        TradeUtil tradeUtil,
                        ArbitratorManager arbitratorManager,
                        MediatorManager mediatorManager,
                        Provider provider,
                        ClockWatcher clockWatcher,
                        PersistenceManager<TradableList<TradeModel>> persistenceManager,
                        ReferralIdService referralIdService,
                        DumpDelayedPayoutTx dumpDelayedPayoutTx,
                        @Named(Config.ALLOW_FAULTY_DELAYED_TXS) boolean allowFaultyDelayedTxs) {
        this.user = user;
        this.keyRing = keyRing;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.openOfferManager = openOfferManager;
        this.closedTradableManager = closedTradableManager;
        this.bsqSwapTradeManager = bsqSwapTradeManager;
        this.failedTradesManager = failedTradesManager;
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.tradeUtil = tradeUtil;
        this.arbitratorManager = arbitratorManager;
        this.mediatorManager = mediatorManager;
        this.provider = provider;
        this.clockWatcher = clockWatcher;
        this.referralIdService = referralIdService;
        this.dumpDelayedPayoutTx = dumpDelayedPayoutTx;
        this.allowFaultyDelayedTxs = allowFaultyDelayedTxs;
        this.persistenceManager = persistenceManager;

        this.persistenceManager.initialize(tradableList, "PendingTrades", PersistenceManager.Source.PRIVATE);

        p2PService.addDecryptedDirectMessageListener(this);

        failedTradesManager.setUnFailTradeCallback(this::unFailTrade);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted(Runnable completeHandler) {
        persistenceManager.readPersisted(persisted -> {
                    tradableList.setAll(persisted.getList());
                    tradableList.stream()
                            .filter(tradeModel -> tradeModel.getOffer() != null)
                            .forEach(tradeModel -> tradeModel.getOffer().setPriceFeedService(priceFeedService));
                    dumpDelayedPayoutTx.maybeDumpDelayedPayoutTxs(tradableList, "delayed_payout_txs_pending");
                    completeHandler.run();
                },
                completeHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DecryptedDirectMessageListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onDirectMessage(DecryptedMessageWithPubKey message, NodeAddress peer) {
        NetworkEnvelope networkEnvelope = message.getNetworkEnvelope();
        if (networkEnvelope instanceof InputsForDepositTxRequest) {
            handleTakeOfferRequest(peer, (InputsForDepositTxRequest) networkEnvelope);
        } else if (networkEnvelope instanceof CreateAtomicTxRequest) {
            handleTakeAtomicOfferRequest(peer, (CreateAtomicTxRequest) networkEnvelope);
        }
    }

    // The maker received a TakeOfferRequest
    private void handleTakeOfferRequest(NodeAddress peer, InputsForDepositTxRequest inputsForDepositTxRequest) {
        log.info("Received inputsForDepositTxRequest from {} with tradeId {} and uid {}",
                peer, inputsForDepositTxRequest.getTradeId(), inputsForDepositTxRequest.getUid());

        try {
            Validator.nonEmptyStringOf(inputsForDepositTxRequest.getTradeId());
        } catch (Throwable t) {
            log.warn("Invalid inputsForDepositTxRequest " + inputsForDepositTxRequest.toString());
            return;
        }

        Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(inputsForDepositTxRequest.getTradeId());
        if (!openOfferOptional.isPresent()) {
            return;
        }

        OpenOffer openOffer = openOfferOptional.get();
        if (openOffer.getState() != OpenOffer.State.AVAILABLE) {
            return;
        }

        Offer offer = openOffer.getOffer();
        openOfferManager.reserveOpenOffer(openOffer);
        Trade trade;
        if (offer.isBuyOffer()) {
            trade = new BuyerAsMakerTrade(offer,
                    Coin.valueOf(inputsForDepositTxRequest.getTxFee()),
                    Coin.valueOf(inputsForDepositTxRequest.getTakerFee()),
                    inputsForDepositTxRequest.isCurrencyForTakerFeeBtc(),
                    openOffer.getArbitratorNodeAddress(),
                    openOffer.getMediatorNodeAddress(),
                    openOffer.getRefundAgentNodeAddress(),
                    btcWalletService,
                    getNewProcessModel(offer),
                    UUID.randomUUID().toString());
        } else {
            trade = new SellerAsMakerTrade(offer,
                    Coin.valueOf(inputsForDepositTxRequest.getTxFee()),
                    Coin.valueOf(inputsForDepositTxRequest.getTakerFee()),
                    inputsForDepositTxRequest.isCurrencyForTakerFeeBtc(),
                    openOffer.getArbitratorNodeAddress(),
                    openOffer.getMediatorNodeAddress(),
                    openOffer.getRefundAgentNodeAddress(),
                    btcWalletService,
                    getNewProcessModel(offer),
                    UUID.randomUUID().toString());
        }

        TradeProtocol tradeProtocol = createTradeProtocol(trade);

        initTradeAndProtocol(trade, tradeProtocol);

        ((MakerProtocol) tradeProtocol).handleTakeOfferRequest(inputsForDepositTxRequest, peer, errorMessage -> {
            if (takeOfferRequestErrorMessageHandler != null)
                takeOfferRequestErrorMessageHandler.handleErrorMessage(errorMessage);
        });

        requestPersistence();
    }

    // The maker received a TakeOfferRequest
    private void handleTakeAtomicOfferRequest(NodeAddress peer, CreateAtomicTxRequest createAtomicTxRequest) {
        log.info("Received createAtomicTxRequest from {} with tradeId {} and uid {}",
                peer, createAtomicTxRequest.getTradeId(), createAtomicTxRequest.getUid());

        try {
            Validator.nonEmptyStringOf(createAtomicTxRequest.getTradeId());
        } catch (Throwable t) {
            log.warn("Invalid inputsForDepositTxRequest " + createAtomicTxRequest.toString());
            return;
        }

        Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(createAtomicTxRequest.getTradeId());
        if (!openOfferOptional.isPresent()) {
            return;
        }

        OpenOffer openOffer = openOfferOptional.get();
        if (openOffer.getState() != OpenOffer.State.AVAILABLE) {
            return;
        }

        Offer offer = openOffer.getOffer();
        openOfferManager.reserveOpenOffer(openOffer);
        BsqSwapTrade bsqSwapTrade = new BsqSwapMakerTrade(
                UUID.randomUUID().toString(),
                offer,
                Coin.valueOf(createAtomicTxRequest.getBtcTradeAmount()),
                createAtomicTxRequest.getTradePrice(),
                new Date().getTime(),
                createAtomicTxRequest.getSenderNodeAddress(),
                createAtomicTxRequest.getTxFeePerVbyte(),
                createAtomicTxRequest.getMakerFee(),
                createAtomicTxRequest.getTakerFee(),
                new BsqSwapProtocolModel(keyRing.getPubKeyRing()),
                "",
                BsqSwapTrade.State.PREPARATION);

        TradeProtocol tradeProtocol = createTradeProtocol(bsqSwapTrade);

        initTradeAndProtocol(bsqSwapTrade, tradeProtocol);

        ((BsqSwapMakerProtocol) tradeProtocol).handleTakeAtomicRequest(createAtomicTxRequest, peer, errorMessage -> {
            if (takeOfferRequestErrorMessageHandler != null)
                takeOfferRequestErrorMessageHandler.handleErrorMessage(errorMessage);
        });

        requestPersistence();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        if (p2PService.isBootstrapped()) {
            initPersistedTrades();
        } else {
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onUpdatedDataReceived() {
                    initPersistedTrades();
                }
            });
        }

        getObservableList().addListener((ListChangeListener<TradeModel>) change -> onTradesChanged());
        onTradesChanged();

        btcWalletService.getAddressEntriesForAvailableBalanceStream()
                .filter(addressEntry -> addressEntry.getOfferId() != null)
                .forEach(addressEntry -> {
                    log.warn("Swapping pending OFFER_FUNDING entries at startup. offerId={}", addressEntry.getOfferId());
                    btcWalletService.swapTradeEntryToAvailableEntry(addressEntry.getOfferId(), AddressEntry.Context.OFFER_FUNDING);
                });
    }

    public TradeProtocol getTradeProtocol(TradeModel trade) {
        String uid = trade.getUid();
        if (tradeProtocolByTradeId.containsKey(uid)) {
            return tradeProtocolByTradeId.get(uid);
        } else {
            TradeProtocol tradeProtocol = TradeProtocolFactory.getNewTradeProtocol(trade);
            TradeProtocol prev = tradeProtocolByTradeId.put(uid, tradeProtocol);
            if (prev != null) {
                log.error("We had already an entry with uid {}", trade.getUid());
            }

            return tradeProtocol;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Init pending trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void initPersistedTrades() {
        tradableList.forEach(this::initPersistedTrade);
        persistedTradesInitialized.set(true);

        // We do not include failed trades as they should not be counted anyway in the trade statistics
        Set<TradeModel> allTrades = new HashSet<>(closedTradableManager.getClosedTrades());
        allTrades.addAll(bsqSwapTradeManager.getBsqSwapTrades());
        allTrades.addAll(tradableList.getList());
        String referralId = referralIdService.getOptionalReferralId().orElse(null);
        boolean isTorNetworkNode = p2PService.getNetworkNode() instanceof TorNetworkNode;
        tradeStatisticsManager.maybeRepublishTradeStatistics(allTrades, referralId, isTorNetworkNode);
    }

    private void initPersistedTrade(TradeModel tradeModel) {
        initTradeAndProtocol(tradeModel, getTradeProtocol(tradeModel));

        if (tradeModel instanceof Trade) {
            ((Trade) tradeModel).updateDepositTxFromWallet();
        }
        requestPersistence();
    }

    private void initTradeAndProtocol(TradeModel tradeModel, TradeProtocol tradeProtocol) {
        tradeProtocol.initialize(provider, this, tradeModel.getOffer());
        if (tradeModel instanceof Trade) {
            ((Trade) tradeModel).initialize(provider);
        }
        requestPersistence();
    }

    public void requestPersistence() {
        persistenceManager.requestPersistence();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Take offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void checkOfferAvailability(Offer offer,
                                       boolean isTakerApiUser,
                                       ResultHandler resultHandler,
                                       ErrorMessageHandler errorMessageHandler) {
        if (btcWalletService.isUnconfirmedTransactionsLimitHit() ||
                bsqWalletService.isUnconfirmedTransactionsLimitHit()) {
            String errorMessage = Res.get("shared.unconfirmedTransactionsLimitReached");
            errorMessageHandler.handleErrorMessage(errorMessage);
            log.warn(errorMessage);
            return;
        }

        offer.checkOfferAvailability(getOfferAvailabilityModel(offer, isTakerApiUser), resultHandler, errorMessageHandler);
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
                            boolean isTakerApiUser,
                            TradeResultHandler<Trade> tradeResultHandler,
                            ErrorMessageHandler errorMessageHandler) {

        checkArgument(!wasOfferAlreadyUsedInTrade(offer.getId()));

        OfferAvailabilityModel model = getOfferAvailabilityModel(offer, isTakerApiUser);
        offer.checkOfferAvailability(model,
                () -> {
                    if (offer.getState() == Offer.State.AVAILABLE) {
                        Trade trade;
                        if (offer.isBuyOffer()) {
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
                                    btcWalletService,
                                    getNewProcessModel(offer),
                                    UUID.randomUUID().toString());
                        } else {
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
                                    btcWalletService,
                                    getNewProcessModel(offer),
                                    UUID.randomUUID().toString());
                        }
                        trade.getProcessModel().setUseSavingsWallet(useSavingsWallet);
                        trade.getProcessModel().setFundsNeededForTradeAsLong(fundsNeededForTrade.value);
                        trade.setTakerPaymentAccountId(paymentAccountId);

                        TradeProtocol tradeProtocol = createTradeProtocol(trade);

                        initTradeAndProtocol(trade, tradeProtocol);

                        ((TakerProtocol) tradeProtocol).onTakeOffer();
                        tradeResultHandler.handleResult(trade);
                        requestPersistence();
                    }
                },
                errorMessageHandler);

        requestPersistence();
    }

    public void onTakeAtomicOffer(Offer offer,
                                  Coin amount,
                                  long price,
                                  long miningFeePerByte,
                                  long makerFee,
                                  long takerFee,
                                  boolean isTakerApiUser,
                                  TradeResultHandler<BsqSwapTrade> tradeResultHandler,
                                  ErrorMessageHandler errorMessageHandler) {

        checkArgument(!wasOfferAlreadyUsedInTrade(offer.getId()));

        OfferAvailabilityModel model = getOfferAvailabilityModel(offer, isTakerApiUser);
        offer.checkOfferAvailability(model,
                () -> {
                    if (offer.getState() == Offer.State.AVAILABLE) {
                        BsqSwapTrade bsqSwapTrade = new BsqSwapTakerTrade(UUID.randomUUID().toString(),
                                offer,
                                amount,
                                price,
                                new Date().getTime(),
                                model.getPeerNodeAddress(),
                                miningFeePerByte,
                                makerFee,
                                takerFee,
                                new BsqSwapProtocolModel(keyRing.getPubKeyRing()),
                                "",
                                BsqSwapTrade.State.PREPARATION);

                        TradeProtocol tradeProtocol = createTradeProtocol(bsqSwapTrade);

                        initTradeAndProtocol(bsqSwapTrade, tradeProtocol);

                        ((TakerProtocol) tradeProtocol).onTakeOffer();
                        tradeResultHandler.handleResult(bsqSwapTrade);
                        requestPersistence();
                    }
                },
                errorMessageHandler);

        requestPersistence();
    }

    @NotNull
    private TradeProtocol createTradeProtocol(TradeModel trade) {
        TradeProtocol tradeProtocol = TradeProtocolFactory.getNewTradeProtocol(trade);
        TradeProtocol prev = tradeProtocolByTradeId.put(trade.getUid(), tradeProtocol);
        if (prev != null) {
            log.error("We had already an entry with uid {}", trade.getUid());
        }
        tradableList.add(trade);
        return tradeProtocol;
    }

    private ProcessModel getNewProcessModel(Offer offer) {
        return new ProcessModel(checkNotNull(offer).getId(),
                provider.getUser().getAccountId(),
                provider.getKeyRing().getPubKeyRing());
    }

    private OfferAvailabilityModel getOfferAvailabilityModel(Offer offer, boolean isTakerApiUser) {
        return new OfferAvailabilityModel(
                offer,
                keyRing.getPubKeyRing(),
                p2PService,
                user,
                mediatorManager,
                tradeStatisticsManager,
                isTakerApiUser);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Complete trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onWithdrawRequest(String toAddress,
                                  Coin amount,
                                  Coin fee,
                                  KeyParameter aesKey,
                                  Trade trade,
                                  @Nullable String memo,
                                  ResultHandler resultHandler,
                                  FaultHandler faultHandler) {
        String fromAddress = btcWalletService.getOrCreateAddressEntry(trade.getId(),
                AddressEntry.Context.TRADE_PAYOUT).getAddressString();
        FutureCallback<Transaction> callback = new FutureCallback<>() {
            @Override
            public void onSuccess(@javax.annotation.Nullable Transaction transaction) {
                if (transaction != null) {
                    log.debug("onWithdraw onSuccess tx ID:" + transaction.getTxId().toString());
                    onTradeCompleted(trade);
                    trade.setState(Trade.State.WITHDRAW_COMPLETED);
                    getTradeProtocol(trade).onWithdrawCompleted();
                    requestPersistence();
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
            btcWalletService.sendFunds(fromAddress, toAddress, amount, fee, aesKey,
                    AddressEntry.Context.TRADE_PAYOUT, memo, callback);
        } catch (AddressFormatException | InsufficientMoneyException | AddressEntryException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            faultHandler.handleFault("An exception occurred at requestWithdraw.", e);
        }
    }

    // If trade was completed (closed without fault but might be closed by a dispute) we move it to the closed trades
    public void onTradeCompleted(TradeModel tradeModel) {
        removeTrade(tradeModel);
        if (tradeModel instanceof BsqSwapTrade) {
            bsqSwapTradeManager.add((BsqSwapTrade) tradeModel);
        } else {
            closedTradableManager.add(tradeModel);
        }
        // TODO The address entry should have been removed already. Check and if its the case remove that.
        btcWalletService.resetAddressEntriesForPendingTrade(tradeModel.getId());
        requestPersistence();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void closeDisputedTrade(String tradeId, Trade.DisputeState disputeState) {
        getTradeById(tradeId).ifPresent(trade -> {
            trade.setDisputeState(disputeState);
            onTradeCompleted(trade);
            btcWalletService.swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT);
            requestPersistence();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade period state
    ///////////////////////////////////////////////////////////////////////////////////////////

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
        getObservableList().stream()
                .filter(tradeModel -> tradeModel instanceof Trade)
                .map(tradable -> (Trade) tradable)
                .forEach(trade -> {
                    if (!trade.isPayoutPublished()) {
                        Date maxTradePeriodDate = trade.getMaxTradePeriodDate();
                        Date halfTradePeriodDate = trade.getHalfTradePeriodDate();
                        if (maxTradePeriodDate != null && halfTradePeriodDate != null) {
                            Date now = new Date();
                            if (now.after(maxTradePeriodDate)) {
                                trade.setTradePeriodState(Trade.TradePeriodState.TRADE_PERIOD_OVER);
                                requestPersistence();
                            } else if (now.after(halfTradePeriodDate)) {
                                trade.setTradePeriodState(Trade.TradePeriodState.SECOND_HALF);
                                requestPersistence();
                            }
                        }
                    }
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Failed trade handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    // If trade is in already in critical state (if taker role: taker fee; both roles: after deposit published)
    // we move the trade to failedTradesManager
    public void onMoveInvalidTradeToFailedTrades(Trade trade) {
        removeTrade(trade);
        failedTradesManager.add(trade);
    }

    public void addFailedTradeToPendingTrades(Trade trade) {
        if (!trade.isInitialized()) {
            initPersistedTrade(trade);
        }
        addTrade(trade);
    }

    public Stream<Trade> getTradesStreamWithFundsLockedIn() {
        return getObservableList().stream()
                .filter(tradable -> tradable instanceof Trade)
                .map(tradable -> (Trade) tradable)
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
                        TransactionConfidence confidence = btcWalletService.getConfidenceForTxId(depositTx.getTxId().toString());
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

    // If trade still has funds locked up it might come back from failed trades
    // Aborts unfailing if the address entries needed are not available
    private boolean unFailTrade(Trade trade) {
        if (!recoverAddresses(trade)) {
            log.warn("Failed to recover address during unFail trade");
            return false;
        }

        initPersistedTrade(trade);

        if (!tradableList.contains(trade)) {
            tradableList.add(trade);
        }
        return true;
    }

    // The trade is added to pending trades if the associated address entries are AVAILABLE and
    // the relevant entries are changed, otherwise it's not added and no address entries are changed
    private boolean recoverAddresses(Trade trade) {
        // Find addresses associated with this trade.
        var entries = tradeUtil.getAvailableAddresses(trade);
        if (entries == null)
            return false;

        btcWalletService.recoverAddressEntry(trade.getId(), entries.first,
                AddressEntry.Context.MULTI_SIG);
        btcWalletService.recoverAddressEntry(trade.getId(), entries.second,
                AddressEntry.Context.TRADE_PAYOUT);
        return true;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters, Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<TradeModel> getObservableList() {
        return tradableList.getObservableList();
    }

    public BooleanProperty persistedTradesInitializedProperty() {
        return persistedTradesInitialized;
    }

    public boolean isMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }

    public boolean wasOfferAlreadyUsedInTrade(String offerId) {
        var hasTaken = tradableList.stream()
                .anyMatch(t -> t.getOffer().getId().equals(offerId));
        hasTaken &= failedTradesManager.getObservableList().stream()
                .anyMatch(t -> t.getOffer().getId().equals(offerId));
        Stream<Tradable> oldTradables = Stream.concat(closedTradableManager.getObservableList().stream(),
                bsqSwapTradeManager.getObservableList().stream());
        return hasTaken && oldTradables
                .anyMatch(t -> t.getOffer().getId().equals(offerId));
    }

    public boolean isBuyer(Offer offer) {
        // If I am the maker, we use the OfferPayload.Direction, otherwise the mirrored direction
        if (isMyOffer(offer))
            return offer.isBuyOffer();
        else
            return offer.getDirection() == OfferPayloadBase.Direction.SELL;
    }

    public Optional<TradeModel> getTradeModelById(String tradeId) {
        return tradableList.stream()
                .filter(tradeModel -> tradeModel.getId().equals(tradeId))
                .findFirst();
    }

    public Optional<BsqSwapTrade> getAtomicTradeById(String tradeId) {
        return bsqSwapTradeManager.findBsqSwapTradeById(tradeId);
    }

    public Optional<Trade> getTradeById(String tradeId) {
        return getTradeModelById(tradeId)
                .filter(tradeModel -> tradeModel instanceof Trade)
                .map(tradeModel -> (Trade) tradeModel);
    }

    private void removeTrade(TradeModel tradeModel) {
        if (tradableList.remove(tradeModel)) {
            requestPersistence();
        }
    }

    private void addTrade(TradeModel trade) {
        if (tradableList.add(trade)) {
            requestPersistence();
        }
    }

    // TODO Remove once tradableList is refactored to a final field
    //  (part of the persistence refactor PR)
    private void onTradesChanged() {
        this.numPendingTrades.set(getObservableList().size());
    }
}
