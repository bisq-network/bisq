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

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.TxConfidenceListener;
import io.bitsquare.gui.UIModel;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;

import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;

import com.google.common.util.concurrent.FutureCallback;

import com.google.inject.Inject;

import java.util.Optional;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PendingTradesModel extends UIModel {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesModel.class);

    private final TradeManager tradeManager;
    private final WalletFacade walletFacade;
    private final User user;

    private final ObservableList<PendingTradesListItem> list = FXCollections.observableArrayList();

    private PendingTradesListItem selectedItem;
    private boolean isOfferer;
    private Trade closedTrade;

    private TxConfidenceListener txConfidenceListener;
    private ChangeListener<Trade.State> stateChangeListener;
    private ChangeListener<Throwable> faultChangeListener;
    private MapChangeListener<String, Trade> mapChangeListener;

    final StringProperty txId = new SimpleStringProperty();
    final ObjectProperty<Trade.State> tradeState = new SimpleObjectProperty<>();
    final ObjectProperty<Throwable> fault = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    PendingTradesModel(TradeManager tradeManager, WalletFacade walletFacade, User user) {
        this.tradeManager = tradeManager;
        this.walletFacade = walletFacade;
        this.user = user;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        stateChangeListener = (ov, oldValue, newValue) -> tradeState.set(newValue);
        faultChangeListener = (ov, oldValue, newValue) -> fault.set(newValue);

        mapChangeListener = change -> {
            if (change.wasAdded())
                list.add(new PendingTradesListItem(change.getValueAdded()));
            else if (change.wasRemoved())
                closedTrade = change.getValueRemoved();
        };

        super.initialize();
    }

    @Override
    public void activate() {
        super.activate();

        list.clear();
        // transform trades to list of PendingTradesListItems and keep it updated
        tradeManager.getPendingTrades().values().stream()
                .forEach(e -> list.add(new PendingTradesListItem(e)));
        tradeManager.getPendingTrades().addListener(mapChangeListener);

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getTrade().getDate().compareTo(o1.getTrade().getDate()));

        // select either currentPendingTrade or first in the list
        Optional<PendingTradesListItem> currentTradeItemOptional = list.stream()
                .filter((e) -> tradeManager.getCurrentPendingTrade() != null &&
                        tradeManager.getCurrentPendingTrade().getId().equals(e.getTrade().getId()))
                .findFirst();
        if (currentTradeItemOptional.isPresent())
            selectTrade(currentTradeItemOptional.get());
        else if (list.size() > 0)
            selectTrade(list.get(0));
    }

    @Override
    public void deactivate() {
        super.deactivate();

        tradeManager.getPendingTrades().removeListener(mapChangeListener);
        selectTrade(null);
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
        // clean up previous selectedItem
        if (selectedItem != null) {
            Trade trade = getTrade();
            trade.stateProperty().removeListener(stateChangeListener);
            trade.faultProperty().removeListener(faultChangeListener);

            if (txConfidenceListener != null)
                walletFacade.removeTxConfidenceListener(txConfidenceListener);
        }

        selectedItem = item;

        if (selectedItem != null) {
            isOfferer = getTrade().getOffer().getMessagePublicKey().equals(user.getMessagePublicKey());

            // we want to re-trigger a change if the state is the same but different trades
            tradeState.set(null);

            Trade trade = getTrade();
            if (trade.getDepositTx() != null)
                txId.set(trade.getDepositTx().getHashAsString());

            txConfidenceListener = new TxConfidenceListener(txId.get()) {
                @Override
                public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                    updateConfidence(confidence);
                }
            };
            walletFacade.addTxConfidenceListener(txConfidenceListener);
            updateConfidence(walletFacade.getConfidenceForTxId(txId.get()));

            trade.stateProperty().addListener(stateChangeListener);
            tradeState.set(trade.stateProperty().get());

            trade.faultProperty().addListener(faultChangeListener);
            fault.set(trade.faultProperty().get());

            if (closedTrade != null) {
                list.removeIf(e -> e.getTrade().getId().equals(closedTrade.getId()));
            }
        }
        else {
            txId.set(null);
            tradeState.set(null);
        }
    }

    void fiatPaymentStarted() {
        tradeManager.fiatPaymentStarted(getTrade().getId());
    }

    void fiatPaymentReceived() {
        tradeManager.fiatPaymentReceived(getTrade().getId());
    }

    void removePendingTrade() {
        if (closedTrade != null) {
            list.removeIf(e -> e.getTrade().getId().equals(closedTrade.getId()));
        }
    }

    void withdraw(String toAddress) {
        FutureCallback<Transaction> callback = new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@javax.annotation.Nullable Transaction transaction) {
                if (transaction != null) {
                    log.info("onWithdraw onSuccess tx ID:" + transaction.getHashAsString());
                }
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                log.debug("onWithdraw onFailure");
            }
        };

        AddressEntry addressEntry = walletFacade.getAddressInfoByTradeID(getTrade().getId());
        String fromAddress = addressEntry.getAddressString();
        try {
            walletFacade.sendFunds(fromAddress, toAddress, getAmountToWithdraw(), callback);
        } catch (AddressFormatException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        } catch (InsufficientMoneyException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }

