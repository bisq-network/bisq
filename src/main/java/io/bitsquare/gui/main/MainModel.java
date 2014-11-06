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

package io.bitsquare.gui.main;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.UIModel;
import io.bitsquare.gui.util.Profiler;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.BootstrapListener;
import io.bitsquare.network.BootstrapState;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;

import org.bitcoinj.core.DownloadListener;

import com.google.inject.Inject;

import java.util.Date;

import javax.inject.Named;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MainModel extends UIModel {
    private static final Logger log = LoggerFactory.getLogger(MainModel.class);

    private final User user;
    private final WalletFacade walletFacade;
    private final MessageFacade messageFacade;
    private final TradeManager tradeManager;
    private final Persistence persistence;
    private final int clientPort;

    private boolean messageFacadeInited;
    private boolean walletFacadeInited;
    private boolean facadesInitialised;

    final BooleanProperty backendReady = new SimpleBooleanProperty();
    final DoubleProperty networkSyncProgress = new SimpleDoubleProperty(-1);
    final IntegerProperty numPendingTrades = new SimpleIntegerProperty(0);
    final StringProperty bootstrapState = new SimpleStringProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MainModel(User user, WalletFacade walletFacade, MessageFacade messageFacade,
                      TradeManager tradeManager, Persistence persistence,
                      @Named("clientPort") int clientPort) {
        this.user = user;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;
        this.tradeManager = tradeManager;
        this.persistence = persistence;
        this.clientPort = clientPort;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("EmptyMethod")
    @Override
    public void initialize() {
        super.initialize();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    void initBackend() {

        // For testing with the serverside seednode we need the BootstrappedPeerFactory which gets started form
        // messageFacade.init

        messageFacade.init(clientPort, new BootstrapListener() {
            @Override
            public void onCompleted() {
                messageFacadeInited = true;
                if (walletFacadeInited) onFacadesInitialised();
            }

            @Override
            public void onFailed(Throwable throwable) {
                log.error(throwable.toString());
            }

            @Override
            public void onBootstrapStateChanged(BootstrapState bootstrapState) {
                MainModel.this.bootstrapState.set(bootstrapState.getMessage());
            }
        });

        Profiler.printMsgWithTime("MainModel.initFacades");

        DownloadListener downloadListener = new DownloadListener() {
            @Override
            protected void progress(double percent, int blocksLeft, Date date) {
                super.progress(percent, blocksLeft, date);
                Platform.runLater(() -> {
                    networkSyncProgress.set(percent / 100.0);

                    if (facadesInitialised && percent >= 100.0)
                        backendReady.set(true);
                });
            }

            @Override
            protected void doneDownload() {
                super.doneDownload();
                Platform.runLater(() -> {
                    networkSyncProgress.set(1.0);

                    if (facadesInitialised)
                        backendReady.set(true);
                });
            }
        };

        walletFacade.initialize(downloadListener, () -> {
            walletFacadeInited = true;
            if (messageFacadeInited)
                onFacadesInitialised();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setCurrentBankAccount(BankAccount bankAccount) {
        user.setCurrentBankAccount(bankAccount);
        persistence.write(user);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    ObservableList<BankAccount> getBankAccounts() {
        return user.getBankAccounts();
    }

    ObjectProperty<BankAccount> currentBankAccountProperty() {
        return user.currentBankAccountProperty();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onFacadesInitialised() {
        tradeManager.getPendingTrades().addListener((MapChangeListener<String,
                Trade>) change -> updateNumPendingTrades());
        updateNumPendingTrades();

        facadesInitialised = true;

        if (networkSyncProgress.get() >= 1.0)
            backendReady.set(true);
    }

    private void updateNumPendingTrades() {
        numPendingTrades.set(tradeManager.getPendingTrades().size());
    }

}
