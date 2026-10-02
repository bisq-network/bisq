package bisq.desktop.main.presentation;

import bisq.desktop.Navigation;
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.dao.monitor.MonitorView;
import bisq.desktop.main.dao.monitor.daostate.DaoStateMonitorView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.popups.PopupManager;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;

import bisq.common.app.DevEnv;

import com.google.common.annotations.VisibleForTesting;

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
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Singleton
public class DaoPresentation implements DaoStateListener, DaoStateMonitoringService.Listener {
    public static final String DAO_NEWS = "daoNews";

    private final Navigation navigation;
    private final BtcWalletService btcWalletService;
    private final DaoStateMonitoringService daoStateMonitoringService;
    private final BsqWalletService bsqWalletService;
    private final DaoStateService daoStateService;

    private final ChangeListener<Number> walletChainHeightListener;
    @Getter
    private final DoubleProperty daoStateSyncProgress = new SimpleDoubleProperty(-1);
    @Getter
    private final StringProperty daoStateInfo = new SimpleStringProperty("");
    private final SimpleBooleanProperty showNotification = new SimpleBooleanProperty(false);
    // Chain height at which the user was last reminded that the DAO state needs a resync
    private int resyncReminderHeight;

    @Inject
    public DaoPresentation(Preferences preferences,
                           Navigation navigation,
                           BtcWalletService btcWalletService,
                           BsqWalletService bsqWalletService,
                           DaoStateService daoStateService,
                           DaoStateMonitoringService daoStateMonitoringService) {
        this.navigation = navigation;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.daoStateMonitoringService = daoStateMonitoringService;
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
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void init() {
        showNotification.set(false);

        btcWalletService.getChainHeightProperty().addListener(walletChainHeightListener);
        daoStateService.addDaoStateListener(this);
        daoStateMonitoringService.addListener(this);

        onUpdateAnyChainHeight();
    }

    public BooleanProperty getShowDaoUpdatesNotification() {
        return showNotification;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        onUpdateAnyChainHeight();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateMonitoringService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onDaoStateHashesChanged() {
        if (!daoStateService.isParseBlockChainComplete() || bsqWalletService.getBestChainHeight() != daoStateService.getChainHeight()) {
            return;
        }

        // We get called several times per block. While a resync is needed we remind the user once per block, so a user
        // who closed the popup sees it again at the next block.
        int chainHeight = daoStateService.getChainHeight();
        if (isResyncReminderDue(chainHeight) && showResyncPopup()) {
            resyncReminderHeight = chainHeight;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Gate for actions which create or take an offer or publish a DAO transaction. If the DAO state needs a resync,
     * the action must not run: the user gets the resync popup and the method returns false.
     */
    public boolean isDaoStateInSyncOrShowPopup() {
        if (!isDaoStateResyncNeeded()) {
            return true;
        }

        showResyncPopup();
        return false;
    }

    @VisibleForTesting
    boolean isResyncReminderDue(int chainHeight) {
        return isDaoStateResyncNeeded() && chainHeight != resyncReminderHeight;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean isDaoStateResyncNeeded() {
        return daoStateService.isParseBlockChainComplete() &&
                (daoStateMonitoringService.isInConflictWithSeedNode() ||
                        daoStateMonitoringService.isDaoStateBlockChainNotConnecting());
    }

    // Returns false if the popup could not be shown now
    private boolean showResyncPopup() {
        if (DevEnv.isIgnorePopupsInDevMode()) {
            log.warn("The DAO state needs a resync. The popup is ignored in dev mode.");
            return true;
        }

        // We do not queue behind another popup, which can be an earlier resync popup. Otherwise the popups would pile
        // up while the user is away from the screen.
        if (!PopupManager.isNoPopupDisplayed()) {
            return false;
        }

        new Popup().warning(Res.get("popup.warning.daoNeedsResync"))
                .actionButtonTextWithGoTo("navigation.dao.networkMonitor")
                .onAction(() -> navigation.navigateTo(MainView.class, DaoView.class, MonitorView.class,
                        DaoStateMonitorView.class))
                .show();
        return true;
    }

    private void onUpdateAnyChainHeight() {
        int bsqWalletChainHeight = bsqWalletService.getBestChainHeight();
        int daoStateChainHeight = daoStateService.getChainHeight();
        boolean chainHeightsInSync = bsqWalletChainHeight > 0 && bsqWalletChainHeight == daoStateChainHeight;
        boolean isDaoStateReady = chainHeightsInSync && daoStateService.isParseBlockChainComplete();
        daoStateSyncProgress.set(isDaoStateReady ? 0 : -1);
        daoStateInfo.set(isDaoStateReady ? "" : Res.get("mainView.footer.bsqInfo.synchronizing"));
    }
}
