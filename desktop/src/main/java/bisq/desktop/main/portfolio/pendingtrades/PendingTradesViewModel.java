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
import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.common.model.ViewModel;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.locale.Res;
import bisq.core.network.MessageState;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferUtil;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.mempool.MempoolService;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.bisq_v1.TradeUtil;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.BtcAddressValidator;

import bisq.network.p2p.P2PService;

import bisq.common.ClockWatcher;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javax.inject.Named;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.Getter;

import javax.annotation.Nullable;

import static bisq.desktop.main.portfolio.pendingtrades.PendingTradesViewModel.SellerState.UNDEFINED;
import static com.google.common.base.Preconditions.checkNotNull;

public class PendingTradesViewModel extends ActivatableWithDataModel<PendingTradesDataModel> implements ViewModel {

    @Getter
    @Nullable
    private Trade trade;

    interface State {
    }

    enum BuyerState implements State {
        UNDEFINED,
        STEP1,
        STEP2,
        STEP3,
        STEP4
    }

    enum SellerState implements State {
        UNDEFINED,
        STEP1,
        STEP2,
        STEP3,
        STEP4
    }

    public final CoinFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;
    public final BtcAddressValidator btcAddressValidator;
    final AccountAgeWitnessService accountAgeWitnessService;
    public final P2PService p2PService;
    private final MempoolService mempoolService;
    private final ClosedTradableManager closedTradableManager;
    private final OfferUtil offerUtil;
    private final TradeUtil tradeUtil;
    public final ClockWatcher clockWatcher;
    @Getter
    private final Navigation navigation;
    @Getter
    private final User user;

    private final ObjectProperty<BuyerState> buyerState = new SimpleObjectProperty<>();
    private final ObjectProperty<SellerState> sellerState = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<MessageState> messageStateProperty = new SimpleObjectProperty<>(MessageState.UNDEFINED);
    private Subscription tradeStateSubscription;
    private Subscription messageStateSubscription;
    @Getter
    protected final IntegerProperty mempoolStatus = new SimpleIntegerProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesViewModel(PendingTradesDataModel dataModel,
                                  @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                                  BsqFormatter bsqFormatter,
                                  BtcAddressValidator btcAddressValidator,
                                  P2PService p2PService,
                                  MempoolService mempoolService,
                                  ClosedTradableManager closedTradableManager,
                                  OfferUtil offerUtil,
                                  TradeUtil tradeUtil,
                                  AccountAgeWitnessService accountAgeWitnessService,
                                  ClockWatcher clockWatcher,
                                  Navigation navigation,
                                  User user) {
        super(dataModel);

        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;
        this.btcAddressValidator = btcAddressValidator;
        this.p2PService = p2PService;
        this.mempoolService = mempoolService;
        this.closedTradableManager = closedTradableManager;
        this.offerUtil = offerUtil;
        this.tradeUtil = tradeUtil;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.clockWatcher = clockWatcher;
        this.navigation = navigation;
        this.user = user;
    }


    @Override
    protected void deactivate() {
        if (tradeStateSubscription != null) {
            tradeStateSubscription.unsubscribe();
            tradeStateSubscription = null;
        }

        if (messageStateSubscription != null) {
            messageStateSubscription.unsubscribe();
            messageStateSubscription = null;
        }
    }

    // Don't set own listener as we need to control the order of the calls
    public void onSelectedItemChanged(PendingTradesListItem selectedItem) {
        if (tradeStateSubscription != null) {
            tradeStateSubscription.unsubscribe();
            sellerState.set(SellerState.UNDEFINED);
            buyerState.set(BuyerState.UNDEFINED);
        }

        if (messageStateSubscription != null) {
            messageStateSubscription.unsubscribe();
            messageStateProperty.set(MessageState.UNDEFINED);
        }

        if (selectedItem != null) {
            this.trade = selectedItem.getTrade();
            tradeStateSubscription = EasyBind.subscribe(trade.stateProperty(), this::onTradeStateChanged);

            messageStateSubscription = EasyBind.subscribe(trade.getProcessModel().getPaymentStartedMessageStateProperty(), this::onMessageStateChanged);
        }
    }

