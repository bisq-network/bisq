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

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.viewfx.model.Activatable;
import io.bitsquare.common.viewfx.model.DataModel;
import io.bitsquare.offer.Direction;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import com.google.inject.Inject;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
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

class PendingTradesDataModel implements Activatable, DataModel {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesDataModel.class);

    private final TradeManager tradeManager;
    private final WalletService walletService;
    private final User user;

    private final ObservableList<PendingTradesListItem> list = FXCollections.observableArrayList();

    private PendingTradesListItem selectedItem;
    private boolean isOfferer;
    private Trade closedTrade;

    private final ChangeListener<Trade.State> tradeStateChangeListener;
    private final MapChangeListener<String, Trade> mapChangeListener;

    final StringProperty txId = new SimpleStringProperty();
    final ObjectProperty<Trade.State> tradeState = new SimpleObjectProperty<>();
    final IntegerProperty selectedIndex = new SimpleIntegerProperty(-1);

    @Inject
    public PendingTradesDataModel(TradeManager tradeManager, WalletService walletService, User user) {
        this.tradeManager = tradeManager;
        this.walletService = walletService;
        this.user = user;

        tradeStateChangeListener = (ov, oldValue, newValue) -> tradeState.set(newValue);

        mapChangeListener = change -> {
            if (change.wasAdded()) {
                list.add(new PendingTradesListItem(change.getValueAdded()));
                if (list.size() == 1) {
                    selectTrade(list.get(0));
                    selectedIndex.set(0);
                }
            }
            else if (change.wasRemoved()) {
                closedTrade = change.getValueRemoved();
                if (list.size() == 0) {
                    selectTrade(null);
                    selectedIndex.set(-1);
                }
            }

            sortList();
        };
    }

    @Override
    public void activate() {
        list.clear();
        // transform trades to list of PendingTradesListItems and keep it updated
        tradeManager.getPendingTrades().values().stream().forEach(e -> list.add(new PendingTradesListItem(e)));
        tradeManager.getPendingTrades().addListener(mapChangeListener);

        // we sort by date, earliest first
        sortList();

        // select either currentPendingTrade or first in the list
        if (tradeManager.getCurrentPendingTrade() != null) {
            for (int i = 0; i < list.size(); i++) {
                PendingTradesListItem item = list.get(i);
                if (tradeManager.getCurrentPendingTrade().getId().equals(item.getTrade().getId())) {
                    selectedIndex.set(i);
                    selectTrade(item);
                    break;
                }
            }
        }
        else if (list.size() > 0) {
            selectTrade(list.get(0));
            selectedIndex.set(0);
        }
    }

    @Override
    public void deactivate() {
        tradeManager.getPendingTrades().removeListener(mapChangeListener);

        cleanUpSelectedTrade();
    }


    void selectTrade(PendingTradesListItem item) {
        // clean up previous selectedItem
        cleanUpSelectedTrade();

        selectedItem = item;

        if (selectedItem != null) {
            isOfferer = getTrade().getOffer().getP2PSigPubKey().equals(user.getP2PSigPubKey());

            Trade trade = getTrade();
            trade.stateProperty().addListener(tradeStateChangeListener);
            tradeState.set(trade.stateProperty().get());
            log.trace("selectTrade trade.stateProperty().get() " + trade.stateProperty().get());

            if (trade.getDepositTx() != null)
                txId.set(trade.getDepositTx().getHashAsString());
        }
        else {
            txId.set(null);
            tradeState.set(null);
        }
    }

    void fiatPaymentStarted() {
        tradeManager.onFiatPaymentStarted(getTrade().getId());
    }

    void fiatPaymentReceived() {
        tradeManager.onFiatPaymentReceived(getTrade().getId());
    }

    void withdraw(String toAddress) {
        FutureCallback<Transaction> callback = new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@javax.annotation.Nullable Transaction transaction) {
                if (transaction != null) {
                    log.info("onWithdraw onSuccess tx ID:" + transaction.getHashAsString());

                    if (closedTrade != null) {
                        list.removeIf(e -> e.getTrade().getId().equals(closedTrade.getId()));
                    }
                }
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                log.debug("onWithdraw onFailure");
            }
        };

        AddressEntry addressEntry = walletService.getAddressEntry(getTrade().getId());
        String fromAddress = addressEntry.getAddressString();
        try {
            walletService.sendFunds(fromAddress, toAddress, getAmountToWithdraw(), callback);
        } catch (AddressFormatException | InsufficientMoneyException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }

        tradeManager.onWithdrawAtTradeCompleted(getTrade());

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

        if (Popups.isOK(response)) {
            try {
                walletService.sendFunds(
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

    WalletService getWalletService() {
        return walletService;
    }

    PendingTradesListItem getSelectedItem() {
        return selectedItem;
    }

    String getCurrencyCode() {
        return selectedItem.getTrade().getOffer().getCurrency().getCurrencyCode();
    }

    public Direction getDirection(Offer offer) {
        return offer.getP2PSigPubKey().equals(user.getP2PSigPubKey()) ?
                offer.getDirection() : offer.getMirroredDirection();
    }

    Coin getAmountToWithdraw() {
        AddressEntry addressEntry = walletService.getAddressEntry(getTrade().getId());
        log.debug("trade id " + getTrade().getId());
        log.debug("getAddressString " + addressEntry.getAddressString());
        log.debug("funds  " + walletService.getBalanceForAddress(addressEntry.getAddress()).subtract(FeePolicy
                .TX_FEE).toString());
        // return walletService.getBalanceForAddress(addressEntry.getAddress()).subtract(FeePolicy.TX_FEE);

        // TODO handle overpaid securityDeposit
        if (isOfferer())
            return getTrade().getTradeAmount().add(getTrade().getOffer().getSecurityDeposit());
        else
            return getTrade().getSecurityDeposit();
    }


    private void cleanUpSelectedTrade() {
        if (selectedItem != null) {
            selectedItem.getTrade().stateProperty().removeListener(tradeStateChangeListener);
        }
    }

    private void sortList() {
        list.sort((o1, o2) -> o2.getTrade().getDate().compareTo(o1.getTrade().getDate()));
    }

}

