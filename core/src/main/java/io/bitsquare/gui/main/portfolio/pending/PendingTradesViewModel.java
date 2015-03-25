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
import io.bitsquare.trade.Trade;

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
    private final InvalidationListener stateChangeListener;
    private final BtcAddressValidator btcAddressValidator;

    final StringProperty txId = new SimpleStringProperty();
    final ObjectProperty<State> state = new SimpleObjectProperty<>();
    final BooleanProperty withdrawalButtonDisable = new SimpleBooleanProperty(true);
    final IntegerProperty selectedIndex = new SimpleIntegerProperty(-1);


    @Inject
    public PendingTradesViewModel(PendingTradesDataModel dataModel, BSFormatter formatter,
                                  BtcAddressValidator btcAddressValidator) {
        super(dataModel);

        this.formatter = formatter;
        this.btcAddressValidator = btcAddressValidator;
        this.stateChangeListener = (ov) -> updateState();
    }

    @Override
    public void doActivate() {
        txId.bind(dataModel.txId);
        selectedIndex.bind(dataModel.selectedIndex);

        dataModel.tradeState.addListener(stateChangeListener);
        updateState();
    }

    @Override
    public void doDeactivate() {
        txId.unbind();
        selectedIndex.unbind();

        dataModel.tradeState.removeListener(stateChangeListener);
    }


    void selectTrade(PendingTradesListItem item) {
        dataModel.selectTrade(item);
        updateState();
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
        return formatter.formatCoinWithCode(dataModel.getAmountToWithdraw()); //.subtract(FeePolicy.TX_FEE));
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
        return BSResources.get(dataModel.getTrade().getContract().takerFiatAccount.type.toString());
    }

    String getFiatAmount() {
        return formatter.formatFiatWithCode(dataModel.getTrade().getTradeVolume());
    }

    String getHolderName() {
        return dataModel.getTrade().getContract().takerFiatAccount.accountHolderName;
    }

    String getPrimaryId() {
        return dataModel.getTrade().getContract().takerFiatAccount.accountPrimaryID;
    }

    String getSecondaryId() {
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


    private void updateState() {
        Trade.ProcessState tradeProcessState = dataModel.tradeState.get();
        log.trace("tradeState " + tradeProcessState);
        if (tradeProcessState != null) {
            switch (tradeProcessState) {
                case DEPOSIT_PUBLISHED:
                    state.set(dataModel.isOfferer() ? State.OFFERER_BUYER_WAIT_TX_CONF : State.TAKER_SELLER_WAIT_TX_CONF);
                    break;
                case DEPOSIT_CONFIRMED:
                    state.set(dataModel.isOfferer() ? State.OFFERER_BUYER_START_PAYMENT :
                            State.TAKER_SELLER_WAIT_PAYMENT_STARTED);
                    break;
                case FIAT_PAYMENT_STARTED:
                    state.set(dataModel.isOfferer() ? State.OFFERER_BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED :
                            State.TAKER_SELLER_CONFIRM_RECEIVE_PAYMENT);
                    break;
                case PAYOUT_PUBLISHED:
                    state.set(dataModel.isOfferer() ? State.OFFERER_BUYER_COMPLETED : State.TAKER_SELLER_COMPLETED);
                    break;
                case MESSAGE_SENDING_FAILED:
                    // TODO error states not implemented yet
                    break;
                default:
                    log.warn("unhandled state " + tradeProcessState);
                    break;
            }
        }
        else {
            state.set(null);
        }
    }

}
