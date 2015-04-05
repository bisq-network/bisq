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

import io.bitsquare.btc.WalletService;
import io.bitsquare.common.viewfx.model.ActivatableWithDataModel;
import io.bitsquare.common.viewfx.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcAddressValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.states.OffererTradeState;
import io.bitsquare.trade.states.TakerTradeState;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import com.google.inject.Inject;

import java.util.Date;

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradesViewModel extends ActivatableWithDataModel<PendingTradesDataModel> implements ViewModel {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesViewModel.class);

    enum ViewState {
        UNDEFINED,
        SELLER_WAIT_TX_CONF,
        SELLER_WAIT_PAYMENT_STARTED,
        SELLER_CONFIRM_RECEIVE_PAYMENT,
        SELLER_SEND_PUBLISHED_MSG,
        SELLER_COMPLETED,

        BUYER_WAIT_TX_CONF,
        BUYER_START_PAYMENT,
        BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED,
        BUYER_COMPLETED,

        MESSAGE_SENDING_FAILED,
        TIMEOUT,
        EXCEPTION
    }

    private final BSFormatter formatter;
    private final InvalidationListener sellerStateListener;
    private final InvalidationListener buyerStateListener;
    private final BtcAddressValidator btcAddressValidator;
    private final ObjectProperty<ViewState> viewState = new SimpleObjectProperty<>(ViewState.UNDEFINED);
    private final StringProperty txId = new SimpleStringProperty();
    private final BooleanProperty withdrawalButtonDisable = new SimpleBooleanProperty(true);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesViewModel(PendingTradesDataModel dataModel, BSFormatter formatter,
                                  BtcAddressValidator btcAddressValidator) {
        super(dataModel);

        this.formatter = formatter;
        this.btcAddressValidator = btcAddressValidator;
        this.sellerStateListener = (ov) -> updateSellerState();
        this.buyerStateListener = (ov) -> updateBuyerState();
    }

    @Override
    public void doActivate() {
        txId.bind(dataModel.getTxId());

        dataModel.getSellerProcessState().addListener(sellerStateListener);
        dataModel.getBuyerProcessState().addListener(buyerStateListener);

        if (dataModel.getTrade() != null) {
            updateSellerState();
            updateBuyerState();
        }
    }

    @Override
    public void doDeactivate() {
        txId.unbind();

        dataModel.getSellerProcessState().removeListener(sellerStateListener);
        dataModel.getBuyerProcessState().removeListener(buyerStateListener);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onSelectTrade(PendingTradesListItem item) {
        dataModel.onSelectTrade(item);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    ReadOnlyObjectProperty<ViewState> getViewState() {
        return viewState;
    }

    public ReadOnlyStringProperty getTxId() {
        return txId;
    }

    public ReadOnlyBooleanProperty getWithdrawalButtonDisable() {
        return withdrawalButtonDisable;
    }

    boolean isBuyOffer() {
        return dataModel.isBuyOffer();
    }

    ReadOnlyObjectProperty<Trade> currentTrade() {
        return dataModel.getTradeProperty();
    }

    public void fiatPaymentStarted() {
        dataModel.onFiatPaymentStarted();
    }

    public void fiatPaymentReceived() {
        dataModel.onFiatPaymentReceived();
    }

    public void onWithdrawRequest(String withdrawToAddress) {
        dataModel.onWithdrawRequest(withdrawToAddress);
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
        return dataModel.isOffererRole();
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

    public BtcAddressValidator getBtcAddressValidator() {
        return btcAddressValidator;
    }

    Throwable getTradeException() {
        return dataModel.getTradeException();
    }

    String getErrorMessage() {
        return dataModel.getErrorMessage();
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
        return formatter.formatCoinWithCode(dataModel.getTrade().getTradeAmount());
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
        if (dataModel.isOffererRole())
            return formatter.formatCoinWithCode(dataModel.getTrade().getOffer().getSecurityDeposit());
        else
            return formatter.formatCoinWithCode(dataModel.getTrade().getSecurityDeposit());

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // States
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateSellerState() {
        if (dataModel.getSellerProcessState().get() instanceof TakerTradeState.ProcessState) {
            TakerTradeState.ProcessState processState = (TakerTradeState.ProcessState) dataModel.getSellerProcessState().get();
            log.debug("updateSellerState (TakerTradeState) " + processState);
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
                        viewState.set(ViewState.SELLER_WAIT_TX_CONF);
                        break;
                    case DEPOSIT_CONFIRMED:
                        viewState.set(ViewState.SELLER_WAIT_PAYMENT_STARTED);
                        break;

                    case FIAT_PAYMENT_STARTED:
                        viewState.set(ViewState.SELLER_CONFIRM_RECEIVE_PAYMENT);
                        break;

                    case FIAT_PAYMENT_RECEIVED:
                        break;
                    case PAYOUT_PUBLISHED:
                        viewState.set(ViewState.SELLER_SEND_PUBLISHED_MSG);
                        break;
                    case PAYOUT_PUBLISHED_MSG_SENT:
                        viewState.set(ViewState.SELLER_COMPLETED);
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
        else if (dataModel.getSellerProcessState().get() instanceof OffererTradeState.ProcessState) {
            OffererTradeState.ProcessState processState = (OffererTradeState.ProcessState) dataModel.getSellerProcessState().get();
            log.debug("updateSellerState (OffererTradeState) " + processState);
            if (processState != null) {
                switch (processState) {
                    case UNDEFINED:
                        break;

                    case DEPOSIT_PUBLISHED:
                        viewState.set(ViewState.SELLER_WAIT_TX_CONF);
                        break;
                    case DEPOSIT_CONFIRMED:
                        viewState.set(ViewState.SELLER_WAIT_PAYMENT_STARTED);
                        break;

                    case FIAT_PAYMENT_STARTED:
                        viewState.set(ViewState.SELLER_CONFIRM_RECEIVE_PAYMENT);
                        break;

                    case FIAT_PAYMENT_RECEIVED:
                        break;
                    case PAYOUT_PUBLISHED:
                        viewState.set(ViewState.SELLER_SEND_PUBLISHED_MSG);
                        break;
                    case PAYOUT_PUBLISHED_MSG_SENT:
                        viewState.set(ViewState.SELLER_COMPLETED);
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

    private void updateBuyerState() {
        if (dataModel.getBuyerProcessState().get() instanceof TakerTradeState.ProcessState) {
            TakerTradeState.ProcessState processState = (TakerTradeState.ProcessState) dataModel.getBuyerProcessState().get();
            log.debug("updateBuyerState (TakerTradeState)" + processState);
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
                        viewState.set(ViewState.BUYER_WAIT_TX_CONF);
                        break;
                    case DEPOSIT_CONFIRMED:
                        viewState.set(ViewState.BUYER_START_PAYMENT);
                        break;

                    case FIAT_PAYMENT_STARTED:
                        viewState.set(ViewState.BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED);
                        break;

                    case FIAT_PAYMENT_RECEIVED:
                    case PAYOUT_PUBLISHED:
                        viewState.set(ViewState.BUYER_COMPLETED);
                        break;

                    case MESSAGE_SENDING_FAILED:
                        viewState.set(ViewState.MESSAGE_SENDING_FAILED);
                        break;
                    case TIMEOUT:
                        viewState.set(ViewState.TIMEOUT);
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
        else if (dataModel.getBuyerProcessState().get() instanceof OffererTradeState.ProcessState) {
            OffererTradeState.ProcessState processState = (OffererTradeState.ProcessState) dataModel.getBuyerProcessState().get();
            log.debug("updateBuyerState (OffererTradeState) " + processState);
            if (processState != null) {
                switch (processState) {
                    case UNDEFINED:
                        break;
                    case DEPOSIT_PUBLISHED:
                        viewState.set(ViewState.BUYER_WAIT_TX_CONF);
                        break;
                    case DEPOSIT_CONFIRMED:
                        viewState.set(ViewState.BUYER_START_PAYMENT);
                        break;

                    case FIAT_PAYMENT_STARTED:
                        viewState.set(ViewState.BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED);
                        break;

                    case FIAT_PAYMENT_RECEIVED:
                    case PAYOUT_PUBLISHED:
                        viewState.set(ViewState.BUYER_COMPLETED);
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
