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

package bisq.desktop.main.disputes;

import bisq.desktop.Navigation;
import bisq.desktop.common.model.Activatable;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.main.MainView;
import bisq.desktop.main.disputes.arbitrator.ArbitratorDisputeView;
import bisq.desktop.main.disputes.trader.TraderDisputeView;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.arbitration.Arbitrator;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.arbitration.DisputeManager;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.DevEnv;
import bisq.common.crypto.KeyRing;
import bisq.common.locale.Res;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import javafx.beans.value.ChangeListener;

import javafx.collections.MapChangeListener;

// will be probably only used for arbitration communication, will be renamed and the icon changed
@FxmlView
public class DisputesView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML
    Tab tradersDisputesTab;

    private Tab arbitratorsDisputesTab;

    private final Navigation navigation;
    private final ArbitratorManager arbitratorManager;
    private final DisputeManager disputeManager;
    private final KeyRing keyRing;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;
    private Tab currentTab;
    private final ViewLoader viewLoader;
    private MapChangeListener<NodeAddress, Arbitrator> arbitratorMapChangeListener;

    @Inject
    public DisputesView(CachingViewLoader viewLoader, Navigation navigation,
                        ArbitratorManager arbitratorManager, DisputeManager disputeManager,
                        KeyRing keyRing) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.arbitratorManager = arbitratorManager;
        this.disputeManager = disputeManager;
        this.keyRing = keyRing;
    }

    @Override
    public void initialize() {
        log.debug("initialize ");
        tradersDisputesTab.setText(Res.get("support.tab.support"));
        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(DisputesView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == tradersDisputesTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, DisputesView.class, TraderDisputeView.class);
            else if (newValue == arbitratorsDisputesTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, DisputesView.class, ArbitratorDisputeView.class);
        };

        arbitratorMapChangeListener = change -> updateArbitratorsDisputesTabDisableState();
    }

    private void updateArbitratorsDisputesTabDisableState() {
        boolean isActiveArbitrator = arbitratorManager.getArbitratorsObservableMap().values().stream()
                .filter(e -> e.getPubKeyRing() != null && e.getPubKeyRing().equals(keyRing.getPubKeyRing()))
                .findAny().isPresent();

        boolean hasDisputesAsArbitrator = disputeManager.getDisputesAsObservableList().stream()
                .filter(d -> d.getArbitratorPubKeyRing().equals(keyRing.getPubKeyRing()))
                .findAny().isPresent();

        if (arbitratorsDisputesTab == null && (isActiveArbitrator || hasDisputesAsArbitrator)) {
            arbitratorsDisputesTab = new Tab(Res.get("support.tab.ArbitratorsSupportTickets"));
            arbitratorsDisputesTab.setClosable(false);
            root.getTabs().add(arbitratorsDisputesTab);
            tradersDisputesTab.setText(Res.get("support.tab.TradersSupportTickets"));
        }
    }

    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    protected void activate() {
        arbitratorManager.updateArbitratorMap();
        arbitratorManager.getArbitratorsObservableMap().addListener(arbitratorMapChangeListener);
        updateArbitratorsDisputesTabDisableState();

        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (arbitratorsDisputesTab != null && root.getSelectionModel().getSelectedItem() == arbitratorsDisputesTab)
            //noinspection unchecked
            navigation.navigateTo(MainView.class, DisputesView.class, ArbitratorDisputeView.class);
        else
            //noinspection unchecked
            navigation.navigateTo(MainView.class, DisputesView.class, TraderDisputeView.class);

        //noinspection UnusedAssignment
        String key = "supportInfo";
        if (!DevEnv.isDevMode())
            //noinspection unchecked
            new Popup<>().backgroundInfo(Res.get("support.backgroundInfo"))
                    .width(900)
                    .dontShowAgainId(key)
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

