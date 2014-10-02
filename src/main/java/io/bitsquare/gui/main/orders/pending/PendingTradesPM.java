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

package io.bitsquare.gui.main.orders.pending;

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcAddressValidator;
import io.bitsquare.locale.BSResources;

import com.google.inject.Inject;

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradesPM extends PresentationModel<PendingTradesModel> {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesPM.class);

    enum State {
        TAKER_SELLER_WAIT_TX_CONF,
        TAKER_SELLER_WAIT_PAYMENT_STARTED,
        TAKER_SELLER_CONFIRM_RECEIVE_PAYMENT,
        TAKER_SELLER_COMPLETED,

        OFFERER_BUYER_WAIT_TX_CONF,
        OFFERER_BUYER_START_PAYMENT,
        OFFERER_BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED,
        OFFERER_BUYER_COMPLETED,
    }

    private final BSFormatter formatter;
    private InvalidationListener stateChangeListener;
    private BtcAddressValidator btcAddressValidator;

    final StringProperty txId = new SimpleStringProperty();
    final ObjectProperty<State> state = new SimpleObjectProperty<>();
    final ObjectProperty<Throwable> fault = new SimpleObjectProperty<>();
    final BooleanProperty withdrawalButtonDisable = new SimpleBooleanProperty(true);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    PendingTradesPM(PendingTradesModel model, BSFormatter formatter,
                    BtcAddressValidator btcAddressValidator) {
        super(model);

        this.formatter = formatter;
        this.btcAddressValidator = btcAddressValidator;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        stateChangeListener = (ov) -> updateState();

        super.initialize();
    }

    @Override
    public void activate() {
        super.activate();

        txId.bind(model.txId);
        fault.bind(model.fault);

        model.tradeState.addListener(stateChangeListener);
        updateState();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();

        txId.unbind();
        fault.unbind();

        model.tradeState.removeListener(stateChangeListener);
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    void selectTrade(PendingTradesListItem item) {
        model.selectTrade(item);
    }

    void fiatPaymentStarted() {
        model.fiatPaymentStarted();
    }

    void fiatPaymentReceived() {
        model.fiatPaymentReceived();
    }

    void removePendingTrade() {
        model.removePendingTrade();
    }

    void withdraw(String withdrawToAddress) {
        // TODO address validation
        if (withdrawToAddress != null && withdrawToAddress.length() > 0)
            model.withdraw(withdrawToAddress);
        else
            Popups.openWarningPopup("Please fill in a withdrawal address where you want to send your bitcoins.");
    }

    void withdrawAddressFocusOut(String text) {
        withdrawalButtonDisable.set(!btcAddressValidator.validate(text).isValid);
    }

    String getAmountToWithdraw() {
        return formatter.formatCoinWithCode(model.getAmountToWithdraw());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    ObservableList<PendingTradesListItem> getList() {
        return model.getList();
    }

    boolean isOfferer() {
        return model.isOfferer();
    }

    WalletFacade getWalletFacade() {
        return model.getWalletFacade();
    }

    PendingTradesListItem getSelectedItem() {
        return model.getSelectedItem();
    }

    String getCurrencyCode() {
        return model.getCurrencyCode();
    }

    // columns
    String getTradeId(PendingTradesListItem item) {
        return item.getTrade().getId();
    }

    String getAmount(PendingTradesListItem item) {
        return (item != null) ? formatter.formatCoinWithCode(item.getTrade().getTradeAmount()) : "";
    }

    String getPrice(PendingTradesListItem item) {
        return (item != null) ? formatter.formatFiat(item.getTrade().getOffer().getPrice()) : "";
    }

    String getVolume(PendingTradesListItem item) {
        return (item != null) ? formatter.formatFiatWithCode(item.getTrade().getTradeVolume()) : "";
    }

    String getDirectionLabel(PendingTradesListItem item) {
        return (item != null) ? formatter.formatDirection(model.getDirection(item.getTrade().getOffer())) : "";
    }

    String getDate(PendingTradesListItem item) {
        return formatter.formatDateTime(item.getTrade().getDate());
    }

    // payment
    String getPaymentMethod() {
        return BSResources.get(model.getTrade().getContract().getTakerBankAccount().getBankAccountType().toString());
    }

    String getFiatAmount() {
        return formatter.formatFiatWithCode(model.getTrade().getTradeVolume());
    }

    String getHolderName() {
        return model.getTrade().getContract().getTakerBankAccount().getAccountHolderName();
    }

    String getPrimaryId() {
        return model.getTrade().getContract().getTakerBankAccount().getAccountPrimaryID();
    }

    String getSecondaryId() {
        return model.getTrade().getContract().getTakerBankAccount().getAccountSecondaryID();
    }

    // summary
    String getTradeVolume() {
        return formatter.formatCoinWithCode(model.getTrade().getTradeAmount());
    }

    String getFiatVolume() {
        return formatter.formatFiatWithCode(model.getTrade().getTradeVolume());
    }

    String getTotalFees() {
        return formatter.formatCoinWithCode(model.getTotalFees());
    }

    String getCollateral() {
        // collateral is handled different for offerer and taker.
        // Offerer have paid in the max amount, but taker might have taken less so also paid in less collateral
        if (model.isOfferer())
            return formatter.formatCoinWithCode(model.getTrade().getOffer().getCollateralAmount());
        else
            return formatter.formatCoinWithCode(model.getTrade().getCollateralAmount());
    }

    BtcAddressValidator getBtcAddressValidator() {
        return btcAddressValidator;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateState() {
        if (model.tradeState.get() != null) {
            switch (model.tradeState.get()) {
                case DEPOSIT_PUBLISHED:
                    state.set(model.isOfferer() ? State.OFFERER_BUYER_WAIT_TX_CONF : State.TAKER_SELLER_WAIT_TX_CONF);
                    break;
                case DEPOSIT_CONFIRMED:
                    state.set(model.isOfferer() ? State.OFFERER_BUYER_START_PAYMENT :
                            State.TAKER_SELLER_WAIT_PAYMENT_STARTED);
                    break;
                case PAYMENT_STARTED:
                    state.set(model.isOfferer() ? State.OFFERER_BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED :
                            State.TAKER_SELLER_CONFIRM_RECEIVE_PAYMENT);
                    break;
                case COMPLETED:
                    state.set(model.isOfferer() ? State.OFFERER_BUYER_COMPLETED : State.TAKER_SELLER_COMPLETED);
                    break;
                case FAILED:
                    // TODO error states not implemented yet
                    break;
                default:
                    log.warn("unhandled state " + state);
                    break;
            }
        }
        else {
            state.set(null);
        }
    }

}
