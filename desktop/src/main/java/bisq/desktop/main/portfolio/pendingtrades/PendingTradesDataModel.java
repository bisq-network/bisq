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

package bisq.desktop.main.portfolio.pendingtrades;

import bisq.desktop.Navigation;
import bisq.desktop.common.model.ActivatableDataModel;
import bisq.desktop.main.MainView;
import bisq.desktop.main.overlays.notifications.NotificationCenter;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.WalletPasswordWindow;
import bisq.desktop.main.support.SupportView;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeAlreadyOpenException;
import bisq.core.support.dispute.DisputeList;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.traderchat.TraderChatManager;
import bisq.core.trade.BuyerTrade;
import bisq.core.trade.SellerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.user.Preferences;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.FaultHandler;
import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import com.google.inject.Inject;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import org.spongycastle.crypto.params.KeyParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PendingTradesDataModel extends ActivatableDataModel {
    public final TradeManager tradeManager;
    public final BtcWalletService btcWalletService;
    public final ArbitrationManager arbitrationManager;
    public final MediationManager mediationManager;
    private final P2PService p2PService;
    private final WalletsSetup walletsSetup;
    @Getter
    private final AccountAgeWitnessService accountAgeWitnessService;
    public final Navigation navigation;
    public final WalletPasswordWindow walletPasswordWindow;
    private final NotificationCenter notificationCenter;

    final ObservableList<PendingTradesListItem> list = FXCollections.observableArrayList();
    private final ListChangeListener<Trade> tradesListChangeListener;
    private boolean isMaker;

    final ObjectProperty<PendingTradesListItem> selectedItemProperty = new SimpleObjectProperty<>();
    public final StringProperty txId = new SimpleStringProperty();
    @Getter
    private final TraderChatManager traderChatManager;
    public final Preferences preferences;
    private boolean activated;
    private ChangeListener<Trade.State> tradeStateChangeListener;
    private Trade selectedTrade;
    @Getter
    private PubKeyRing pubKeyRing;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesDataModel(TradeManager tradeManager,
                                  BtcWalletService btcWalletService,
                                  PubKeyRing pubKeyRing,
                                  ArbitrationManager arbitrationManager,
                                  MediationManager mediationManager,
                                  TraderChatManager traderChatManager,
                                  Preferences preferences,
                                  P2PService p2PService,
                                  WalletsSetup walletsSetup,
                                  AccountAgeWitnessService accountAgeWitnessService,
                                  Navigation navigation,
                                  WalletPasswordWindow walletPasswordWindow,
                                  NotificationCenter notificationCenter) {
        this.tradeManager = tradeManager;
        this.btcWalletService = btcWalletService;
        this.pubKeyRing = pubKeyRing;
        this.arbitrationManager = arbitrationManager;
        this.mediationManager = mediationManager;
        this.traderChatManager = traderChatManager;
        this.preferences = preferences;
        this.p2PService = p2PService;
        this.walletsSetup = walletsSetup;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.navigation = navigation;
        this.walletPasswordWindow = walletPasswordWindow;
        this.notificationCenter = notificationCenter;

        tradesListChangeListener = change -> onListChanged();
        notificationCenter.setSelectItemByTradeIdConsumer(this::selectItemByTradeId);
    }

    @Override
    protected void activate() {
        tradeManager.getTradableList().addListener(tradesListChangeListener);
        onListChanged();
        if (selectedItemProperty.get() != null)
            notificationCenter.setSelectedTradeId(selectedItemProperty.get().getTrade().getId());

        activated = true;
    }

    @Override
    protected void deactivate() {
        tradeManager.getTradableList().removeListener(tradesListChangeListener);
        notificationCenter.setSelectedTradeId(null);
        activated = false;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onSelectItem(PendingTradesListItem item) {
        doSelectItem(item);
    }

    public void onPaymentStarted(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        final Trade trade = getTrade();
        checkNotNull(trade, "trade must not be null");
        checkArgument(trade instanceof BuyerTrade, "Check failed: trade instanceof BuyerTrade");
        // TODO UI not impl yet
        trade.setCounterCurrencyTxId("");
        ((BuyerTrade) trade).onFiatPaymentStarted(resultHandler, errorMessageHandler);
    }

    public void onFiatPaymentReceived(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        checkNotNull(getTrade(), "trade must not be null");
        checkArgument(getTrade() instanceof SellerTrade, "Check failed: trade not instanceof SellerTrade");
        ((SellerTrade) getTrade()).onFiatPaymentReceived(resultHandler, errorMessageHandler);
    }

    public void onWithdrawRequest(String toAddress,
                                  Coin amount,
                                  Coin fee,
                                  KeyParameter aesKey,
                                  ResultHandler resultHandler,
                                  FaultHandler faultHandler) {
        checkNotNull(getTrade(), "trade must not be null");

        if (toAddress != null && toAddress.length() > 0) {
            tradeManager.onWithdrawRequest(
                    toAddress,
                    amount,
                    fee,
                    aesKey,
                    getTrade(),
                    () -> {
                        resultHandler.handleResult();
                        selectBestItem();
                    },
                    (errorMessage, throwable) -> {
                        log.error(errorMessage);
                        faultHandler.handleFault(errorMessage, throwable);
                    });
        } else {
            faultHandler.handleFault(Res.get("portfolio.pending.noReceiverAddressDefined"), null);
        }
    }

    public void onOpenDispute() {
        tryOpenDispute(false);
    }

    public void onOpenSupportTicket() {
        tryOpenDispute(true);
    }

    public void onMoveToFailedTrades() {
        tradeManager.addTradeToFailedTrades(getTrade());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public Trade getTrade() {
        return selectedItemProperty.get() != null ? selectedItemProperty.get().getTrade() : null;
    }

    @Nullable
    Offer getOffer() {
        return getTrade() != null ? getTrade().getOffer() : null;
    }

    private boolean isBuyOffer() {
        return getOffer() != null && getOffer().getDirection() == OfferPayload.Direction.BUY;
    }

    boolean isBuyer() {
        return (isMaker(getOffer()) && isBuyOffer())
                || (!isMaker(getOffer()) && !isBuyOffer());
    }

    boolean isMaker(Offer offer) {
        return tradeManager.isMyOffer(offer);
    }

    public boolean isMaker() {
        return isMaker;
    }

    Coin getTradeFeeInBTC() {
        Trade trade = getTrade();
        if (trade != null) {
            Offer offer = trade.getOffer();
            if (isMaker()) {
                if (offer != null) {
                    if (offer.isCurrencyForMakerFeeBtc())
                        return offer.getMakerFee();
                    else
                        return Coin.ZERO;// getTradeFeeAsBsq is used for BSQ
                } else {
                    log.error("offer is null");
                    return Coin.ZERO;
                }
            } else {
                if (trade.isCurrencyForTakerFeeBtc())
                    return trade.getTakerFee();
                else
                    return Coin.ZERO; // getTradeFeeAsBsq is used for BSQ
            }
        } else {
            log.error("Trade is null at getTotalFees");
            return Coin.ZERO;
        }
    }

    Coin getTxFee() {
        Trade trade = getTrade();
        if (trade != null) {
            if (isMaker()) {
                Offer offer = trade.getOffer();
                if (offer != null) {
                    if (offer.isCurrencyForMakerFeeBtc())
                        return offer.getTxFee();
                    else
                        return offer.getTxFee().subtract(offer.getMakerFee()); // BSQ will be used as part of the miner fee
                } else {
                    log.error("offer is null");
                    return Coin.ZERO;
                }
            } else {
                if (trade.isCurrencyForTakerFeeBtc())
                    return trade.getTxFee().multiply(3);
                else
                    return trade.getTxFee().multiply(3).subtract(trade.getTakerFee()); // BSQ will be used as part of the miner fee
            }
        } else {
            log.error("Trade is null at getTotalFees");
            return Coin.ZERO;
        }
    }

    Coin getTradeFeeAsBsq() {
        Trade trade = getTrade();
        if (trade != null) {
            if (isMaker()) {
                Offer offer = trade.getOffer();
                if (offer != null) {
                    if (offer.isCurrencyForMakerFeeBtc()) {
                        return Coin.ZERO; // getTradeFeeInBTC is used for BTC
                    } else {
                        return offer.getMakerFee();
                    }
                } else {
                    log.error("offer is null");
                    return Coin.ZERO;
                }
            } else {
                if (trade.isCurrencyForTakerFeeBtc())
                    return Coin.ZERO; // getTradeFeeInBTC is used for BTC
                else
                    return trade.getTakerFee();
            }
        } else {
            log.error("Trade is null at getTotalFees");
            return Coin.ZERO;
        }
    }

    public String getCurrencyCode() {
        return getOffer() != null ? getOffer().getCurrencyCode() : "";
    }


    @Nullable
    public PaymentAccountPayload getSellersPaymentAccountPayload() {
        if (getTrade() != null && getTrade().getContract() != null)
            return getTrade().getContract().getSellerPaymentAccountPayload();
        else
            return null;
    }

    @Nullable
    public PaymentAccountPayload getBuyersPaymentAccountPayload() {
        if (getTrade() != null && getTrade().getContract() != null)
            return getTrade().getContract().getBuyerPaymentAccountPayload();
        else
            return null;
    }

    public String getReference() {
        return getOffer() != null ? getOffer().getShortId() : "";
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onListChanged() {
        list.clear();
        list.addAll(tradeManager.getTradableList().stream().map(PendingTradesListItem::new).collect(Collectors.toList()));

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getTrade().getDate().compareTo(o1.getTrade().getDate()));

        selectBestItem();
    }

    private void selectBestItem() {
        if (list.size() == 1)
            doSelectItem(list.get(0));
        else if (list.size() > 1 && (selectedItemProperty.get() == null || !list.contains(selectedItemProperty.get())))
            doSelectItem(list.get(0));
        else if (list.size() == 0)
            doSelectItem(null);
    }

    private void selectItemByTradeId(String tradeId) {
        if (activated) {
            list.stream().filter(e -> e.getTrade().getId().equals(tradeId)).findAny().ifPresent(this::doSelectItem);
        }
    }

    private void doSelectItem(@Nullable PendingTradesListItem item) {
        if (selectedTrade != null)
            selectedTrade.stateProperty().removeListener(tradeStateChangeListener);

        if (item != null) {
            selectedTrade = item.getTrade();
            if (selectedTrade == null) {
                log.error("selectedTrade is null");
                return;
            }

            Transaction depositTx = selectedTrade.getDepositTx();
            String tradeId = selectedTrade.getId();
            tradeStateChangeListener = (observable, oldValue, newValue) -> {
                if (depositTx != null) {
                    txId.set(depositTx.getHashAsString());
                    notificationCenter.setSelectedTradeId(tradeId);
                    selectedTrade.stateProperty().removeListener(tradeStateChangeListener);
                } else {
                    txId.set("");
                }
            };
            selectedTrade.stateProperty().addListener(tradeStateChangeListener);

            Offer offer = selectedTrade.getOffer();
            if (offer == null) {
                log.error("offer is null");
                return;
            }

            isMaker = tradeManager.isMyOffer(offer);
            if (depositTx != null) {
                txId.set(depositTx.getHashAsString());
            } else {
                txId.set("");
            }
            notificationCenter.setSelectedTradeId(tradeId);
        } else {
            selectedTrade = null;
            txId.set("");
            notificationCenter.setSelectedTradeId(null);
        }
        selectedItemProperty.set(item);
    }

    private void tryOpenDispute(boolean isSupportTicket) {
        Trade trade = getTrade();
        if (trade == null) {
            log.error("Trade is null");
            return;
        }

        Transaction depositTx = trade.getDepositTx();
        if (depositTx != null) {
            doOpenDispute(isSupportTicket, depositTx);
        } else {
            //TODO consider to remove that
            log.info("Trade.depositTx is null. We try to find the tx in our wallet.");
            List<Transaction> candidates = new ArrayList<>();
            List<Transaction> transactions = btcWalletService.getRecentTransactions(100, true);
            transactions.forEach(transaction -> {
                Coin valueSentFromMe = btcWalletService.getValueSentFromMeForTransaction(transaction);
                if (!valueSentFromMe.isZero()) {
                    // spending tx
                    // MS tx
                    candidates.addAll(transaction.getOutputs().stream()
                            .filter(output -> !btcWalletService.isTransactionOutputMine(output))
                            .filter(output -> output.getScriptPubKey().isPayToScriptHash())
                            .map(transactionOutput -> transaction)
                            .collect(Collectors.toList()));
                }
            });

            if (candidates.size() > 0) {
                log.error("Trade.depositTx is null. We take the first possible MultiSig tx just to be able to open a dispute. " +
                        "candidates={}", candidates);
                doOpenDispute(isSupportTicket, candidates.get(0));
            } else if (transactions.size() > 0) {
                doOpenDispute(isSupportTicket, transactions.get(0));
                log.error("Trade.depositTx is null and we did not find any MultiSig transaction. We take any random tx just to be able to open a dispute");
            } else {
                log.error("Trade.depositTx is null and we did not find any transaction.");
            }
        }
    }

    private void doOpenDispute(boolean isSupportTicket, Transaction depositTx) {
        Trade trade = getTrade();
        if (trade == null) {
            log.warn("trade is null at doOpenDispute");
            return;
        }

        Offer offer = trade.getOffer();
        if (offer == null) {
            log.warn("offer is null at doOpenDispute");
            return;
        }

        if (!GUIUtil.isBootstrappedOrShowPopup(p2PService)) {
            return;
        }

        byte[] payoutTxSerialized = null;
        String payoutTxHashAsString = null;
        Transaction payoutTx = trade.getPayoutTx();
        if (payoutTx != null) {
            payoutTxSerialized = payoutTx.bitcoinSerialize();
            payoutTxHashAsString = payoutTx.getHashAsString();
        }
        Trade.DisputeState disputeState = trade.getDisputeState();
        DisputeManager<? extends DisputeList<? extends DisputeList>> disputeManager;
        boolean useMediation;
        boolean useArbitration;
        // If mediation is not activated we use arbitration
        if (MediationManager.isMediationActivated()) {
            // In case we re-open a dispute we allow Trade.DisputeState.MEDIATION_REQUESTED or
            useMediation = disputeState == Trade.DisputeState.NO_DISPUTE || disputeState == Trade.DisputeState.MEDIATION_REQUESTED;
            // in case of arbitration disputeState == Trade.DisputeState.ARBITRATION_REQUESTED
            useArbitration = disputeState == Trade.DisputeState.MEDIATION_CLOSED || disputeState == Trade.DisputeState.DISPUTE_REQUESTED;
        } else {
            useMediation = false;
            useArbitration = true;
        }
        if (useMediation) {
            // If no dispute state set we start with mediation
            disputeManager = mediationManager;
            PubKeyRing mediatorPubKeyRing = trade.getMediatorPubKeyRing();
            checkNotNull(mediatorPubKeyRing, "mediatorPubKeyRing must not be null");
            byte[] depositTxSerialized = depositTx.bitcoinSerialize();
            String depositTxHashAsString = depositTx.getHashAsString();
            Dispute dispute = new Dispute(disputeManager.getStorage(),
                    trade.getId(),
                    pubKeyRing.hashCode(), // traderId
                    (offer.getDirection() == OfferPayload.Direction.BUY) == isMaker,
                    isMaker,
                    pubKeyRing,
                    trade.getDate().getTime(),
                    trade.getContract(),
                    trade.getContractHash(),
                    depositTxSerialized,
                    payoutTxSerialized,
                    depositTxHashAsString,
                    payoutTxHashAsString,
                    trade.getContractAsJson(),
                    trade.getMakerContractSignature(),
                    trade.getTakerContractSignature(),
                    mediatorPubKeyRing,
                    isSupportTicket);

            trade.setDisputeState(Trade.DisputeState.MEDIATION_REQUESTED);
            sendOpenNewDisputeMessage(dispute, false, disputeManager);
        } else if (useArbitration) {
            // Only if we have completed mediation we allow arbitration
            disputeManager = arbitrationManager;
            PubKeyRing arbitratorPubKeyRing = trade.getArbitratorPubKeyRing();
            checkNotNull(arbitratorPubKeyRing, "arbitratorPubKeyRing must not be null");
            byte[] depositTxSerialized = depositTx.bitcoinSerialize();
            String depositTxHashAsString = depositTx.getHashAsString();
            Dispute dispute = new Dispute(disputeManager.getStorage(),
                    trade.getId(),
                    pubKeyRing.hashCode(), // traderId
                    (offer.getDirection() == OfferPayload.Direction.BUY) == isMaker,
                    isMaker,
                    pubKeyRing,
                    trade.getDate().getTime(),
                    trade.getContract(),
                    trade.getContractHash(),
                    depositTxSerialized,
                    payoutTxSerialized,
                    depositTxHashAsString,
                    payoutTxHashAsString,
                    trade.getContractAsJson(),
                    trade.getMakerContractSignature(),
                    trade.getTakerContractSignature(),
                    arbitratorPubKeyRing,
                    isSupportTicket);

            trade.setDisputeState(Trade.DisputeState.DISPUTE_REQUESTED);
            sendOpenNewDisputeMessage(dispute, false, disputeManager);
        } else {
            log.warn("Invalid dispute state {}", disputeState.name());
        }
    }

    private void sendOpenNewDisputeMessage(Dispute dispute,
                                           boolean reOpen,
                                           DisputeManager<? extends DisputeList<? extends DisputeList>> disputeManager) {
        disputeManager.sendOpenNewDisputeMessage(dispute,
                reOpen,
                () -> navigation.navigateTo(MainView.class, SupportView.class),
                (errorMessage, throwable) -> {
                    if ((throwable instanceof DisputeAlreadyOpenException)) {
                        errorMessage += "\n\n" + Res.get("portfolio.pending.openAgainDispute.msg");
                        new Popup<>().warning(errorMessage)
                                .actionButtonText(Res.get("portfolio.pending.openAgainDispute.button"))
                                .onAction(() -> sendOpenNewDisputeMessage(dispute, true, disputeManager))
                                .closeButtonText(Res.get("shared.cancel"))
                                .show();
                    } else {
                        new Popup<>().warning(errorMessage).show();
                    }
                });
    }

    public boolean isReadyForTxBroadcast() {
        return GUIUtil.isReadyForTxBroadcastOrShowPopup(p2PService, walletsSetup);
    }

    public boolean isBootstrappedOrShowPopup() {
        return GUIUtil.isBootstrappedOrShowPopup(p2PService);
    }
}

