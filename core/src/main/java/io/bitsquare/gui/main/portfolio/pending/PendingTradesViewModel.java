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
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcAddressValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.trade.OffererAsBuyerTrade;
import io.bitsquare.trade.OffererAsSellerTrade;
import io.bitsquare.trade.TakerAsBuyerTrade;
import io.bitsquare.trade.TakerAsSellerTrade;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import com.google.inject.Inject;

import java.util.Date;

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

class PendingTradesViewModel extends ActivatableWithDataModel<PendingTradesDataModel> implements ViewModel {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesViewModel.class);

    enum ViewState {
        TAKER_SELLER_WAIT_TX_CONF,
        TAKER_SELLER_WAIT_PAYMENT_STARTED,
        TAKER_SELLER_CONFIRM_RECEIVE_PAYMENT,
        TAKER_SELLER_COMPLETED,

        OFFERER_BUYER_WAIT_TX_CONF,
        OFFERER_BUYER_START_PAYMENT,
        OFFERER_BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED,
        OFFERER_BUYER_COMPLETED,

        MESSAGE_SENDING_FAILED,
        EXCEPTION
    }

    private final BSFormatter formatter;
    private final InvalidationListener takerStateListener;
    private final InvalidationListener offererStateListener;
    private final BtcAddressValidator btcAddressValidator;

    final StringProperty txId = new SimpleStringProperty();
    final BooleanProperty withdrawalButtonDisable = new SimpleBooleanProperty(true);
    final IntegerProperty selectedIndex = new SimpleIntegerProperty(-1);
    final ObjectProperty<ViewState> viewState = new SimpleObjectProperty<>();


    @Inject
    public PendingTradesViewModel(PendingTradesDataModel dataModel, BSFormatter formatter,
                                  BtcAddressValidator btcAddressValidator) {
        super(dataModel);

        this.formatter = formatter;
        this.btcAddressValidator = btcAddressValidator;
        this.takerStateListener = (ov) -> updateTakerState();
        this.offererStateListener = (ov) -> updateOffererState();
    }

    @Override
    public void doActivate() {
        txId.bind(dataModel.txId);
        selectedIndex.bind(dataModel.selectedIndex);

        dataModel.takerProcessState.addListener(takerStateListener);
        dataModel.offererProcessState.addListener(offererStateListener);

        updateTakerState();
        updateOffererState();
    }

    @Override
    public void doDeactivate() {
        txId.unbind();
        selectedIndex.unbind();

        dataModel.takerProcessState.removeListener(takerStateListener);
        dataModel.offererProcessState.removeListener(offererStateListener);
    }


    void selectTrade(PendingTradesListItem item) {
        dataModel.selectTrade(item);
       /* updateTakerState();
        updateOffererState();*/
    }

    void fiatPaymentStarted() {
        dataModel.fiatPaymentStarted();
    }

    void fiatPaymentReceived() {
        dataModel.fiatPaymentReceived();
    }

    void withdraw(String withdrawToAddress) {
        dataModel.withdraw(withdrawToAddress);
    }

    void withdrawAddressFocusOut(String text) {
        withdrawalButtonDisable.set(!btcAddressValidator.validate(text).isValid);
    }

    String getAmountToWithdraw() {
        return formatter.formatCoinWithCode(dataModel.getAmountToWithdraw());
    }

    ObservableList<PendingTradesListItem> getList() {
        return dataModel.getList();
    }

    boolean isOfferer() {
        return dataModel.isOfferer();
    }

    WalletService getWalletService() {
        return dataModel.getWalletService();
    }

    PendingTradesListItem getSelectedItem() {
        return dataModel.getSelectedItem();
    }

    String getCurrencyCode() {
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
    String getPaymentMethod() {
        assert dataModel.getTrade().getContract() != null;
        return BSResources.get(dataModel.getTrade().getContract().takerFiatAccount.type.toString());
    }

    String getFiatAmount() {
        return formatter.formatFiatWithCode(dataModel.getTrade().getTradeVolume());
    }

    String getHolderName() {
        assert dataModel.getTrade().getContract() != null;
        return dataModel.getTrade().getContract().takerFiatAccount.accountHolderName;
    }

    String getPrimaryId() {
        assert dataModel.getTrade().getContract() != null;
        return dataModel.getTrade().getContract().takerFiatAccount.accountPrimaryID;
    }

    String getSecondaryId() {
        assert dataModel.getTrade().getContract() != null;
        return dataModel.getTrade().getContract().takerFiatAccount.accountSecondaryID;
    }

    // summary
    String getTradeVolume() {
        return formatter.formatCoinWithCode(dataModel.getTrade().getTradeAmount());
    }

    String getFiatVolume() {
        return formatter.formatFiatWithCode(dataModel.getTrade().getTradeVolume());
    }

    String getTotalFees() {
        return formatter.formatCoinWithCode(dataModel.getTotalFees());
    }

    String getSecurityDeposit() {
        // securityDeposit is handled different for offerer and taker.
        // Offerer have paid in the max amount, but taker might have taken less so also paid in less securityDeposit
        if (dataModel.isOfferer())
            return formatter.formatCoinWithCode(dataModel.getTrade().getOffer().getSecurityDeposit());
        else
            return formatter.formatCoinWithCode(dataModel.getTrade().getSecurityDeposit());
    }

    BtcAddressValidator getBtcAddressValidator() {
        return btcAddressValidator;
    }

    Throwable getTradeException() {
        return dataModel.getTradeException();
    }

    String getErrorMessage() {
        return dataModel.getErrorMessage();
    }

    private void updateTakerState() {
        if (dataModel.getTrade() instanceof TakerAsSellerTrade) {
            TakerAsSellerTrade.ProcessState processState = (TakerAsSellerTrade.ProcessState) dataModel.takerProcessState.get();
            log.debug("updateTakerState " + processState);
            if (processState != null) {
                switch (processState) {
                    case TAKE_OFFER_FEE_TX_CREATED:
                        break;
                    case TAKE_OFFER_FEE_PUBLISHED:
                        break;
                    case TAKE_OFFER_FEE_PUBLISH_FAILED:
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
                        viewState.set(ViewState.TAKER_SELLER_COMPLETED);
                        break;
                    case PAYOUT_PUBLISHED:
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
        else if (dataModel.getTrade() instanceof TakerAsBuyerTrade) {
            TakerAsBuyerTrade.ProcessState processState = (TakerAsBuyerTrade.ProcessState) dataModel.takerProcessState.get();
            log.debug("updateTakerState " + processState);
            if (processState != null) {
                switch (processState) {
                    case TAKE_OFFER_FEE_TX_CREATED:
                        break;
                    case TAKE_OFFER_FEE_PUBLISHED:
                        break;
                    case TAKE_OFFER_FEE_PUBLISH_FAILED:
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
                        viewState.set(ViewState.TAKER_SELLER_COMPLETED);
                        break;
                    case PAYOUT_PUBLISHED:
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

    private void updateOffererState() {
        if (dataModel.getTrade() instanceof OffererAsBuyerTrade) {
            OffererAsBuyerTrade.ProcessState processState = (OffererAsBuyerTrade.ProcessState) dataModel.offererProcessState.get();
            log.debug("updateOffererState " + processState);
            if (processState != null) {
                switch (processState) {
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
        else if (dataModel.getTrade() instanceof OffererAsSellerTrade) {
            OffererAsSellerTrade.ProcessState processState = (OffererAsSellerTrade.ProcessState) dataModel.offererProcessState.get();
            log.debug("updateOffererState " + processState);
            if (processState != null) {
                switch (processState) {
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

    }
}
