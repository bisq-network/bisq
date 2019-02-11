package bisq.desktop.main.presentation;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;

import javax.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import javafx.collections.MapChangeListener;

import lombok.Getter;

public class DaoPresentation implements DaoStateListener {

    public static final String DAO_NEWS = "daoNewsVersion0.9.4";

    private final Preferences preferences;
    private final BtcWalletService btcWalletService;
    private final DaoFacade daoFacade;
    private final BsqWalletService bsqWalletService;
    private final DaoStateService daoStateService;
    
    private final ChangeListener<Number> walletChainHeightListener;

    @Getter
    private final DoubleProperty bsqSyncProgress = new SimpleDoubleProperty(-1);
    @Getter
    private final StringProperty bsqInfo = new SimpleStringProperty(Res.get("mainView.footer.bsqInfo.initializing"));
    private final SimpleBooleanProperty showNotification = new SimpleBooleanProperty(false);

    @Inject
    public DaoPresentation(Preferences preferences,
                           BtcWalletService btcWalletService,
                           BsqWalletService bsqWalletService,
                           DaoStateService daoStateService,
                           DaoFacade daoFacade) {
        this.preferences = preferences;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.daoFacade = daoFacade;
        this.daoStateService = daoStateService;
        
        preferences.getDontShowAgainMapAsObservable().addListener((MapChangeListener<? super String, ? super Boolean>) change -> {
            if (change.getKey().equals(DAO_NEWS) && !BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
                showNotification.set(!change.wasAdded());
            }
        });

        walletChainHeightListener = (observable, oldValue, newValue) -> onUpdateAnyChainHeight();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onUpdateAnyChainHeight() {
        final int bsqBlockChainHeight = daoFacade.getChainHeight();
        final int bsqWalletChainHeight = bsqWalletService.getBestChainHeight();
        if (bsqWalletChainHeight > 0) {
            final boolean synced = bsqWalletChainHeight == bsqBlockChainHeight;
            if (bsqBlockChainHeight != bsqWalletChainHeight) {
                bsqSyncProgress.set(-1);
            } else {
                bsqSyncProgress.set(0);
            }

            if (synced) {
                bsqInfo.set("");
            } else {
                bsqInfo.set(Res.get("mainView.footer.bsqInfo.synchronizing"));
            }
        } else {
            bsqInfo.set(Res.get("mainView.footer.bsqInfo.synchronizing"));
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {

    }

    @Override
    public void onParseBlockChainComplete() {

    }

    @Override
    public void onParseTxsCompleteAfterBatchProcessing(Block block) {
        onUpdateAnyChainHeight();
    }

    @Override
    public void onParseTxsComplete(Block block) {

    }

    public BooleanProperty getShowDaoUpdatesNotification() {
        return showNotification;
    }

    public void setup() {
        if (!BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq())
            showNotification.set(preferences.showAgain(DAO_NEWS));

        this.btcWalletService.getChainHeightProperty().addListener(walletChainHeightListener);
        daoStateService.addBsqStateListener(this);
        
        onUpdateAnyChainHeight();
    }
}
