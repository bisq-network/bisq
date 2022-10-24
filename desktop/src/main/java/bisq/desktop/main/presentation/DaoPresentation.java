package bisq.desktop.main.presentation;

import bisq.desktop.Navigation;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import javafx.collections.MapChangeListener;

import lombok.Getter;

@Singleton
public class DaoPresentation implements DaoStateListener {
    public static final String DAO_NEWS = "daoNews";

    private final Preferences preferences;
    private final Navigation navigation;
    private final BtcWalletService btcWalletService;
    private final DaoFacade daoFacade;
    private final BsqWalletService bsqWalletService;
    private final DaoStateService daoStateService;

    private final ChangeListener<Number> walletChainHeightListener;

    @Getter
    private final DoubleProperty bsqSyncProgress = new SimpleDoubleProperty(-1);
    @Getter
    private final StringProperty bsqInfo = new SimpleStringProperty("");
    private final SimpleBooleanProperty showNotification = new SimpleBooleanProperty(false);
    private boolean daoConflictWarningShown = false;    // allow only one conflict warning per session

    @Inject
    public DaoPresentation(Preferences preferences,
                           Navigation navigation,
                           BtcWalletService btcWalletService,
                           BsqWalletService bsqWalletService,
                           DaoStateService daoStateService,
                           DaoFacade daoFacade) {
        this.preferences = preferences;
        this.navigation = navigation;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.daoFacade = daoFacade;
        this.daoStateService = daoStateService;

        preferences.getDontShowAgainMapAsObservable().addListener((MapChangeListener<? super String, ? super Boolean>) change -> {
            if (change.getKey().equals(DAO_NEWS)) {
                // devs enable this when a news badge is required
                // showNotification.set(!change.wasAdded());
                showNotification.set(false);
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
                if (daoFacade.daoStateNeedsRebuilding() && !daoConflictWarningShown) {
                    daoConflictWarningShown = true;     // only warn max 1 time per session so as not to annoy
                    GUIUtil.showDaoNeedsResyncPopup(navigation);
                }
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
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        onUpdateAnyChainHeight();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BooleanProperty getShowDaoUpdatesNotification() {
        return showNotification;
    }

    public void setup() {
        // devs enable this when a news badge is required
        //showNotification.set(DevEnv.isDaoActivated() && preferences.showAgain(DAO_NEWS));
        showNotification.set(false);

        this.btcWalletService.getChainHeightProperty().addListener(walletChainHeightListener);
        daoStateService.addDaoStateListener(this);

        onUpdateAnyChainHeight();
    }
}
