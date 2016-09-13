/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.portfolio.pendingtrades;

import com.google.inject.Inject;
import io.bitsquare.app.Log;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.common.Clock;
import io.bitsquare.gui.common.model.ActivatableWithDataModel;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcAddressValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.user.User;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import org.bitcoinj.core.BlockChainListener;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.Date;
import java.util.stream.Collectors;

import static io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel.SellerState.*;

public class PendingTradesViewModel extends ActivatableWithDataModel<PendingTradesDataModel> implements ViewModel {
    private Subscription tradeStateSubscription;

    interface State {
    }

    enum BuyerState implements State {
        UNDEFINED,
        WAIT_FOR_BLOCKCHAIN_CONFIRMATION,
        REQUEST_START_FIAT_PAYMENT,
        WAIT_FOR_FIAT_PAYMENT_RECEIPT,
        WAIT_FOR_BROADCAST_AFTER_UNLOCK,
        REQUEST_WITHDRAWAL
    }

    enum SellerState implements State {
        UNDEFINED,
        WAIT_FOR_BLOCKCHAIN_CONFIRMATION,
        WAIT_FOR_FIAT_PAYMENT_STARTED,
        REQUEST_CONFIRM_FIAT_PAYMENT_RECEIVED,
        WAIT_FOR_PAYOUT_TX,
        WAIT_FOR_BROADCAST_AFTER_UNLOCK,
        REQUEST_WITHDRAWAL
    }

    public final BSFormatter formatter;
    public final BtcAddressValidator btcAddressValidator;

    public final P2PService p2PService;
    public final User user;
    private ClosedTradableManager closedTradableManager;
    public final Clock clock;

    private final ObjectProperty<BuyerState> buyerState = new SimpleObjectProperty<>();
    private final ObjectProperty<SellerState> sellerState = new SimpleObjectProperty<>();

    private final BooleanProperty withdrawalButtonDisable = new SimpleBooleanProperty(true);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesViewModel(PendingTradesDataModel dataModel,
                                  BSFormatter formatter,
                                  BtcAddressValidator btcAddressValidator,
                                  P2PService p2PService,
                                  User user,
                                  ClosedTradableManager closedTradableManager,
                                  Clock clock) {
        super(dataModel);

        this.formatter = formatter;
        this.btcAddressValidator = btcAddressValidator;
        this.p2PService = p2PService;
        this.user = user;
        this.closedTradableManager = closedTradableManager;
        this.clock = clock;
    }

    private ChangeListener<Trade.State> tradeStateChangeListener;

    @Override
    protected void activate() {
    }

    // Dont set own listener as we need to control the order of the calls
    public void onSelectedItemChanged(PendingTradesListItem selectedItem) {
        if (tradeStateSubscription != null)
            tradeStateSubscription.unsubscribe();

        if (selectedItem != null)
            tradeStateSubscription = EasyBind.subscribe(selectedItem.getTrade().stateProperty(), this::onTradeStateChanged);
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

    public void withdrawAddressFocusOut(String text) {
        withdrawalButtonDisable.set(!btcAddressValidator.validate(text).isValid);
    }

    public String getPayoutAmount() {
        return dataModel.getTrade() != null ? formatter.formatCoinWithCode(dataModel.getTrade().getPayoutAmount()) : "";
    }

    String getMarketLabel(PendingTradesListItem item) {
        if ((item == null))
            return "";

        return formatter.getCurrencyPair(item.getTrade().getOffer().getCurrencyCode());
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
        return formatter.formatDurationAsWords(Math.max(0, getRemainingTradeDuration()));
    }

    public double getRemainingTradeDurationAsPercentage() {
        long maxPeriod = getMaxTradePeriod();
        long remaining = getRemainingTradeDuration();
        if (maxPeriod != 0) {
            double v = 1 - (double) remaining / (double) maxPeriod;
            return v;
        } else
            return 0;
    }

    public String getDateForOpenDispute() {
        return formatter.formatDateTime(new Date(new Date().getTime() + getRemainingTradeDuration()));
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
            return formatter.getRole(contract.isBuyerOffererAndSellerTaker(), dataModel.isOfferer(offer), offer.getCurrencyCode());
        } else {
            return "";
        }
    }

    String getPaymentMethod(PendingTradesListItem item) {
        String result = "";
        if (item != null) {
            Offer offer = item.getTrade().getOffer();
            String method = BSResources.get(offer.getPaymentMethod().getId() + "_SHORT");
            String methodCountryCode = offer.getCountryCode();

            if (methodCountryCode != null)
                result = method + " (" + methodCountryCode + ")";
            else
                result = method;
        }
        return result;
    }

    public void addBlockChainListener(BlockChainListener blockChainListener) {
        dataModel.addBlockChainListener(blockChainListener);
    }

    public void removeBlockChainListener(BlockChainListener blockChainListener) {
        dataModel.removeBlockChainListener(blockChainListener);
    }

