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

package io.bisq.gui.main.portfolio.pendingtrades;

import com.google.inject.Inject;
import io.bisq.common.Clock;
import io.bisq.common.app.DevEnv;
import io.bisq.common.app.Log;
import io.bisq.common.locale.Res;
import io.bisq.core.offer.Offer;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.payload.PaymentMethod;
import io.bisq.core.trade.Contract;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.closed.ClosedTradableManager;
import io.bisq.gui.common.model.ActivatableWithDataModel;
import io.bisq.gui.common.model.ViewModel;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.BsqFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.gui.util.validation.BtcAddressValidator;
import io.bisq.network.p2p.P2PService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.bitcoinj.core.Coin;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.stream.Collectors;

import static io.bisq.gui.main.portfolio.pendingtrades.PendingTradesViewModel.SellerState.UNDEFINED;

public class PendingTradesViewModel extends ActivatableWithDataModel<PendingTradesDataModel> implements ViewModel {
    private Subscription tradeStateSubscription;
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

    public final BSFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;
    public final BtcAddressValidator btcAddressValidator;
    final AccountAgeWitnessService accountAgeWitnessService;
    public final P2PService p2PService;
    private final ClosedTradableManager closedTradableManager;
    public final Clock clock;

    private final ObjectProperty<BuyerState> buyerState = new SimpleObjectProperty<>();
    private final ObjectProperty<SellerState> sellerState = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesViewModel(PendingTradesDataModel dataModel,
                                  BSFormatter btcFormatter,
                                  BsqFormatter bsqFormatter,
                                  BtcAddressValidator btcAddressValidator,
                                  P2PService p2PService,
                                  ClosedTradableManager closedTradableManager,
                                  AccountAgeWitnessService accountAgeWitnessService,
                                  Clock clock) {
        super(dataModel);

        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;
        this.btcAddressValidator = btcAddressValidator;
        this.p2PService = p2PService;
        this.closedTradableManager = closedTradableManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.clock = clock;
    }

    @Override
    protected void activate() {
    }

    // Dont set own listener as we need to control the order of the calls
    public void onSelectedItemChanged(PendingTradesListItem selectedItem) {
        if (tradeStateSubscription != null) {
            tradeStateSubscription.unsubscribe();
            sellerState.set(SellerState.UNDEFINED);
            buyerState.set(BuyerState.UNDEFINED);
        }
        if (selectedItem != null) {
            this.trade = selectedItem.getTrade();
            tradeStateSubscription = EasyBind.subscribe(trade.stateProperty(), this::onTradeStateChanged);
        }
    }

