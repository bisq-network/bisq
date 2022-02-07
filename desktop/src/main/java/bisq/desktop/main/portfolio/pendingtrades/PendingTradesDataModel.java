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
import bisq.desktop.main.support.dispute.client.mediation.MediationClientView;
import bisq.desktop.main.support.dispute.client.refund.RefundClientView;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferUtil;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeAlreadyOpenException;
import bisq.core.support.dispute.DisputeList;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.support.messages.ChatMessage;
import bisq.core.support.traderchat.TraderChatManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.TradeDataValidation;
import bisq.core.trade.model.bisq_v1.BuyerTrade;
import bisq.core.trade.model.bisq_v1.SellerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.BuyerProtocol;
import bisq.core.trade.protocol.bisq_v1.DisputeProtocol;
import bisq.core.trade.protocol.bisq_v1.SellerProtocol;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.FaultHandler;
import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import org.bouncycastle.crypto.params.KeyParameter;

import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import lombok.Getter;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PendingTradesDataModel extends ActivatableDataModel {
    @Getter
    public final TradeManager tradeManager;
    public final BtcWalletService btcWalletService;
    public final MediationManager mediationManager;
    public final RefundManager refundManager;
    private final P2PService p2PService;
    private final WalletsSetup walletsSetup;
    @Getter
    private final AccountAgeWitnessService accountAgeWitnessService;
    public final DaoFacade daoFacade;
    public final Navigation navigation;
    public final WalletPasswordWindow walletPasswordWindow;
    private final NotificationCenter notificationCenter;
    private final OfferUtil offerUtil;
    private final CoinFormatter btcFormatter;

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
    private final PubKeyRing pubKeyRing;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesDataModel(TradeManager tradeManager,
                                  BtcWalletService btcWalletService,
                                  PubKeyRing pubKeyRing,
                                  MediationManager mediationManager,
                                  RefundManager refundManager,
                                  TraderChatManager traderChatManager,
                                  Preferences preferences,
                                  P2PService p2PService,
                                  WalletsSetup walletsSetup,
                                  AccountAgeWitnessService accountAgeWitnessService,
                                  DaoFacade daoFacade,
                                  Navigation navigation,
                                  WalletPasswordWindow walletPasswordWindow,
                                  NotificationCenter notificationCenter,
                                  OfferUtil offerUtil,
                                  @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter) {
        this.tradeManager = tradeManager;
        this.btcWalletService = btcWalletService;
        this.pubKeyRing = pubKeyRing;
        this.mediationManager = mediationManager;
        this.refundManager = refundManager;
        this.traderChatManager = traderChatManager;
        this.preferences = preferences;
        this.p2PService = p2PService;
        this.walletsSetup = walletsSetup;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.daoFacade = daoFacade;
        this.navigation = navigation;
        this.walletPasswordWindow = walletPasswordWindow;
        this.notificationCenter = notificationCenter;
        this.offerUtil = offerUtil;
        this.btcFormatter = formatter;

        tradesListChangeListener = change -> onListChanged();
        notificationCenter.setSelectItemByTradeIdConsumer(this::selectItemByTradeId);
    }

    @Override
    protected void activate() {
        tradeManager.getObservableList().addListener(tradesListChangeListener);
        onListChanged();
        if (selectedItemProperty.get() != null)
            notificationCenter.setSelectedTradeId(selectedItemProperty.get().getTrade().getId());

        activated = true;
    }

    @Override
    protected void deactivate() {
        tradeManager.getObservableList().removeListener(tradesListChangeListener);
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
        Trade trade = getTrade();
        checkNotNull(trade, "trade must not be null");
        checkArgument(trade instanceof BuyerTrade, "Check failed: trade instanceof BuyerTrade. Was: " + trade.getClass().getSimpleName());
        ((BuyerProtocol) tradeManager.getTradeProtocol(trade)).onPaymentStarted(resultHandler, errorMessageHandler);
    }

    public void onFiatPaymentReceived(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        Trade trade = getTrade();
        checkNotNull(trade, "trade must not be null");
        checkArgument(trade instanceof SellerTrade, "Trade must be instance of SellerTrade");
        ((SellerProtocol) tradeManager.getTradeProtocol(trade)).onPaymentReceived(resultHandler, errorMessageHandler);
    }

    public void onWithdrawRequest(String toAddress,
                                  Coin amount,
                                  Coin fee,
                                  KeyParameter aesKey,
                                  @Nullable String memo,
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
                    memo,
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
        return getOffer() != null && offerUtil.isBuyOffer(getOffer().getDirection());
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
                    return trade.getTradeTxFee().multiply(3);
                else
                    return trade.getTradeTxFee().multiply(3).subtract(trade.getTakerFee()); // BSQ will be used as part of the miner fee
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
        list.addAll(tradeManager.getObservableList().stream()
                .map(trade -> new PendingTradesListItem(trade, btcFormatter))
                .collect(Collectors.toList()));

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
                    txId.set(depositTx.getTxId().toString());
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
                txId.set(depositTx.getTxId().toString());
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

        doOpenDispute(isSupportTicket, trade.getDepositTx());
    }

    private void doOpenDispute(boolean isSupportTicket, Transaction depositTx) {
        // We do not support opening a dispute if the deposit tx is null. Traders have to use the support channel at Matrix
        // in such cases. The mediators or arbitrators could not help anyway with a payout in such cases.
        if (depositTx == null) {
            log.error("Deposit tx must not be null");
            new Popup().instruction(Res.get("portfolio.pending.error.depositTxNull")).show();
            return;
        }
        String depositTxId = depositTx.getTxId().toString();

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
            payoutTxHashAsString = payoutTx.getTxId().toString();
        }
        Trade.DisputeState disputeState = trade.getDisputeState();
        DisputeManager<? extends DisputeList<Dispute>> disputeManager;
        long lockTime = trade.getDelayedPayoutTx() == null ? trade.getLockTime() : trade.getDelayedPayoutTx().getLockTime();
        long remainingLockTime = lockTime - btcWalletService.getBestChainHeight();
        // In case we re-open a dispute we allow Trade.DisputeState.MEDIATION_REQUESTED
        boolean useMediation = disputeState == Trade.DisputeState.NO_DISPUTE ||
                (disputeState == Trade.DisputeState.MEDIATION_REQUESTED && remainingLockTime > 0);
        // In case we re-open a dispute we allow Trade.DisputeState.REFUND_REQUESTED
        boolean useRefundAgent = disputeState == Trade.DisputeState.MEDIATION_CLOSED ||
                disputeState == Trade.DisputeState.REFUND_REQUESTED || remainingLockTime <= 0;

        AtomicReference<String> donationAddressString = new AtomicReference<>("");
        Transaction delayedPayoutTx = trade.getDelayedPayoutTx();
        try {
            TradeDataValidation.validateDelayedPayoutTx(trade,
                    delayedPayoutTx,
                    daoFacade,
                    btcWalletService,
                    donationAddressString::set);
        } catch (TradeDataValidation.ValidationException e) {
            // The peer sent us an invalid donation address. We do not return here as we don't want to break
            // mediation/arbitration and log only the issue. The dispute agent will run validation as well and will get
            // a popup displayed to react.
            log.error("DelayedPayoutTxValidation failed. {}", e.toString());

            if (useRefundAgent) {
                // We don't allow to continue and publish payout tx and open refund agent case.
                // In case it was caused by some bug we want to prevent a wrong payout. In case its a scam attempt we
                // want to protect the refund agent.
                return;
            }
        }

        ResultHandler resultHandler;
        if (useMediation) {
            // If no dispute state set we start with mediation
            resultHandler = () -> navigation.navigateTo(MainView.class, SupportView.class, MediationClientView.class);
            disputeManager = mediationManager;
            PubKeyRing mediatorPubKeyRing = trade.getMediatorPubKeyRing();
            checkNotNull(mediatorPubKeyRing, "mediatorPubKeyRing must not be null");
            byte[] depositTxSerialized = depositTx.bitcoinSerialize();
            Dispute dispute = new Dispute(new Date().getTime(),
                    trade.getId(),
                    pubKeyRing.hashCode(), // traderId
                    (offer.getDirection() == OfferDirection.BUY) == isMaker,
                    isMaker,
                    pubKeyRing,
                    trade.getDate().getTime(),
                    trade.getMaxTradePeriodDate().getTime(),
                    trade.getContract(),
                    trade.getContractHash(),
                    depositTxSerialized,
                    payoutTxSerialized,
                    depositTxId,
                    payoutTxHashAsString,
                    trade.getContractAsJson(),
                    trade.getMakerContractSignature(),
                    trade.getTakerContractSignature(),
                    mediatorPubKeyRing,
                    isSupportTicket,
                    SupportType.MEDIATION);
            dispute.setExtraData("counterCurrencyTxId", trade.getCounterCurrencyTxId());
            dispute.setExtraData("counterCurrencyExtraData", trade.getCounterCurrencyExtraData());

            dispute.setDonationAddressOfDelayedPayoutTx(donationAddressString.get());
            if (delayedPayoutTx != null) {
                dispute.setDelayedPayoutTxId(delayedPayoutTx.getTxId().toString());
            }

            trade.setDisputeState(Trade.DisputeState.MEDIATION_REQUESTED);
            sendOpenDisputeMessage(disputeManager, resultHandler, dispute);
            tradeManager.requestPersistence();
        } else if (useRefundAgent) {
            resultHandler = () -> navigation.navigateTo(MainView.class, SupportView.class, RefundClientView.class);

            if (delayedPayoutTx == null) {
                log.error("Delayed payout tx is missing");
                return;
            }

            // We only require for refund agent a confirmed deposit tx. For mediation we tolerate a unconfirmed tx as
            // no harm can be done to the mediator (refund agent who would accept a invalid deposit tx might reimburse
            // the traders but the funds never have been spent).
            TransactionConfidence confidenceForTxId = btcWalletService.getConfidenceForTxId(depositTxId);
            if (confidenceForTxId == null || confidenceForTxId.getConfidenceType() != TransactionConfidence.ConfidenceType.BUILDING) {
                log.error("Confidence for deposit tx must be BUILDING, confidenceForTxId={}", confidenceForTxId);
                new Popup().instruction(Res.get("portfolio.pending.error.depositTxNotConfirmed")).show();
                return;
            }

            if (remainingLockTime > 0) {
                new Popup().instruction(Res.get("portfolio.pending.timeLockNotOver",
                        FormattingUtils.getDateFromBlockHeight(remainingLockTime), remainingLockTime))
                        .show();
                return;
            }

            disputeManager = refundManager;
            PubKeyRing refundAgentPubKeyRing = trade.getRefundAgentPubKeyRing();
            checkNotNull(refundAgentPubKeyRing, "refundAgentPubKeyRing must not be null");
            byte[] depositTxSerialized = depositTx.bitcoinSerialize();
            String depositTxHashAsString = depositTx.getTxId().toString();
            Dispute dispute = new Dispute(new Date().getTime(),
                    trade.getId(),
                    pubKeyRing.hashCode(), // traderId
                    (offer.getDirection() == OfferDirection.BUY) == isMaker,
                    isMaker,
                    pubKeyRing,
                    trade.getDate().getTime(),
                    trade.getMaxTradePeriodDate().getTime(),
                    trade.getContract(),
                    trade.getContractHash(),
                    depositTxSerialized,
                    payoutTxSerialized,
                    depositTxHashAsString,
                    payoutTxHashAsString,
                    trade.getContractAsJson(),
                    trade.getMakerContractSignature(),
                    trade.getTakerContractSignature(),
                    refundAgentPubKeyRing,
                    isSupportTicket,
                    SupportType.REFUND);
            dispute.setExtraData("counterCurrencyTxId", trade.getCounterCurrencyTxId());
            dispute.setExtraData("counterCurrencyExtraData", trade.getCounterCurrencyExtraData());

            String tradeId = dispute.getTradeId();
            mediationManager.findDispute(tradeId)
                    .ifPresent(mediatorsDispute -> {
                        DisputeResult mediatorsDisputeResult = mediatorsDispute.getDisputeResultProperty().get();
                        ChatMessage mediatorsResultMessage = mediatorsDisputeResult == null ? null : mediatorsDisputeResult.getChatMessage();
                        if (mediatorsResultMessage != null) {
                            String mediatorAddress = Res.get("support.mediatorsAddress",
                                    mediatorsDispute.getContract().getRefundAgentNodeAddress().getFullAddress());
                            String message = mediatorAddress + "\n\n" + mediatorsResultMessage.getMessage();
                            dispute.setMediatorsDisputeResult(message);
                        }
                    });

            dispute.setDonationAddressOfDelayedPayoutTx(donationAddressString.get());
            dispute.setDelayedPayoutTxId(delayedPayoutTx.getTxId().toString());
            trade.setDisputeState(Trade.DisputeState.REFUND_REQUESTED);

            ((DisputeProtocol) tradeManager.getTradeProtocol(trade)).onPublishDelayedPayoutTx(() -> {
                        log.info("DelayedPayoutTx published and message sent to peer");
                        sendOpenDisputeMessage(disputeManager, resultHandler, dispute);
                    },
                    errorMessage -> new Popup().error(errorMessage).show());
        } else {
            log.warn("Invalid dispute state {}", disputeState.name());
        }
        tradeManager.requestPersistence();
    }

    public boolean isReadyForTxBroadcast() {
        return GUIUtil.isReadyForTxBroadcastOrShowPopup(p2PService, walletsSetup);
    }

    public boolean isBootstrappedOrShowPopup() {
        return GUIUtil.isBootstrappedOrShowPopup(p2PService);
    }

    public void onMoveInvalidTradeToFailedTrades(Trade trade) {
        tradeManager.onMoveInvalidTradeToFailedTrades(trade);
    }

    public boolean isSignWitnessTrade() {
        return accountAgeWitnessService.isSignWitnessTrade(selectedTrade);
    }

    private void sendOpenDisputeMessage(DisputeManager<? extends DisputeList<Dispute>> disputeManager,
                                        ResultHandler resultHandler,
                                        Dispute dispute) {
        disputeManager.sendOpenNewDisputeMessage(dispute,
                false,
                resultHandler,
                (errorMessage, throwable) -> {
                    if ((throwable instanceof DisputeAlreadyOpenException)) {
                        errorMessage += "\n\n" + Res.get("portfolio.pending.openAgainDispute.msg");
                        new Popup().warning(errorMessage)
                                .actionButtonText(Res.get("portfolio.pending.openAgainDispute.button"))
                                .onAction(() -> disputeManager.sendOpenNewDisputeMessage(dispute,
                                        true,
                                        resultHandler,
                                        (e, t) -> log.error(e)))
                                .closeButtonText(Res.get("shared.cancel"))
                                .show();
                    } else {
                        new Popup().warning(errorMessage).show();
                    }
                });
    }
}

