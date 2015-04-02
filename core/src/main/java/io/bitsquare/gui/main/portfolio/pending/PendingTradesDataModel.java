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

import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.viewfx.model.Activatable;
import io.bitsquare.common.viewfx.model.DataModel;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.BuyerAsOffererTrade;
import io.bitsquare.trade.BuyerAsTakerTrade;
import io.bitsquare.trade.BuyerTrade;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.SellerAsOffererTrade;
import io.bitsquare.trade.SellerAsTakerTrade;
import io.bitsquare.trade.SellerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.states.TradeState;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import java.util.stream.Collectors;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

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

    private final ListChangeListener<Trade> tradesListChangeListener;

    final StringProperty txId = new SimpleStringProperty();
    final IntegerProperty selectedIndex = new SimpleIntegerProperty(-1);

    final ObjectProperty<TradeState.ProcessState> takerAsSellerProcessState = new SimpleObjectProperty<>();
    final ObjectProperty<TradeState.ProcessState> offererAsBuyerProcessState = new SimpleObjectProperty<>();
    final ObjectProperty<TradeState.ProcessState> takerAsBuyerProcessState = new SimpleObjectProperty<>();
    final ObjectProperty<TradeState.ProcessState> offererAsSellerProcessState = new SimpleObjectProperty<>();

    final ObjectProperty<Trade> currentTrade = new SimpleObjectProperty<>();

    @Inject
    public PendingTradesDataModel(TradeManager tradeManager, WalletService walletService, User user) {
        this.tradeManager = tradeManager;
        this.walletService = walletService;
        this.user = user;

        tradesListChangeListener = change -> onListChanged();
    }

    @Override
    public void activate() {
        onListChanged();
        tradeManager.getPendingTrades().addListener(tradesListChangeListener);

        if (list.size() > 0) {
            selectTrade(list.get(0));
            selectedIndex.set(0);
        }
    }

    @Override
    public void deactivate() {
        tradeManager.getPendingTrades().removeListener(tradesListChangeListener);
        unbindStates();
    }

    private void onListChanged() {
        list.clear();
        list.addAll(tradeManager.getPendingTrades().stream().map(PendingTradesListItem::new).collect(Collectors.toList()));

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getTrade().getDate().compareTo(o1.getTrade().getDate()));

        if (list.size() > 0) {
            selectTrade(list.get(0));
            selectedIndex.set(0);
        }
    }

    boolean isBuyOffer() {
        if (getTrade() != null)
            return getTrade().getOffer().getDirection() == Offer.Direction.BUY;
        else
            return false;
    }

    void selectTrade(PendingTradesListItem item) {
        // clean up previous selectedItem
        unbindStates();

        selectedItem = item;

        if (item == null) {
            currentTrade.set(null);
        }
        else {
            currentTrade.set(item.getTrade());

            Trade trade = item.getTrade();
            isOfferer = trade.getOffer().getP2pSigPubKey().equals(user.getP2pSigPubKey());

            // TODO merge states
            if (trade instanceof SellerAsTakerTrade)
                takerAsSellerProcessState.bind(trade.processStateProperty());
            else if (trade instanceof BuyerAsOffererTrade)
                offererAsBuyerProcessState.bind(trade.processStateProperty());
            else if (trade instanceof BuyerAsTakerTrade)
                takerAsBuyerProcessState.bind(trade.processStateProperty());
            else if (trade instanceof SellerAsOffererTrade)
                offererAsSellerProcessState.bind(trade.processStateProperty());

            if (trade.getDepositTx() != null)
                txId.set(trade.getDepositTx().getHashAsString());
        }
    }

    void fiatPaymentStarted() {
        if (getTrade() instanceof BuyerTrade)
            ((BuyerTrade) getTrade()).onFiatPaymentStarted();
    }

    void fiatPaymentReceived() {
        if (getTrade() instanceof SellerTrade)
            ((SellerTrade) getTrade()).onFiatPaymentReceived();
    }

    void withdraw(String toAddress) {
        if (getTrade() != null) {
            tradeManager.requestWithdraw(toAddress,
                    getTrade(),
                    () -> log.debug("requestWithdraw was successful"),
                    (errorMessage, throwable) -> {
                        log.error(errorMessage);
                        Popups.openExceptionPopup(throwable);
                    });
        

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
    }

    ObservableList<PendingTradesListItem> getList() {
        return list;
    }

    boolean isOfferer() {
        return isOfferer;
    }

   /* @Nullable
    Trade getTrade() {
        return selectedItem != null ? selectedItem.getTrade() : null;
    }*/

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
        return selectedItem.getTrade().getOffer().getCurrencyCode();
    }

    Throwable getTradeException() {
        if (getTrade() != null)
            return getTrade().getThrowable();
        else
            return null;
    }

    String getErrorMessage() {
        if (getTrade() != null)
            return getTrade().getErrorMessage();
        else
            return null;
    }

    public Offer.Direction getDirection(Offer offer) {
        return offer.getP2pSigPubKey().equals(user.getP2pSigPubKey()) ?
                offer.getDirection() : offer.getMirroredDirection();
    }

    private void unbindStates() {
        takerAsSellerProcessState.unbind();
        takerAsBuyerProcessState.unbind();
        offererAsBuyerProcessState.unbind();
        offererAsSellerProcessState.unbind();
    }

    public Coin getPayoutAmount() {
        return getTrade().getPayoutAmount();
    }

    public Contract getContract() {
        if (getTrade() != null)
            return getTrade().getContract();
        else
            return null;
    }

    public Trade getTrade() {
        if (currentTrade.get() != null)
            return currentTrade.get();
        else
            return null;
    }
}

