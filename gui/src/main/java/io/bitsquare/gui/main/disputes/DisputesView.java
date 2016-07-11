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

package io.bitsquare.gui.main.disputes;

import io.bitsquare.app.DevFlags;
import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.arbitration.ArbitratorManager;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.view.*;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.disputes.arbitrator.ArbitratorDisputeView;
import io.bitsquare.gui.main.disputes.trader.TraderDisputeView;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.portfolio.PortfolioView;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesView;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.user.Preferences;
import javafx.beans.value.ChangeListener;
import javafx.collections.MapChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import javax.inject.Inject;

// will be probably only used for arbitration communication, will be renamed and the icon changed
@FxmlView
public class DisputesView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML
    Tab tradersDisputesTab;

    private Tab arbitratorsDisputesTab;

    private final Navigation navigation;
    private final ArbitratorManager arbitratorManager;
    private final KeyRing keyRing;
    private Preferences preferences;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;
    private Tab currentTab;
    private final ViewLoader viewLoader;
    private MapChangeListener<NodeAddress, Arbitrator> arbitratorMapChangeListener;
    private boolean isArbitrator;

    @Inject
    public DisputesView(CachingViewLoader viewLoader, Navigation navigation, ArbitratorManager arbitratorManager,
                        KeyRing keyRing, Preferences preferences) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.arbitratorManager = arbitratorManager;
        this.keyRing = keyRing;


        this.preferences = preferences;
    }

    @Override
    public void initialize() {
        log.debug("initialize ");
        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(DisputesView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == tradersDisputesTab)
                navigation.navigateTo(MainView.class, DisputesView.class, TraderDisputeView.class);
            else if (newValue == arbitratorsDisputesTab)
                navigation.navigateTo(MainView.class, DisputesView.class, ArbitratorDisputeView.class);
        };

        arbitratorMapChangeListener = change -> updateArbitratorsDisputesTabDisableState();
    }

    private void updateArbitratorsDisputesTabDisableState() {
        isArbitrator = arbitratorManager.getArbitratorsObservableMap().values().stream()
                .filter(e -> e.getPubKeyRing() != null && e.getPubKeyRing().equals(keyRing.getPubKeyRing()))
                .findAny().isPresent();

        if (arbitratorsDisputesTab == null && isArbitrator) {
            arbitratorsDisputesTab = new Tab("Arbitrators support tickets");
            arbitratorsDisputesTab.setClosable(false);
            root.getTabs().add(arbitratorsDisputesTab);
            tradersDisputesTab.setText("Traders support tickets");
        }
    }

    @Override
    protected void activate() {
        arbitratorManager.updateArbitratorMap();
        arbitratorManager.getArbitratorsObservableMap().addListener(arbitratorMapChangeListener);
        updateArbitratorsDisputesTabDisableState();

        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (arbitratorsDisputesTab != null && root.getSelectionModel().getSelectedItem() == arbitratorsDisputesTab)
            navigation.navigateTo(MainView.class, DisputesView.class, ArbitratorDisputeView.class);
        else
            navigation.navigateTo(MainView.class, DisputesView.class, TraderDisputeView.class);

        String key = "supportInfo";
        if (!DevFlags.DEV_MODE)
            new Popup().backgroundInfo("Bitsquare is not a company and not operating any kind of customer support.\n\n" +
                    "If there are disputes in the trade process (e.g. one trader does not follow the trade protocol) " +
                    "the application will display a \"Open dispute\" button after the trade period is over " +
                    "for contacting the arbitrator.\n" +
                    "In cases of software bugs or network problems, which are detected by the application there will " +
                    "be displayed a \"Open support ticket\" button to contact the arbitrator who will forward the issue " +
                    "to the developers.\n\n" +
                    "In cases where a user got stuck by a bug without getting displayed that \"Open support ticket\" button, " +
                    "you can open a support ticket manually with a special short cut.\n\n" +
                    "Please use that only if you are sure that the software is not working like expected. " +
                    "If you have problems how to use Bitsquare or any questions please review the FAQ at the " +
                    "Bitsquare.io web page or contact the Bitsquare team using " +
                    "any of the communication channels offered " +
                    "at the Bitsquare.io web page. The Bitsquare forum has a support section as well.\n\n" +
                    "If you are sure you want to open a support ticket please select the trade which causes the problem " +
                    "under \"Portfolio/Open trades\" and type the key combination \"cmd + o\" or \"crtl + o\" to open " +
                    "the support ticket.")
                    .actionButtonText("Go to \"Open trades\"")
                    .onAction(() -> navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class))
                    .dontShowAgainId(key, preferences)
                    .show();
    }

    @Override
    protected void deactivate() {
        arbitratorManager.getArbitratorsObservableMap().removeListener(arbitratorMapChangeListener);
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);
        currentTab = null;
    }

    private void loadView(Class<? extends View> viewClass) {
        // we want to get activate/deactivate called, so we remove the old view on tab change
        if (currentTab != null)
            currentTab.setContent(null);

        View view = viewLoader.load(viewClass);

        if (arbitratorsDisputesTab != null && view instanceof ArbitratorDisputeView)
            currentTab = arbitratorsDisputesTab;
        else
            currentTab = tradersDisputesTab;

        currentTab.setContent(view.getRoot());
        root.getSelectionModel().select(currentTab);
    }
}