    @Override
    protected void deactivate() {
        if (tradeStateSubscription != null) {
            tradeStateSubscription.unsubscribe();
            tradeStateSubscription = null;
        }
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

    public String getPayoutAmount() {
        return dataModel.getTrade() != null ? btcFormatter.formatCoinWithCode(dataModel.getTrade().getPayoutAmount()) : "";
    }

    String getMarketLabel(PendingTradesListItem item) {
        if ((item == null))
            return "";

        return btcFormatter.getCurrencyPair(item.getTrade().getOffer().getCurrencyCode());
    }
    // trade period

    private long getMaxTradePeriod() {
        return dataModel.getOffer() != null ? dataModel.getOffer().getPaymentMethod().getMaxTradePeriod() : 0;
    }

    private long getTimeWhenDisputeOpens() {
        return dataModel.getTrade() != null ? dataModel.getTrade().getDate().getTime() + getMaxTradePeriod() : 0;
    }

    private long getTimeWhenHalfPeriodReached() {
        return dataModel.getTrade() != null ? dataModel.getTrade().getDate().getTime() + getMaxTradePeriod() / 2 : 0;
    }

    private Date getDateWhenDisputeOpens() {
        return new Date(getTimeWhenDisputeOpens());
    }

    private Date getDateWhenHalfPeriodReached() {
        return new Date(getTimeWhenHalfPeriodReached());
    }

    private long getRemainingTradeDuration() {
        return getDateWhenDisputeOpens().getTime() - new Date().getTime();
    }

    public String getRemainingTradeDurationAsWords() {
        return btcFormatter.formatDurationAsWords(Math.max(0, getRemainingTradeDuration()));
    }

    public double getRemainingTradeDurationAsPercentage() {
        long maxPeriod = getMaxTradePeriod();
        long remaining = getRemainingTradeDuration();
        if (maxPeriod != 0) {
            return 1 - (double) remaining / (double) maxPeriod;
        } else
            return 0;
    }

    public String getDateForOpenDispute() {
        return btcFormatter.formatDateTime(new Date(new Date().getTime() + getRemainingTradeDuration()));
    }

    public boolean showWarning() {
        return new Date().after(getDateWhenHalfPeriodReached());
    }

    public boolean showDispute() {
        return new Date().after(getDateWhenDisputeOpens());
    }

    //

    String getMyRole(PendingTradesListItem item) {
        Trade trade = item.getTrade();
        Contract contract = trade.getContract();
        if (contract != null) {
            Offer offer = trade.getOffer();
            return btcFormatter.getRole(contract.isBuyerMakerAndSellerTaker(), dataModel.isMaker(offer), offer.getCurrencyCode());
        } else {
            return "";
        }
    }

    String getPaymentMethod(PendingTradesListItem item) {
        String result = "";
        if (item != null) {
            Offer offer = item.getTrade().getOffer();
            String method = Res.get(offer.getPaymentMethod().getId() + "_SHORT");
            String methodCountryCode = offer.getCountryCode();

            if (methodCountryCode != null)
                result = method + " (" + methodCountryCode + ")";
            else
                result = method;
        }
        return result;
    }

    // summary
    public String getTradeVolume() {
        return dataModel.getTrade() != null ? btcFormatter.formatCoinWithCode(dataModel.getTrade().getTradeAmount()) : "";
    }

    public String getFiatVolume() {
        return dataModel.getTrade() != null ? btcFormatter.formatVolumeWithCode(dataModel.getTrade().getTradeVolume()) : "";
    }

    public String getTxFee() {
        if (trade != null) {
            Coin txFee = dataModel.getTxFee();
            String percentage = GUIUtil.getPercentageOfTradeAmount(txFee, trade.getTradeAmount(), btcFormatter);
            return btcFormatter.formatCoinWithCode(txFee) + percentage;
        } else {
            return "";
        }
    }

    public String getTradeFee() {
        if (trade != null && dataModel.getOffer() != null) {
            if (dataModel.getOffer().isCurrencyForMakerFeeBtc()) {
                Coin tradeFeeInBTC = dataModel.getTradeFeeInBTC();
                String percentage = GUIUtil.getPercentageOfTradeAmount(tradeFeeInBTC, trade.getTradeAmount(), btcFormatter);
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
        if (offer != null && trade != null) {
            Coin securityDeposit = dataModel.isBuyer() ?
                    offer.getBuyerSecurityDeposit()
                    : offer.getSellerSecurityDeposit();
            String percentage = GUIUtil.getPercentageOfTradeAmount(securityDeposit, trade.getTradeAmount(), btcFormatter);
            return btcFormatter.formatCoinWithCode(securityDeposit) + percentage;
        } else {
            return "";
        }
    }

    public boolean isBlockChainMethod() {
        return dataModel.getOffer() != null && dataModel.getOffer().getPaymentMethod().equals(PaymentMethod.BLOCK_CHAINS);
    }

    public int getNumPastTrades(Trade trade) {
        return closedTradableManager.getClosedTradables().stream()
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // States
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onTradeStateChanged(Trade.State tradeState) {
        Log.traceCall(tradeState.toString());
        log.debug("UI tradeState={}, id={}",
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
            case TAKER_PUBLISHED_DEPOSIT_TX:

                // DEPOSIT_TX_PUBLISHED_MSG
                // taker perspective
            case TAKER_SENT_DEPOSIT_TX_PUBLISHED_MSG:
            case TAKER_SAW_ARRIVED_DEPOSIT_TX_PUBLISHED_MSG:
            case TAKER_STORED_IN_MAILBOX_DEPOSIT_TX_PUBLISHED_MSG:
            case TAKER_SEND_FAILED_DEPOSIT_TX_PUBLISHED_MSG:

                // maker perspective
            case MAKER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG:

                // Alternatively the maker could have seen the deposit tx earlier before he received the DEPOSIT_TX_PUBLISHED_MSG
            case MAKER_SAW_DEPOSIT_TX_IN_NETWORK:
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
                if (DevEnv.DEV_MODE)
                    throw new RuntimeException("unhandled processState " + tradeState);
                break;
        }
    }
}
