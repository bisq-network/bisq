/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.disputes;

import io.bisq.common.app.DevEnv;
import io.bisq.common.locale.Res;
import io.bisq.core.arbitration.ArbitratorManager;
import io.bisq.core.arbitration.DisputeManager;
import io.bisq.core.user.Preferences;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.model.Activatable;
import io.bisq.gui.common.view.*;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.disputes.arbitrator.ArbitratorDisputeView;
import io.bisq.gui.main.disputes.trader.TraderDisputeView;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.portfolio.PortfolioView;
import io.bisq.gui.main.portfolio.pendingtrades.PendingTradesView;
import io.bisq.protobuffer.crypto.KeyRing;
import io.bisq.protobuffer.payload.arbitration.Arbitrator;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
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
    private final DisputeManager disputeManager;
    private final KeyRing keyRing;
    private final Preferences preferences;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;
    private Tab currentTab;
    private final ViewLoader viewLoader;
    private MapChangeListener<NodeAddress, Arbitrator> arbitratorMapChangeListener;

    @Inject
    public DisputesView(CachingViewLoader viewLoader, Navigation navigation,
                        ArbitratorManager arbitratorManager, DisputeManager disputeManager,
                        KeyRing keyRing, Preferences preferences) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.arbitratorManager = arbitratorManager;
        this.disputeManager = disputeManager;
        this.keyRing = keyRing;
        this.preferences = preferences;
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
                navigation.navigateTo(MainView.class, DisputesView.class, TraderDisputeView.class);
            else if (newValue == arbitratorsDisputesTab)
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
        if (!DevEnv.DEV_MODE)
            new Popup().backgroundInfo(Res.get("support.backgroundInfo"))
                    .width(900)
                    .actionButtonTextWithGoTo("navigation.portfolio.pending")
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

