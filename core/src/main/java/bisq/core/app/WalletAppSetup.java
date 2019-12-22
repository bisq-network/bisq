/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.app;

import bisq.core.btc.exceptions.RejectedTxException;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;

import org.bitcoinj.core.VersionMessage;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.ChainFileLockedException;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@Singleton
public class WalletAppSetup {
    private final WalletsManager walletsManager;
    private final WalletsSetup walletsSetup;
    private final BisqEnvironment bisqEnvironment;
    private final Preferences preferences;

    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<String> btcInfoBinding;

    @Getter
    private final DoubleProperty btcSyncProgress = new SimpleDoubleProperty(-1);
    @Getter
    private final StringProperty walletServiceErrorMsg = new SimpleStringProperty();
    @Getter
    private final StringProperty btcSplashSyncIconId = new SimpleStringProperty();
    @Getter
    private final StringProperty btcInfo = new SimpleStringProperty(Res.get("mainView.footer.btcInfo.initializing"));
    @Getter
    private final ObjectProperty<RejectedTxException> rejectedTxException = new SimpleObjectProperty<>();
    @Getter
    private int numBtcPeers = 0;
    @Getter
    private final BooleanProperty useTorForBTC = new SimpleBooleanProperty();

    @Inject
    public WalletAppSetup(WalletsManager walletsManager,
                          WalletsSetup walletsSetup,
                          BisqEnvironment bisqEnvironment,
                          Preferences preferences) {
        this.walletsManager = walletsManager;
        this.walletsSetup = walletsSetup;
        this.bisqEnvironment = bisqEnvironment;
        this.preferences = preferences;
        this.useTorForBTC.set(preferences.getUseTorForBitcoinJ());
    }

    void init(@Nullable Consumer<String> chainFileLockedExceptionHandler,
              @Nullable Consumer<String> spvFileCorruptedHandler,
              @Nullable Runnable showFirstPopupIfResyncSPVRequestedHandler,
              Runnable walletPasswordHandler,
              Runnable downloadCompleteHandler,
              Runnable walletInitializedHandler) {
        log.info("Initialize WalletAppSetup with BitcoinJ version {} and hash of BitcoinJ commit {}",
                VersionMessage.BITCOINJ_VERSION, "cd30ad5b");

        ObjectProperty<Throwable> walletServiceException = new SimpleObjectProperty<>();
        btcInfoBinding = EasyBind.combine(walletsSetup.downloadPercentageProperty(),
                walletsSetup.numPeersProperty(),
                walletServiceException,
                (downloadPercentage, numPeers, exception) -> {
                    String result;
                    if (exception == null) {
                        double percentage = (double) downloadPercentage;
                        int peers = (int) numPeers;
                        btcSyncProgress.set(percentage);
                        if (percentage == 1) {
                            result = Res.get("mainView.footer.btcInfo",
                                    peers,
                                    Res.get("mainView.footer.btcInfo.synchronizedWith"),
                                    getBtcNetworkAsString());
                            getBtcSplashSyncIconId().set("image-connection-synced");

                            downloadCompleteHandler.run();
                        } else if (percentage > 0.0) {
                            result = Res.get("mainView.footer.btcInfo",
                                    peers,
                                    Res.get("mainView.footer.btcInfo.synchronizingWith"),
                                    getBtcNetworkAsString() + ": " + FormattingUtils.formatToPercentWithSymbol(percentage));
                        } else {
                            result = Res.get("mainView.footer.btcInfo",
                                    peers,
                                    Res.get("mainView.footer.btcInfo.connectingTo"),
                                    getBtcNetworkAsString());
                        }
                    } else {
                        result = Res.get("mainView.footer.btcInfo",
                                getNumBtcPeers(),
                                Res.get("mainView.footer.btcInfo.connectionFailed"),
                                getBtcNetworkAsString());
                        log.error(exception.toString());
                        if (exception instanceof TimeoutException) {
                            getWalletServiceErrorMsg().set(Res.get("mainView.walletServiceErrorMsg.timeout"));
                        } else if (exception.getCause() instanceof BlockStoreException) {
                            if (exception.getCause().getCause() instanceof ChainFileLockedException && chainFileLockedExceptionHandler != null) {
                                chainFileLockedExceptionHandler.accept(Res.get("popup.warning.startupFailed.twoInstances"));
                            } else if (spvFileCorruptedHandler != null) {
                                spvFileCorruptedHandler.accept(Res.get("error.spvFileCorrupted", exception.getMessage()));
                            }
                        } else if (exception instanceof RejectedTxException) {
                            rejectedTxException.set((RejectedTxException) exception);
                            getWalletServiceErrorMsg().set(Res.get("mainView.walletServiceErrorMsg.rejectedTxException", exception.getMessage()));
                        } else {
                            getWalletServiceErrorMsg().set(Res.get("mainView.walletServiceErrorMsg.connectionError", exception.toString()));
                        }
                    }
                    return result;

                });
        btcInfoBinding.subscribe((observable, oldValue, newValue) -> getBtcInfo().set(newValue));

        walletsSetup.initialize(null,
                () -> {
                    numBtcPeers = walletsSetup.numPeersProperty().get();

                    // We only check one wallet as we apply encryption to all or none
                    if (walletsManager.areWalletsEncrypted()) {
                        walletPasswordHandler.run();
                    } else {
                        if (preferences.isResyncSpvRequested()) {
                            if (showFirstPopupIfResyncSPVRequestedHandler != null)
                                showFirstPopupIfResyncSPVRequestedHandler.run();
                        } else {
                            walletInitializedHandler.run();
                        }
                    }
                },
                walletServiceException::set);
    }

    private String getBtcNetworkAsString() {
        String postFix;
        if (bisqEnvironment.isBitcoinLocalhostNodeRunning())
            postFix = " " + Res.get("mainView.footer.localhostBitcoinNode");
        else if (preferences.getUseTorForBitcoinJ())
            postFix = " " + Res.get("mainView.footer.usingTor");
        else
            postFix = "";
        return Res.get(BisqEnvironment.getBaseCurrencyNetwork().name()) + postFix;
    }
}
