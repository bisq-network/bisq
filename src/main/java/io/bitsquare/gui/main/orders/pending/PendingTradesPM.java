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
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Trade;

import com.google.inject.Inject;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradesPM extends PresentationModel<PendingTradesModel> {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesPM.class);
    private BSFormatter formatter;
    private BSResources resources;


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


    final StringProperty amount = new SimpleStringProperty();
    final StringProperty price = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final IntegerProperty selectedIndex = new SimpleIntegerProperty(-1);
    final ObjectProperty<State> state = new SimpleObjectProperty<>();
    final ObjectProperty<Trade.State> tradeState = new SimpleObjectProperty<>();
    final ObjectProperty<Throwable> fault = new SimpleObjectProperty<>();
    final StringProperty txId = new SimpleStringProperty();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesPM(PendingTradesModel model, BSFormatter formatter, BSResources resources) {
        super(model);
        this.formatter = formatter;
        this.resources = resources;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        selectedIndex.bind(model.selectedIndex);
        txId.bind(model.txId);
        model.tradeState.addListener((ov, oldValue, newValue) -> {
            updateState();
        });
        fault.bind(model.fault);

        super.initialize();
    }

    @Override
    public void activate() {
        super.activate();

        updateState();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    public void selectPendingTrade(PendingTradesListItem item) {
        model.selectPendingTrade(item);
    }

    public void paymentStarted() {
        model.paymentStarted();
    }

    public void paymentReceived() {
        model.paymentReceived();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    ObservableList<PendingTradesListItem> getPendingTrades() {
        return model.getPendingTrades();
    }


    public boolean isOfferer() {
        return model.isOfferer();
    }

    public WalletFacade getWalletFacade() {
        return model.getWalletFacade();
    }

    String getAmount(PendingTradesListItem item) {
        return (item != null) ? BSFormatter.formatCoin(item.getOffer().getAmount()) +
                " (" + BSFormatter.formatCoin(item.getOffer().getMinAmount()) + ")" : "";
    }

    String getPrice(PendingTradesListItem item) {
        return (item != null) ? BSFormatter.formatFiat(item.getOffer().getPrice()) : "";
    }

    String getVolume(PendingTradesListItem item) {
        return (item != null) ? BSFormatter.formatFiat(item.getOffer().getOfferVolume()) +
                " (" + BSFormatter.formatFiat(item.getOffer().getMinOfferVolume()) + ")" : "";
    }

    String getBankAccountType(PendingTradesListItem item) {
        return (item != null) ? BSResources.get(item.getOffer().getBankAccountType().toString()) : "";
    }

    String getDirectionLabel(PendingTradesListItem item) {
        // mirror direction!
        if (item != null) {
            Direction direction = item.getOffer().getDirection() == Direction.BUY ? Direction.SELL : Direction.BUY;
            return BSFormatter.formatDirection(direction, true);
        }
        else {
            return "";
        }
    }

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
        log.debug("updateState " + model.tradeState.get());
        if (model.tradeState.get() != null) {
            switch (model.tradeState.get()) {
                case DEPOSIT_PUBLISHED:
                    state.set(model.isOfferer() ? State.OFFERER_BUYER_WAIT_TX_CONF : State.TAKER_SELLER_WAIT_TX_CONF);
                    break;
                case DEPOSIT_CONFIRMED:
                    state.set(model.isOfferer() ? State.OFFERER_BUYER_START_PAYMENT : State
                            .TAKER_SELLER_WAIT_PAYMENT_STARTED);
                    break;
                case PAYMENT_STARTED:
                    state.set(model.isOfferer() ? State.OFFERER_BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED : State
                            .TAKER_SELLER_CONFIRM_RECEIVE_PAYMENT);
                    break;
                case PAYMENT_RECEIVED:
                case PAYOUT_PUBLISHED:
                    state.set(model.isOfferer() ? State.OFFERER_BUYER_COMPLETED : State.TAKER_SELLER_COMPLETED);
                    break;
                case FAULT:
                    // TODO
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