    public void setMessageStateProperty(MessageState messageState) {
        if (messageStateProperty.get() == MessageState.ACKNOWLEDGED) {
            log.warn("We have already an ACKNOWLEDGED message received. " +
                    "We would not expect any other message after that. Received messageState={}", messageState);
            return;
        }

        if (trade != null)
            trade.getProcessModel().setPaymentStartedMessageState(messageState);
    }

    private void onMessageStateChanged(MessageState messageState) {
        messageStateProperty.set(messageState);
    }

    public void checkTakerFeeTx(Trade trade) {
        mempoolStatus.setValue(-1);
        mempoolService.validateOfferTakerTx(trade, (txValidator -> {
            mempoolStatus.setValue(txValidator.isFail() ? 0 : 1);
            if (txValidator.isFail()) {
                String errorMessage = "Validation of Taker Tx returned: " + txValidator.toString();
                log.warn(errorMessage);
                // prompt user to open mediation
                if (trade.getDisputeState() == Trade.DisputeState.NO_DISPUTE) {
                    UserThread.runAfter(() -> {
                        Popup popup = new Popup();
                        popup.headLine(Res.get("portfolio.pending.openSupportTicket.headline"))
                                .message(Res.get("portfolio.pending.invalidTx", errorMessage))
                                .actionButtonText(Res.get("portfolio.pending.openSupportTicket.headline"))
                                .onAction(dataModel::onOpenSupportTicket)
                                .closeButtonText(Res.get("shared.cancel"))
                                .onClose(popup::hide)
                                .show();
                    }, 100, TimeUnit.MILLISECONDS);
                }
            }
        }));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    ReadOnlyObjectProperty<BuyerState> getBuyerState() {
        return buyerState;
    }

    ReadOnlyObjectProperty<SellerState> getSellerState() {
        return sellerState;
    }

    public String getRemainingTradeDurationAsWords() {
        checkNotNull(dataModel.getTrade(), "model's trade must not be null");
        return tradeUtil.getRemainingTradeDurationAsWords(dataModel.getTrade());
    }

    public double getRemainingTradeDurationAsPercentage() {
        checkNotNull(dataModel.getTrade(), "model's trade must not be null");
        return tradeUtil.getRemainingTradeDurationAsPercentage(dataModel.getTrade());
    }

    public String getDateForOpenDispute() {
        checkNotNull(dataModel.getTrade(), "model's trade must not be null");
        return DisplayUtils.formatDateTime(tradeUtil.getDateForOpenDispute(dataModel.getTrade()));
    }

    public boolean showWarning() {
        checkNotNull(dataModel.getTrade(), "model's trade must not be null");
        Date halfTradePeriodDate = tradeUtil.getHalfTradePeriodDate(dataModel.getTrade());
        return halfTradePeriodDate != null && new Date().after(halfTradePeriodDate);
    }

    public boolean showDispute() {
        return getMaxTradePeriodDate() != null && new Date().after(getMaxTradePeriodDate());
    }

    //

    String getMyRole(PendingTradesListItem item) {
        Trade trade = item.getTrade();
        Contract contract = trade.getContract();
        if (contract != null) {
            Offer offer = trade.getOffer();
            checkNotNull(offer);
            checkNotNull(offer.getCurrencyCode());
            return tradeUtil.getRole(contract.isBuyerMakerAndSellerTaker(),
                    dataModel.isMaker(offer),
                    offer.getCurrencyCode());
        } else {
            return "";
        }
    }

    // summary
    public String getTradeVolume() {
        return dataModel.getTrade() != null
                ? btcFormatter.formatCoinWithCode(dataModel.getTrade().getAmount())
                : "";
    }

    public String getFiatVolume() {
        return dataModel.getTrade() != null
                ? VolumeUtil.formatVolumeWithCode(dataModel.getTrade().getVolume())
                : "";
    }

    public String getTxFee() {
        if (trade != null && trade.getAmount() != null) {
            Coin txFee = dataModel.getTxFee();
            String percentage = GUIUtil.getPercentageOfTradeAmount(txFee,
                    trade.getAmount(),
                    Coin.ZERO);
            return btcFormatter.formatCoinWithCode(txFee) + percentage;
        } else {
            return "";
        }
    }

    public String getTradeFee() {
        if (trade != null && dataModel.getOffer() != null && trade.getAmount() != null) {
            checkNotNull(dataModel.getTrade());
            if (dataModel.isMaker() && dataModel.getOffer().isCurrencyForMakerFeeBtc() ||
                    !dataModel.isMaker() && dataModel.getTrade().isCurrencyForTakerFeeBtc()) {
                Coin tradeFeeInBTC = dataModel.getTradeFeeInBTC();

                Coin minTradeFee = dataModel.isMaker() ?
                        FeeService.getMinMakerFee(true) :
                        FeeService.getMinTakerFee(true);

                String percentage = GUIUtil.getPercentageOfTradeAmount(tradeFeeInBTC, trade.getAmount(),
                        minTradeFee);
                return btcFormatter.formatCoinWithCode(tradeFeeInBTC) + percentage;
            } else {
                return bsqFormatter.formatCoinWithCode(dataModel.getTradeFeeAsBsq());
            }
        } else {
            return "";
        }
    }

    public String getSecurityDeposit() {
        Offer offer = dataModel.getOffer();
        Trade trade = dataModel.getTrade();
        if (offer != null && trade != null && trade.getAmount() != null) {
            Coin securityDeposit = dataModel.isBuyer() ?
                    offer.getBuyerSecurityDeposit()
                    : offer.getSellerSecurityDeposit();

            Coin minSecurityDeposit = dataModel.isBuyer() ?
                    Restrictions.getMinBuyerSecurityDepositAsCoin() :
                    Restrictions.getMinSellerSecurityDepositAsCoin();

            String percentage = GUIUtil.getPercentageOfTradeAmount(securityDeposit,
                    trade.getAmount(),
                    minSecurityDeposit);
            return btcFormatter.formatCoinWithCode(securityDeposit) + percentage;
        } else {
            return "";
        }
    }

    public boolean isBlockChainMethod() {
        return offerUtil.isBlockChainPaymentMethod(dataModel.getOffer());
    }

    public int getNumPastTrades(Trade trade) {
        return closedTradableManager.getObservableList().stream()
                .filter(e -> {
                    if (e instanceof Trade) {
                        Trade t = (Trade) e;
                        return t.getTradingPeerNodeAddress() != null &&
                                trade.getTradingPeerNodeAddress() != null &&
                                t.getTradingPeerNodeAddress().getFullAddress().equals(trade.getTradingPeerNodeAddress().getFullAddress());
                    } else
                        return false;

                })
                .collect(Collectors.toSet())
                .size();
    }

    @Nullable
    private Date getMaxTradePeriodDate() {
        return dataModel.getTrade() != null
                ? dataModel.getTrade().getMaxTradePeriodDate()
                : null;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // States
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onTradeStateChanged(Trade.State tradeState) {
        log.info("UI tradeState={}, id={}",
                tradeState,
                trade != null ? trade.getShortId() : "trade is null");

        switch (tradeState) {
            // #################### Phase PREPARATION
            case PREPARATION:
                sellerState.set(UNDEFINED);
                buyerState.set(BuyerState.UNDEFINED);
                break;

            // At first part maker/taker have different roles
            // taker perspective
            // #################### Phase TAKER_FEE_PAID
            case TAKER_PUBLISHED_TAKER_FEE_TX:

                // PUBLISH_DEPOSIT_TX_REQUEST
                // maker perspective
            case MAKER_SENT_PUBLISH_DEPOSIT_TX_REQUEST:
            case MAKER_SAW_ARRIVED_PUBLISH_DEPOSIT_TX_REQUEST:
            case MAKER_STORED_IN_MAILBOX_PUBLISH_DEPOSIT_TX_REQUEST:
            case MAKER_SEND_FAILED_PUBLISH_DEPOSIT_TX_REQUEST:

                // taker perspective
            case TAKER_RECEIVED_PUBLISH_DEPOSIT_TX_REQUEST:
                // We don't have a UI state for that, we still have not a ready initiated trade
                sellerState.set(UNDEFINED);
                buyerState.set(BuyerState.UNDEFINED);
                break;


            // #################### Phase DEPOSIT_PAID
            // DEPOSIT_TX_PUBLISHED_MSG
            // seller perspective
            case SELLER_PUBLISHED_DEPOSIT_TX:
                // buyer perspective
            case BUYER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG:

                // Alternatively the maker could have seen the deposit tx earlier before he received the DEPOSIT_TX_PUBLISHED_MSG
            case BUYER_SAW_DEPOSIT_TX_IN_NETWORK:
                buyerState.set(BuyerState.STEP1);
                sellerState.set(SellerState.STEP1);
                break;


            // buyer and seller step 2
            // #################### Phase DEPOSIT_CONFIRMED
            case DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN:
                sellerState.set(SellerState.STEP2);
                buyerState.set(BuyerState.STEP2);
                break;

            // buyer step 3
            case BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED: // UI action
            case BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG:  // FIAT_PAYMENT_INITIATED_MSG sent
                // We don't switch the UI before we got the feedback of the msg delivery
                buyerState.set(BuyerState.STEP2);
                break;
            case BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG:  // FIAT_PAYMENT_INITIATED_MSG arrived
            case BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG:  // FIAT_PAYMENT_INITIATED_MSG in mailbox
                buyerState.set(BuyerState.STEP3);
                break;
            case BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG:  // FIAT_PAYMENT_INITIATED_MSG failed
                // if failed we need to repeat sending so back to step 2
                buyerState.set(BuyerState.STEP2);
                break;

            // seller step 3
            case SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG: // FIAT_PAYMENT_INITIATED_MSG received
                sellerState.set(SellerState.STEP3);
                break;

            // seller step 4
            case SELLER_CONFIRMED_IN_UI_FIAT_PAYMENT_RECEIPT:   // UI action
            case SELLER_PUBLISHED_PAYOUT_TX: // payout tx broad casted
            case SELLER_SENT_PAYOUT_TX_PUBLISHED_MSG: // PAYOUT_TX_PUBLISHED_MSG sent
                sellerState.set(SellerState.STEP3);
                break;
            case SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG: // PAYOUT_TX_PUBLISHED_MSG arrived
            case SELLER_STORED_IN_MAILBOX_PAYOUT_TX_PUBLISHED_MSG: // PAYOUT_TX_PUBLISHED_MSG mailbox
            case SELLER_SEND_FAILED_PAYOUT_TX_PUBLISHED_MSG: // PAYOUT_TX_PUBLISHED_MSG failed -  payout tx is published, peer will see it in network so we ignore failure and complete
                sellerState.set(SellerState.STEP4);
                break;

            // buyer step 4
            case BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG:
                // Alternatively the maker could have seen the payout tx earlier before he received the PAYOUT_TX_PUBLISHED_MSG:
            case BUYER_SAW_PAYOUT_TX_IN_NETWORK:
                buyerState.set(BuyerState.STEP4);
                break;

            case WITHDRAW_COMPLETED:
                sellerState.set(UNDEFINED);
                buyerState.set(BuyerState.UNDEFINED);
                break;

            default:
                sellerState.set(UNDEFINED);
                buyerState.set(BuyerState.UNDEFINED);
                log.warn("unhandled processState " + tradeState);
                DevEnv.logErrorAndThrowIfDevMode("unhandled processState " + tradeState);
                break;
        }
    }
}
