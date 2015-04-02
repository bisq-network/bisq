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

package io.bitsquare.gui.main.portfolio.pending;

import io.bitsquare.btc.WalletService;
import io.bitsquare.common.viewfx.model.ActivatableWithDataModel;
import io.bitsquare.common.viewfx.model.ViewModel;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.portfolio.PortfolioView;
import io.bitsquare.gui.main.portfolio.closed.ClosedTradesView;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcAddressValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.trade.states.OffererTradeState;
import io.bitsquare.trade.states.TakerTradeState;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import com.google.inject.Inject;

import java.util.Date;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradesViewModel extends ActivatableWithDataModel<PendingTradesDataModel> implements ViewModel {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesViewModel.class);

    enum ViewState {
        EMPTY,
        TAKER_SELLER_WAIT_TX_CONF,
        TAKER_SELLER_WAIT_PAYMENT_STARTED,
        TAKER_SELLER_CONFIRM_RECEIVE_PAYMENT,
        TAKER_SELLER_COMPLETED,

        OFFERER_BUYER_WAIT_TX_CONF,
        OFFERER_BUYER_START_PAYMENT,
        OFFERER_BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED,
        OFFERER_BUYER_COMPLETED,

        TAKER_BUYER_WAIT_TX_CONF,
        TAKER_BUYER_START_PAYMENT,
        TAKER_BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED,
        TAKER_BUYER_COMPLETED,

        OFFERER_SELLER_WAIT_TX_CONF,
        OFFERER_SELLER_WAIT_PAYMENT_STARTED,
        OFFERER_SELLER_CONFIRM_RECEIVE_PAYMENT,
        OFFERER_SELLER_COMPLETED,

        MESSAGE_SENDING_FAILED,
        EXCEPTION
    }

    private Navigation navigation;
    private final BSFormatter formatter;
    private final InvalidationListener takerAsSellerStateListener;
    private final InvalidationListener offererAsBuyerStateListener;
    private final InvalidationListener takerAsBuyerStateListener;
    private final InvalidationListener offererAsSellerStateListener;


    private final BtcAddressValidator btcAddressValidator;

    public final StringProperty txId = new SimpleStringProperty();
    public final BooleanProperty withdrawalButtonDisable = new SimpleBooleanProperty(true);
    final IntegerProperty selectedIndex = new SimpleIntegerProperty(-1);
    final ObjectProperty<ViewState> viewState = new SimpleObjectProperty<>(ViewState.EMPTY);


    @Inject
    public PendingTradesViewModel(PendingTradesDataModel dataModel, Navigation navigation, BSFormatter formatter,
                                  BtcAddressValidator btcAddressValidator) {
        super(dataModel);
        this.navigation = navigation;

        this.formatter = formatter;
        this.btcAddressValidator = btcAddressValidator;
        this.takerAsSellerStateListener = (ov) -> updateTakerAsSellerState();
        this.offererAsBuyerStateListener = (ov) -> updateOffererAsBuyerState();
        this.takerAsBuyerStateListener = (ov) -> updateTakerAsBuyerState();
        this.offererAsSellerStateListener = (ov) -> updateOffererAsSellerState();
    }

    @Override
    public void doActivate() {
        txId.bind(dataModel.txId);
        selectedIndex.bind(dataModel.selectedIndex);

        dataModel.takerAsSellerProcessState.addListener(takerAsSellerStateListener);
        dataModel.offererAsBuyerProcessState.addListener(offererAsBuyerStateListener);
        dataModel.takerAsBuyerProcessState.addListener(takerAsBuyerStateListener);
        dataModel.offererAsSellerProcessState.addListener(offererAsSellerStateListener);

        updateTakerAsSellerState();
        updateOffererAsBuyerState();
        updateTakerAsBuyerState();
        updateOffererAsSellerState();
    }

    @Override
    public void doDeactivate() {
        txId.unbind();
        selectedIndex.unbind();

        dataModel.takerAsSellerProcessState.removeListener(takerAsSellerStateListener);
        dataModel.offererAsBuyerProcessState.removeListener(offererAsBuyerStateListener);
        dataModel.takerAsBuyerProcessState.removeListener(takerAsBuyerStateListener);
        dataModel.offererAsSellerProcessState.removeListener(offererAsSellerStateListener);
    }


    void selectTrade(PendingTradesListItem item) {
        dataModel.selectTrade(item);
    }

    boolean isBuyOffer() {
        return dataModel.isBuyOffer();
    }


    public void fiatPaymentStarted() {
        dataModel.fiatPaymentStarted();
    }

    public void fiatPaymentReceived() {
        dataModel.fiatPaymentReceived();
    }

    public void withdraw(String withdrawToAddress) {
        dataModel.withdraw(withdrawToAddress);
        Platform.runLater(() -> navigation.navigateTo(MainView.class, PortfolioView.class, ClosedTradesView.class));
    }

    public void withdrawAddressFocusOut(String text) {
        withdrawalButtonDisable.set(!btcAddressValidator.validate(text).isValid);
    }

    public String getPayoutAmount() {
        return formatter.formatCoinWithCode(dataModel.getPayoutAmount());
    }

    ObservableList<PendingTradesListItem> getList() {
        return dataModel.getList();
    }

    public boolean isOfferer() {
        return dataModel.isOfferer();
    }

    public WalletService getWalletService() {
        return dataModel.getWalletService();
    }

    PendingTradesListItem getSelectedItem() {
        return dataModel.getSelectedItem();
    }

    public String getCurrencyCode() {
        return dataModel.getCurrencyCode();
    }

    // columns
    String formatTradeId(String value) {
        return value;
    }

    String formatTradeAmount(Coin value) {
        return formatter.formatCoinWithCode(value);
    }

    String formatPrice(Fiat value) {
        return formatter.formatFiat(value);
    }

    String formatTradeVolume(Fiat value) {
        return formatter.formatFiatWithCode(value);
    }

    String evaluateDirection(PendingTradesListItem item) {
        return (item != null) ? formatter.formatDirection(dataModel.getDirection(item.getTrade().getOffer())) : "";
    }

    String formatDate(Date value) {
        return formatter.formatDateTime(value);
    }

    // payment
    public String getPaymentMethod() {
        assert dataModel.getContract() != null;
        return BSResources.get(dataModel.getContract().sellerFiatAccount.type.toString());
    }

    public String getFiatAmount() {
        return formatter.formatFiatWithCode(dataModel.getTrade().getTradeVolume());
    }

    public String getHolderName() {
        assert dataModel.getContract() != null;
        return dataModel.getContract().sellerFiatAccount.accountHolderName;
    }

    public String getPrimaryId() {
        assert dataModel.getContract() != null;
        return dataModel.getContract().sellerFiatAccount.accountPrimaryID;
    }

    public String getSecondaryId() {
        assert dataModel.getContract() != null;
        return dataModel.getContract().sellerFiatAccount.accountSecondaryID;
    }

    // summary
    public String getTradeVolume() {
        return dataModel.getTrade() != null ? formatter.formatCoinWithCode(dataModel.getTrade().getTradeAmount()) : "";
    }

    public String getFiatVolume() {
        return formatter.formatFiatWithCode(dataModel.getTrade().getTradeVolume());
    }

    public String getTotalFees() {
        return formatter.formatCoinWithCode(dataModel.getTotalFees());
    }

    public String getSecurityDeposit() {
        // securityDeposit is handled different for offerer and taker.
        // Offerer have paid in the max amount, but taker might have taken less so also paid in less securityDeposit
        if (dataModel.isOfferer())
            return formatter.formatCoinWithCode(dataModel.getTrade().getOffer().getSecurityDeposit());
        else
            return formatter.formatCoinWithCode(dataModel.getTrade().getSecurityDeposit());
    }

    public BtcAddressValidator getBtcAddressValidator() {
        return btcAddressValidator;
    }

    Throwable getTradeException() {
        return dataModel.getTradeException();
    }

    String getErrorMessage() {
        return dataModel.getErrorMessage();
    }

    private void updateTakerAsSellerState() {
        TakerTradeState.ProcessState processState = (TakerTradeState.ProcessState) dataModel.takerAsSellerProcessState.get();
        log.debug("updateTakerAsSellerState " + processState);
        if (processState != null) {
            switch (processState) {
                case UNDEFINED:
                case TAKE_OFFER_FEE_TX_CREATED:
                case TAKE_OFFER_FEE_PUBLISHED:
                    break;
                case TAKE_OFFER_FEE_PUBLISH_FAILED:
                    viewState.set(ViewState.EXCEPTION);
                    break;

                case DEPOSIT_PUBLISHED:
                    viewState.set(ViewState.TAKER_SELLER_WAIT_TX_CONF);
                    break;
                case DEPOSIT_CONFIRMED:
                    viewState.set(ViewState.TAKER_SELLER_WAIT_PAYMENT_STARTED);
                    break;

                case FIAT_PAYMENT_STARTED:
                    viewState.set(ViewState.TAKER_SELLER_CONFIRM_RECEIVE_PAYMENT);
                    break;

                case FIAT_PAYMENT_RECEIVED:
                case PAYOUT_PUBLISHED:
                    viewState.set(ViewState.TAKER_SELLER_COMPLETED);
                    break;

                case MESSAGE_SENDING_FAILED:
                    viewState.set(ViewState.MESSAGE_SENDING_FAILED);
                    break;
                case EXCEPTION:
                    viewState.set(ViewState.EXCEPTION);
                    break;

                default:
                    log.warn("unhandled processState " + processState);
                    break;
            }
        }
    }

    private void updateOffererAsBuyerState() {
        OffererTradeState.ProcessState processState = (OffererTradeState.ProcessState) dataModel.offererAsBuyerProcessState.get();
        log.debug("updateOffererAsBuyerState " + processState);
        if (processState != null) {
            switch (processState) {
                case UNDEFINED:
                    break;
                case DEPOSIT_PUBLISHED:
                    viewState.set(ViewState.OFFERER_BUYER_WAIT_TX_CONF);
                    break;
                case DEPOSIT_CONFIRMED:
                    viewState.set(ViewState.OFFERER_BUYER_START_PAYMENT);
                    break;

                case FIAT_PAYMENT_STARTED:
                    viewState.set(ViewState.OFFERER_BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED);
                    break;

                case PAYOUT_PUBLISHED:
                    viewState.set(ViewState.OFFERER_BUYER_COMPLETED);
                    break;

                case MESSAGE_SENDING_FAILED:
                    viewState.set(ViewState.MESSAGE_SENDING_FAILED);
                    break;
                case EXCEPTION:
                    viewState.set(ViewState.EXCEPTION);
                    break;

                default:
                    log.warn("unhandled viewState " + processState);
                    break;
            }
        }
    }

    private void updateTakerAsBuyerState() {
        TakerTradeState.ProcessState processState = (TakerTradeState.ProcessState) dataModel.takerAsBuyerProcessState.get();
        log.debug("updateTakerAsBuyerState " + processState);
        if (processState != null) {
            switch (processState) {
                case UNDEFINED:
                case TAKE_OFFER_FEE_TX_CREATED:
                case TAKE_OFFER_FEE_PUBLISHED:
                    break;
                case TAKE_OFFER_FEE_PUBLISH_FAILED:
                    viewState.set(ViewState.EXCEPTION);
                    break;

                case DEPOSIT_PUBLISHED:
                    viewState.set(ViewState.TAKER_BUYER_WAIT_TX_CONF);
                    break;
                case DEPOSIT_CONFIRMED:
                    viewState.set(ViewState.TAKER_BUYER_START_PAYMENT);
                    break;

                case FIAT_PAYMENT_STARTED:
                    viewState.set(ViewState.TAKER_BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED);
                    break;

                case FIAT_PAYMENT_RECEIVED:
                case PAYOUT_PUBLISHED:
                    viewState.set(ViewState.TAKER_BUYER_COMPLETED);
                    break;

                case MESSAGE_SENDING_FAILED:
                    viewState.set(ViewState.MESSAGE_SENDING_FAILED);
                    break;
                case EXCEPTION:
                    viewState.set(ViewState.EXCEPTION);
                    break;

                default:
                    log.warn("unhandled viewState " + processState);
                    break;
            }
        }
    }

    private void updateOffererAsSellerState() {
        OffererTradeState.ProcessState processState = (OffererTradeState.ProcessState) dataModel.offererAsSellerProcessState.get();
        log.debug("updateOffererAsSellerState " + processState);
        if (processState != null) {
            switch (processState) {
                case UNDEFINED:
                    break;
                case DEPOSIT_PUBLISHED:
                    viewState.set(ViewState.OFFERER_SELLER_WAIT_TX_CONF);
                    break;
                case DEPOSIT_CONFIRMED:
                    viewState.set(ViewState.OFFERER_SELLER_WAIT_PAYMENT_STARTED);
                    break;

                case FIAT_PAYMENT_STARTED:
                    viewState.set(ViewState.OFFERER_SELLER_CONFIRM_RECEIVE_PAYMENT);
                    break;

                case FIAT_PAYMENT_RECEIVED:
                case PAYOUT_PUBLISHED:
                    viewState.set(ViewState.OFFERER_SELLER_COMPLETED);
                    break;

                case MESSAGE_SENDING_FAILED:
                    viewState.set(ViewState.MESSAGE_SENDING_FAILED);
                    break;
                case EXCEPTION:
                    viewState.set(ViewState.EXCEPTION);
                    break;

                default:
                    log.warn("unhandled processState " + processState);
                    break;
            }
        }
    }

}