    public long getLockTime() {
        return dataModel.getLockTime();
    }

    public String getPaymentMethod() {
        if (dataModel.getTrade() != null && dataModel.getTrade().getContract() != null)
            return BSResources.get(dataModel.getTrade().getContract().getPaymentMethodName());
        else
            return "";
    }

    // summary
    public String getTradeVolume() {
        return dataModel.getTrade() != null ? formatter.formatCoinWithCode(dataModel.getTrade().getTradeAmount()) : "";
    }

    public String getFiatVolume() {
        return dataModel.getTrade() != null ? formatter.formatVolumeWithCode(dataModel.getTrade().getTradeVolume()) : "";
    }

    public String getTotalFees() {
        return formatter.formatCoinWithCode(dataModel.getTotalFees());
    }

    public String getSecurityDeposit() {
        return formatter.formatCoinWithCode(FeePolicy.getSecurityDeposit());
    }

    public boolean isBlockChainMethod() {
        return dataModel.getOffer() != null && dataModel.getOffer().getPaymentMethod().equals(PaymentMethod.BLOCK_CHAINS);
    }

    public int getNumPastTrades(Trade trade) {
        return closedTradableManager.getClosedTrades().stream()
                .filter(e -> {
                    if (e instanceof Trade) {
                        Trade t = (Trade) e;
                        return t.getTradingPeerNodeAddress() != null &&
                                trade.getTradingPeerNodeAddress() != null &&
                                t.getTradingPeerNodeAddress().hostName.equals(trade.getTradingPeerNodeAddress().hostName);
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

        // TODO what is first valid state for trade?

        switch (tradeState) {
            case PREPARATION:
                sellerState.set(UNDEFINED);
                buyerState.set(PendingTradesViewModel.BuyerState.UNDEFINED);
                break;

            case TAKER_FEE_PAID:
            case OFFERER_SENT_PUBLISH_DEPOSIT_TX_REQUEST:
            case TAKER_PUBLISHED_DEPOSIT_TX:
            case DEPOSIT_SEEN_IN_NETWORK:
            case TAKER_SENT_DEPOSIT_TX_PUBLISHED_MSG:
            case OFFERER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG:
                sellerState.set(WAIT_FOR_BLOCKCHAIN_CONFIRMATION);
                buyerState.set(PendingTradesViewModel.BuyerState.WAIT_FOR_BLOCKCHAIN_CONFIRMATION);
                break;

            case DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN:
                sellerState.set(WAIT_FOR_FIAT_PAYMENT_STARTED);
                buyerState.set(PendingTradesViewModel.BuyerState.REQUEST_START_FIAT_PAYMENT);
            case BUYER_CONFIRMED_FIAT_PAYMENT_INITIATED:  // we stick with the state until we get the msg sent success
                buyerState.set(PendingTradesViewModel.BuyerState.REQUEST_START_FIAT_PAYMENT);
                break;
            case BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG:
                buyerState.set(PendingTradesViewModel.BuyerState.WAIT_FOR_FIAT_PAYMENT_RECEIPT);
                break;
            case SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG: // seller
            case SELLER_CONFIRMED_FIAT_PAYMENT_RECEIPT:  // we stick with the state until we get the msg sent success
                sellerState.set(REQUEST_CONFIRM_FIAT_PAYMENT_RECEIVED);
                break;
            case SELLER_SENT_FIAT_PAYMENT_RECEIPT_MSG:
                sellerState.set(WAIT_FOR_PAYOUT_TX);
                break;
            case BUYER_RECEIVED_FIAT_PAYMENT_RECEIPT_MSG:
            case BUYER_COMMITTED_PAYOUT_TX:
            case BUYER_STARTED_SEND_PAYOUT_TX:
                // TODO would need extra state for wait until msg arrived and PAYOUT_BROAD_CASTED gets called.
                buyerState.set(PendingTradesViewModel.BuyerState.WAIT_FOR_BROADCAST_AFTER_UNLOCK);
                break;
            case SELLER_RECEIVED_AND_COMMITTED_PAYOUT_TX:
                sellerState.set(SellerState.WAIT_FOR_BROADCAST_AFTER_UNLOCK);
                break;
            case PAYOUT_BROAD_CASTED:
                sellerState.set(REQUEST_WITHDRAWAL);
                buyerState.set(PendingTradesViewModel.BuyerState.REQUEST_WITHDRAWAL);
                break;

            case WITHDRAW_COMPLETED:
                sellerState.set(UNDEFINED);
                buyerState.set(PendingTradesViewModel.BuyerState.UNDEFINED);
                break;

            default:
                sellerState.set(UNDEFINED);
                buyerState.set(PendingTradesViewModel.BuyerState.UNDEFINED);
                log.warn("unhandled processState " + tradeState);
                break;
        }
    }
}
