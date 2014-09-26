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
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.BSResources;

import com.google.inject.Inject;

import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradesPM extends PresentationModel<PendingTradesModel> {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesPM.class);
    private final BSFormatter formatter;
    private InvalidationListener stateChangeListener;

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

    final StringProperty txId = new SimpleStringProperty();
    final ObjectProperty<State> state = new SimpleObjectProperty<>();
    final ObjectProperty<Throwable> fault = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    PendingTradesPM(PendingTradesModel model, BSFormatter formatter) {
        super(model);

        this.formatter = formatter;
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

    void closeSummary() {
        model.closeSummary();
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

    // columns
    String getTradeId(PendingTradesListItem item) {
        return item.getTrade().getId();
    }

    String getAmount(PendingTradesListItem item) {
        return (item != null) ? formatter.formatAmountWithMinAmount(item.getTrade().getOffer()) : "";
    }

    String getPrice(PendingTradesListItem item) {
        return (item != null) ? formatter.formatFiat(item.getTrade().getOffer().getPrice()) : "";
    }

    String getVolume(PendingTradesListItem item) {
        return (item != null) ? formatter.formatVolumeWithMinVolume(item.getTrade().getOffer()) : "";
    }

    String getDirectionLabel(PendingTradesListItem item) {
        return (item != null) ? formatter.formatDirection(item.getTrade().getOffer().getMirroredDirection()) : "";
    }

    String getDate(PendingTradesListItem item) {
        return formatter.formatDateTime(item.getTrade().getDate());
    }

    // payment
    String getPaymentMethod() {
        return BSResources.get(model.getTrade().getContract().getTakerBankAccount().getBankAccountType().toString());
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
        return formatter.formatCoinWithCode(model.getTrade().getCollateralAmount());
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
