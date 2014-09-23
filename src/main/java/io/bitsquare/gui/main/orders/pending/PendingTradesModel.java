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

import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.TxConfidenceListener;
import io.bitsquare.gui.UIModel;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.TransactionConfidence;

import com.google.inject.Inject;

import java.util.Optional;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradesModel extends UIModel {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesModel.class);

    private final TradeManager tradeManager;
    private WalletFacade walletFacade;
    private final ObservableList<PendingTradesListItem> pendingTrades = FXCollections.observableArrayList();

    private PendingTradesListItem currentItem;
    private boolean isOfferer;
    final IntegerProperty selectedIndex = new SimpleIntegerProperty(-1);

    final ObjectProperty<Trade.State> tradeState = new SimpleObjectProperty<>();
    final ObjectProperty<Throwable> fault = new SimpleObjectProperty<>();
    final StringProperty txId = new SimpleStringProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesModel(TradeManager tradeManager, WalletFacade walletFacade) {
        this.tradeManager = tradeManager;
        this.walletFacade = walletFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        super.initialize();
        // transform trades to list of PendingTradesListItems and keep it updated
        tradeManager.getTrades().values().stream().forEach(e -> pendingTrades.add(new PendingTradesListItem(e)));
        tradeManager.getTrades().addListener((MapChangeListener<String, Trade>) change -> {
            if (change.wasAdded())
                pendingTrades.add(new PendingTradesListItem(change.getValueAdded()));
            else if (change.wasAdded())
                pendingTrades.remove(new PendingTradesListItem(change.getValueRemoved()));
        });
    }

    @Override
    public void activate() {
        super.activate();

        // TODO Check if we can really use tradeManager.getPendingTrade() 
        Optional<PendingTradesListItem> currentTradeItemOptional = pendingTrades.stream().filter((e) ->
                tradeManager.getCurrentPendingTrade() != null &&
                        e.getTrade().getId().equals(tradeManager.getCurrentPendingTrade().getId())).findFirst();
        if (currentTradeItemOptional.isPresent())
            selectPendingTrade(currentTradeItemOptional.get());
        else if (pendingTrades.size() > 0)
            selectPendingTrade(pendingTrades.get(0));
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
        if (item != null) {
            currentItem = item;
            isOfferer = tradeManager.isTradeMyOffer(currentItem.getTrade());

            // we want to re-trigger a change if the state is the same but different trades
            tradeState.set(null);

            selectedIndex.set(pendingTrades.indexOf(item));
            Trade currentTrade = currentItem.getTrade();
            if (currentTrade.getDepositTx() != null) {
                walletFacade.addTxConfidenceListener(new TxConfidenceListener(currentItem.getTrade()
                        .getDepositTx().getHashAsString()) {
                    @Override
                    public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                        updateConfidence(confidence);
                    }
                });
                updateConfidence(walletFacade.getConfidenceForTxId(currentItem.getTrade().getDepositTx()
                        .getHashAsString()));
            }

            if (currentItem.getTrade().getDepositTx() != null)
                txId.set(currentItem.getTrade().getDepositTx().getHashAsString());
            else
                txId.set("");

            currentTrade.stateProperty().addListener((ov, oldValue, newValue) -> tradeState.set(newValue));
            tradeState.set(currentTrade.stateProperty().get());

            currentTrade.faultProperty().addListener((ov, oldValue, newValue) -> fault.set(newValue));
            fault.set(currentTrade.faultProperty().get());
        }
    }

    public void paymentStarted() {
        tradeManager.bankTransferInited(currentItem.getTrade().getId());
    }

    public void paymentReceived() {
        tradeManager.onFiatReceived(currentItem.getTrade().getId());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    ObservableList<PendingTradesListItem> getPendingTrades() {
        return pendingTrades;
    }

    public boolean isOfferer() {
        return isOfferer;
    }

    public Trade getTrade() {
        return currentItem.getTrade();
    }

    public Coin getTotalFees() {
        Coin tradeFee = isOfferer() ? FeePolicy.CREATE_OFFER_FEE : FeePolicy.TAKE_OFFER_FEE;
        return tradeFee.add(FeePolicy.TX_FEE);
    }

    public WalletFacade getWalletFacade() {
        return walletFacade;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateConfidence(TransactionConfidence confidence) {
        if (confidence != null &&
                confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING
                && currentItem.getTrade().getState() == Trade.State.DEPOSIT_PUBLISHED)
            currentItem.getTrade().setState(Trade.State.DEPOSIT_CONFIRMED);
    }

}
   
