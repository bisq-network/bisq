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
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.portfolio.PortfolioView;
import io.bitsquare.gui.main.portfolio.closed.ClosedTradesView;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.BuyerTrade;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.SellerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.states.TradeState;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
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
    private Navigation navigation;

    private final ObservableList<PendingTradesListItem> list = FXCollections.observableArrayList();

    private PendingTradesListItem selectedItem;
    private boolean isOfferer;

    private final ListChangeListener<Trade> tradesListChangeListener;

    final StringProperty txId = new SimpleStringProperty();

    final ObjectProperty<TradeState.ProcessState> sellerProcessState = new SimpleObjectProperty<>();
    final ObjectProperty<TradeState.ProcessState> buyerProcessState = new SimpleObjectProperty<>();

    final ObjectProperty<Trade> currentTrade = new SimpleObjectProperty<>();

    @Inject
    public PendingTradesDataModel(TradeManager tradeManager, WalletService walletService, User user, Navigation navigation) {
        this.tradeManager = tradeManager;
        this.walletService = walletService;
        this.user = user;
        this.navigation = navigation;

        tradesListChangeListener = change -> onListChanged();
    }

    @Override
    public void activate() {
        tradeManager.getPendingTrades().addListener(tradesListChangeListener);
        onListChanged();
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
        list.sort((o1, o2) -> o2.getTrade().getTakeOfferDate().compareTo(o1.getTrade().getTakeOfferDate()));

        log.debug("onListChanged {}", list.size());
        if (list.size() > 0)
            selectTrade(list.get(0));
        else if (list.size() == 0)
            selectTrade(null);
    }

    boolean isBuyOffer() throws NoTradeFoundException {
        if (getTrade() != null)
            return getTrade().getOffer().getDirection() == Offer.Direction.BUY;
        else
            throw new NoTradeFoundException();
    }

    void selectTrade(PendingTradesListItem item) {
        log.debug("selectTrade {} {}", item != null, item != null ? item.getTrade().getId() : "null");
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

            if (trade instanceof SellerTrade)
                sellerProcessState.bind(trade.processStateProperty());
            else if (trade instanceof BuyerTrade)
                buyerProcessState.bind(trade.processStateProperty());

            if (trade.getDepositTx() != null)
                txId.set(trade.getDepositTx().getHashAsString());
        }
    }

    void fiatPaymentStarted() {
        try {
            if (getTrade() instanceof BuyerTrade)
                ((BuyerTrade) getTrade()).onFiatPaymentStarted();
        } catch (NoTradeFoundException e) {
            throw new MissingTradeException();
        }
    }

    void fiatPaymentReceived() {
        try {
            if (getTrade() instanceof SellerTrade)
                ((SellerTrade) getTrade()).onFiatPaymentReceived();
        } catch (NoTradeFoundException e) {
            throw new MissingTradeException();
        }
    }

    void withdraw(String toAddress) {
        try {
            if (getTrade() != null) {
                tradeManager.requestWithdraw(toAddress,
                        getTrade(),
                        () -> {
                            log.debug("requestWithdraw was successful");
                            Platform.runLater(() -> navigation.navigateTo(MainView.class, PortfolioView.class, ClosedTradesView.class));
                        },
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
        } catch (NoTradeFoundException e) {
            throw new MissingTradeException();
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
        try {
            return getTrade().getThrowable();
        } catch (NoTradeFoundException e) {
            return null;
        }
    }

    String getErrorMessage() {
        try {
            return getTrade().getErrorMessage();
        } catch (NoTradeFoundException e) {
            return null;
        }
    }

    public Offer.Direction getDirection(Offer offer) {
        return offer.getP2pSigPubKey().equals(user.getP2pSigPubKey()) ?
                offer.getDirection() : offer.getMirroredDirection();
    }

    private void unbindStates() {
        sellerProcessState.unbind();
        buyerProcessState.unbind();
    }

    public Coin getPayoutAmount() {
        try {
            return getTrade().getPayoutAmount();
        } catch (NoTradeFoundException e) {
            return Coin.ZERO;
        }
    }

    public Contract getContract() {
        try {
            return getTrade().getContract();
        } catch (NoTradeFoundException e) {
            throw new MissingTradeException();
        }
    }

    public Trade getTrade() throws NoTradeFoundException {
        if (currentTrade.get() != null)
            return currentTrade.get();
        else
            throw new NoTradeFoundException();
    }
}

