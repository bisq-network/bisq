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
import io.bitsquare.msg.DHTSeedService;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.actor.event.PeerInitialized;
import io.bitsquare.msg.listeners.BootstrapListener;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;

import org.bitcoinj.core.DownloadListener;

import com.google.inject.Inject;

import java.util.Date;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MainModel extends UIModel {
    private static final Logger log = LoggerFactory.getLogger(MainModel.class);

    private final User user;
    private final DHTSeedService dhtSeedService;
    private final WalletFacade walletFacade;
    private final MessageFacade messageFacade;
    private final TradeManager tradeManager;
    private final Persistence persistence;

    private boolean messageFacadeInited;
    private boolean walletFacadeInited;

    final BooleanProperty backendReady = new SimpleBooleanProperty();
    final DoubleProperty networkSyncProgress = new SimpleDoubleProperty(-1);
    final IntegerProperty numPendingTrades = new SimpleIntegerProperty(0);
    private boolean facadesInitialised;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MainModel(User user, DHTSeedService dhtSeedService, WalletFacade walletFacade, MessageFacade messageFacade,
                      TradeManager tradeManager, Persistence persistence) {
        this.user = user;
        this.dhtSeedService = dhtSeedService;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;
        this.tradeManager = tradeManager;
        this.persistence = persistence;
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

        dhtSeedService.setHandler(m -> {
            if (m instanceof PeerInitialized) {
                log.debug("dht seed initialized. ");
                // init messageFacade after seed node initialized
                messageFacade.init(new BootstrapListener() {
                    @Override
                    public void onCompleted() {
                        messageFacadeInited = true;
                        if (walletFacadeInited)
                            onFacadesInitialised();
                    }

                    @Override
                    public void onFailed(Throwable throwable) {
                        log.error(throwable.toString());
                    }
                });
            }
        });

        dhtSeedService.initializePeer();

       /* messageFacade.init(new BootstrapListener() {
            @Override
            public void onCompleted() {
                messageFacadeInited = true;
                if (walletFacadeInited) onFacadesInitialised();
            }

            @Override
            public void onFailed(Throwable throwable) {
                log.error(throwable.toString());
            }
        });*/

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
