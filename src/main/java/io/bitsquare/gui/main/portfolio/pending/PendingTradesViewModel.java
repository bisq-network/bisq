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
import io.bitsquare.gui.ActivatableWithDelegate;
import io.bitsquare.gui.ViewModel;
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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PendingTradesViewModel extends ActivatableWithDelegate<PendingTradesModel> implements ViewModel {
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
    final ObjectProperty<Throwable> fault = new SimpleObjectProperty<>();
    final BooleanProperty withdrawalButtonDisable = new SimpleBooleanProperty(true);


    @Inject
    public PendingTradesViewModel(PendingTradesModel delegate, BSFormatter formatter,
                                  BtcAddressValidator btcAddressValidator) {
        super(delegate);

        this.formatter = formatter;
        this.btcAddressValidator = btcAddressValidator;
        this.stateChangeListener = (ov) -> updateState();
    }

    @Override
    public void doActivate() {
        txId.bind(delegate.txId);
        fault.bind(delegate.fault);

        delegate.tradeState.addListener(stateChangeListener);
        updateState();
    }

    @Override
    public void doDeactivate() {
        txId.unbind();
        fault.unbind();

        delegate.tradeState.removeListener(stateChangeListener);
    }


    void selectTrade(PendingTradesListItem item) {
        delegate.selectTrade(item);
        updateState();
    }

    void fiatPaymentStarted() {
        delegate.fiatPaymentStarted();
    }

    void fiatPaymentReceived() {
        delegate.fiatPaymentReceived();
    }

    void withdraw(String withdrawToAddress) {
        delegate.withdraw(withdrawToAddress);
    }

    void withdrawAddressFocusOut(String text) {
        withdrawalButtonDisable.set(!btcAddressValidator.validate(text).isValid);
    }

    String getAmountToWithdraw() {
        return formatter.formatCoinWithCode(delegate.getAmountToWithdraw()); //.subtract(FeePolicy.TX_FEE));
    }

    ObservableList<PendingTradesListItem> getList() {
        return delegate.getList();
    }

    boolean isOfferer() {
        return delegate.isOfferer();
    }

    WalletService getWalletService() {
        return delegate.getWalletService();
    }

    PendingTradesListItem getSelectedItem() {
        return delegate.getSelectedItem();
    }

    String getCurrencyCode() {
        return delegate.getCurrencyCode();
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
        return (item != null) ? formatter.formatDirection(delegate.getDirection(item.getTrade().getOffer())) : "";
    }

    String formatDate(Date value) {
        return formatter.formatDateTime(value);
    }

    // payment
    String getPaymentMethod() {
        return BSResources.get(delegate.getTrade().getContract().getTakerBankAccount().getBankAccountType().toString());
    }

    String getFiatAmount() {
        return formatter.formatFiatWithCode(delegate.getTrade().getTradeVolume());
    }

    String getHolderName() {
        return delegate.getTrade().getContract().getTakerBankAccount().getAccountHolderName();
    }

    String getPrimaryId() {
        return delegate.getTrade().getContract().getTakerBankAccount().getAccountPrimaryID();
    }

    String getSecondaryId() {
        return delegate.getTrade().getContract().getTakerBankAccount().getAccountSecondaryID();
    }

    // summary
    String getTradeVolume() {
        return formatter.formatCoinWithCode(delegate.getTrade().getTradeAmount());
    }

    String getFiatVolume() {
        return formatter.formatFiatWithCode(delegate.getTrade().getTradeVolume());
    }

    String getTotalFees() {
        return formatter.formatCoinWithCode(delegate.getTotalFees());
    }

    String getSecurityDeposit() {
        // securityDeposit is handled different for offerer and taker.
        // Offerer have paid in the max amount, but taker might have taken less so also paid in less securityDeposit
        if (delegate.isOfferer())
            return formatter.formatCoinWithCode(delegate.getTrade().getOffer().getSecurityDeposit());
        else
            return formatter.formatCoinWithCode(delegate.getTrade().getSecurityDeposit());
    }

    BtcAddressValidator getBtcAddressValidator() {
        return btcAddressValidator;
    }


    private void updateState() {
        Trade.State tradeState = delegate.tradeState.get();
        log.trace("tradeState " + tradeState);
        if (tradeState != null) {
            switch (tradeState) {
                // TODO Check why OFFERER_ACCEPTED can happen, refactor state handling
                case OFFERER_ACCEPTED:
                case DEPOSIT_PUBLISHED:
                    state.set(delegate.isOfferer() ? State.OFFERER_BUYER_WAIT_TX_CONF : State.TAKER_SELLER_WAIT_TX_CONF);
                    break;
                case DEPOSIT_CONFIRMED:
                    state.set(delegate.isOfferer() ? State.OFFERER_BUYER_START_PAYMENT :
                            State.TAKER_SELLER_WAIT_PAYMENT_STARTED);
                    break;
                case PAYMENT_STARTED:
                    state.set(delegate.isOfferer() ? State.OFFERER_BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED :
                            State.TAKER_SELLER_CONFIRM_RECEIVE_PAYMENT);
                    break;
                case COMPLETED:
                    state.set(delegate.isOfferer() ? State.OFFERER_BUYER_COMPLETED : State.TAKER_SELLER_COMPLETED);
                    break;
                case FAILED:
                    // TODO error states not implemented yet
                    break;
                default:
                    log.warn("unhandled state " + tradeState);
                    break;
            }
        }
        else {
            state.set(null);
        }
    }

}
