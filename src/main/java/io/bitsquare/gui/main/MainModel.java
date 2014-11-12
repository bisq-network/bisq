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
import io.bitsquare.btc.WalletService;
import io.bitsquare.gui.UIModel;
import io.bitsquare.gui.util.Profiler;
import io.bitsquare.msg.MessageService;
import io.bitsquare.msg.listeners.BootstrapListener;
import io.bitsquare.network.BootstrapState;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;

import com.google.inject.Inject;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MainModel extends UIModel {
    private static final Logger log = LoggerFactory.getLogger(MainModel.class);

    private final User user;
    private final WalletService walletService;
    private final MessageService messageService;
    private final TradeManager tradeManager;
    private final Persistence persistence;

    private boolean messageServiceInited;
    private boolean walletServiceInited;
    private boolean servicesInitialised;

    final BooleanProperty backendReady = new SimpleBooleanProperty();
    final DoubleProperty networkSyncProgress = new SimpleDoubleProperty(-1);
    final IntegerProperty numPendingTrades = new SimpleIntegerProperty(0);
    final ObjectProperty<BootstrapState> bootstrapState = new SimpleObjectProperty<>();
    final ObjectProperty walletServiceException = new SimpleObjectProperty<Throwable>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MainModel(User user, WalletService walletService, MessageService messageService,
                      TradeManager tradeManager, Persistence persistence) {
        this.user = user;
        this.walletService = walletService;
        this.messageService = messageService;
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

        // For testing with the bootstrap node we need the BootstrappedPeerFactory which gets started from
        // messageService.init

        messageService.init(new BootstrapListener() {
            @Override
            public void onCompleted() {
                messageServiceInited = true;
                if (walletServiceInited) onServicesInitialised();
            }

            @Override
            public void onFailed(Throwable throwable) {
                log.error(throwable.toString());
            }

            @Override
            public void onBootstrapStateChanged(BootstrapState bootstrapState) {
                MainModel.this.bootstrapState.set(bootstrapState);
            }
        });

        Profiler.printMsgWithTime("MainModel.initServices");

        WalletService.BlockchainDownloadListener blockchainDownloadListener = new WalletService
                .BlockchainDownloadListener() {
            @Override
            public void progress(double percentage) {
                networkSyncProgress.set(percentage / 100.0);

                if (servicesInitialised && percentage >= 100.0)
                    backendReady.set(true);
            }

            @Override
            public void doneDownload() {
                networkSyncProgress.set(1.0);

                if (servicesInitialised)
                    backendReady.set(true);
            }
        };

        WalletService.StartupListener startupListener = new WalletService.StartupListener() {
            @Override
            public void completed() {
                walletServiceInited = true;
                if (messageServiceInited)
                    onServicesInitialised();
            }

            @Override
            public void failed(final Throwable failure) {
                walletServiceException.set(failure);
            }
        };

        walletService.initialize(Platform::runLater, blockchainDownloadListener, startupListener);
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

    private void onServicesInitialised() {
        tradeManager.getPendingTrades().addListener((MapChangeListener<String,
                Trade>) change -> updateNumPendingTrades());
        updateNumPendingTrades();

        servicesInitialised = true;

        if (networkSyncProgress.get() >= 1.0)
            backendReady.set(true);
    }

    private void updateNumPendingTrades() {
        numPendingTrades.set(tradeManager.getPendingTrades().size());
    }

}