/*
        Action response = Popups.openConfirmPopup(
                "Withdrawal request", "Confirm your request",
                "Your withdrawal request:\n\n" + "Amount: " + amountTextField.getText() + " BTC\n" + "Sending" +
                        " address: " + withdrawFromTextField.getText() + "\n" + "Receiving address: " +
                        withdrawToTextField.getText() + "\n" + "Transaction fee: " +
                        formatter.formatCoinWithCode(FeePolicy.TX_FEE) + "\n" +
                        "You receive in total: " +
                        formatter.formatCoinWithCode(amount.subtract(FeePolicy.TX_FEE)) + " BTC\n\n" +
                        "Are you sure you withdraw that amount?");

        if (response == Dialog.Actions.OK) {
            try {
                walletFacade.sendFunds(
                        withdrawFromTextField.getText(), withdrawToTextField.getText(),
                        changeAddressTextField.getText(), amount, callback);
            } catch (AddressFormatException e) {
                Popups.openErrorPopup("Address invalid",
                        "The address is not correct. Please check the address format.");

            } catch (InsufficientMoneyException e) {
                Popups.openInsufficientMoneyPopup();
            } catch (IllegalArgumentException e) {
                Popups.openErrorPopup("Wrong inputs", "Please check the inputs.");
            }
        }*/
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    ObservableList<PendingTradesListItem> getList() {
        return list;
    }

    boolean isOfferer() {
        return isOfferer;
    }

    Trade getTrade() {
        return selectedItem.getTrade();
    }

    Coin getTotalFees() {
        return FeePolicy.TX_FEE.add(isOfferer() ? FeePolicy.CREATE_OFFER_FEE : FeePolicy.TAKE_OFFER_FEE);
    }

    WalletFacade getWalletFacade() {
        return walletFacade;
    }

    PendingTradesListItem getSelectedItem() {
        return selectedItem;
    }

    String getCurrencyCode() {
        return selectedItem.getTrade().getOffer().getCurrency().getCurrencyCode();
    }

    public Direction getDirection(Offer offer) {
        return offer.getMessagePublicKey().equals(user.getMessagePublicKey()) ?
                offer.getDirection() : offer.getMirroredDirection();
    }

    Coin getAmountToWithdraw() {
        AddressEntry addressEntry = walletFacade.getAddressInfoByTradeID(getTrade().getId());
        log.debug("trade id " + getTrade().getId());
        log.debug("getAddressString " + addressEntry.getAddressString());
        log.debug("funds  " + walletFacade.getBalanceForAddress(addressEntry.getAddress()).subtract(FeePolicy
                .TX_FEE).toString());
        // return walletFacade.getBalanceForAddress(addressEntry.getAddress()).subtract(FeePolicy.TX_FEE);

        // TODO handle overpaid collateral
        if (isOfferer())
            return getTrade().getTradeAmount().add(getTrade().getOffer().getCollateralAmount()).subtract(FeePolicy
                    .TX_FEE);
        else
            return getTrade().getCollateralAmount().subtract(FeePolicy.TX_FEE);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateConfidence(TransactionConfidence confidence) {
        if (confidence != null &&
                confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING
                && getTrade().getState() == Trade.State.DEPOSIT_PUBLISHED) {
            // only set it once when actual state is DEPOSIT_PUBLISHED, and remove listener afterwards
            getTrade().setState(Trade.State.DEPOSIT_CONFIRMED);
            walletFacade.removeTxConfidenceListener(txConfidenceListener);
            txConfidenceListener = null;
        }
    }

}
   
