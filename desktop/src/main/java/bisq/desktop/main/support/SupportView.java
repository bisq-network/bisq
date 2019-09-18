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

package bisq.desktop.main.support;

import bisq.desktop.Navigation;
import bisq.desktop.common.model.Activatable;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.main.MainView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.support.dispute.agent.arbitration.ArbitratorView;
import bisq.desktop.main.support.dispute.agent.mediation.MediatorView;
import bisq.desktop.main.support.dispute.client.arbitration.ArbitrationClientView;
import bisq.desktop.main.support.dispute.client.mediation.MediationClientView;

import bisq.core.locale.Res;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.arbitration.arbitrator.Arbitrator;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.DevEnv;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import javafx.beans.value.ChangeListener;

import javafx.collections.MapChangeListener;

@FxmlView
public class SupportView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML
    Tab tradersArbitrationDisputesTab, tradersMediationDisputesTab;

    private Tab arbitratorTab, mediatorTab;

    private final Navigation navigation;
    private final ArbitratorManager arbitratorManager;
    private final MediatorManager mediatorManager;
    private final ArbitrationManager arbitrationManager;
    private final MediationManager mediationManager;
    private final KeyRing keyRing;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;
    private Tab currentTab;
    private final ViewLoader viewLoader;
    private MapChangeListener<NodeAddress, Arbitrator> arbitratorMapChangeListener;
    private MapChangeListener<NodeAddress, Mediator> mediatorMapChangeListener;

    @Inject
    public SupportView(CachingViewLoader viewLoader,
                       Navigation navigation,
                       ArbitratorManager arbitratorManager,
                       MediatorManager mediatorManager,
                       ArbitrationManager arbitrationManager,
                       MediationManager mediationManager,
                       KeyRing keyRing) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.arbitratorManager = arbitratorManager;
        this.mediatorManager = mediatorManager;
        this.arbitrationManager = arbitrationManager;
        this.mediationManager = mediationManager;
        this.keyRing = keyRing;
    }

    @Override
    public void initialize() {
        // has to be called before loadView
        updateAgentTabs();

        tradersArbitrationDisputesTab.setText(Res.get("support.tab.arbitration.support").toUpperCase());
        tradersMediationDisputesTab.setText(Res.get("support.tab.mediation.support").toUpperCase());
        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(SupportView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == tradersArbitrationDisputesTab)
                navigation.navigateTo(MainView.class, SupportView.class, ArbitrationClientView.class);
            else if (newValue == tradersMediationDisputesTab)
                navigation.navigateTo(MainView.class, SupportView.class, MediationClientView.class);
            else if (newValue == arbitratorTab)
                navigation.navigateTo(MainView.class, SupportView.class, ArbitratorView.class);
            else if (newValue == mediatorTab)
                navigation.navigateTo(MainView.class, SupportView.class, MediatorView.class);
        };

        arbitratorMapChangeListener = change -> updateAgentTabs();
        mediatorMapChangeListener = change -> updateAgentTabs();

    }

    private void updateAgentTabs() {
        PubKeyRing myPubKeyRing = keyRing.getPubKeyRing();
        boolean isActiveArbitrator = arbitratorManager.getObservableMap().values().stream()
                .anyMatch(e -> e.getPubKeyRing() != null && e.getPubKeyRing().equals(myPubKeyRing));
        boolean isActiveMediator = mediatorManager.getObservableMap().values().stream()
                .anyMatch(e -> e.getPubKeyRing() != null && e.getPubKeyRing().equals(myPubKeyRing));

        if (arbitratorTab == null) {
            // In case a arbitrator has become inactive he still might get disputes from pending trades
            boolean hasDisputesAsArbitrator = arbitrationManager.getDisputesAsObservableList().stream()
                    .anyMatch(d -> d.getAgentPubKeyRing().equals(myPubKeyRing));
            if (isActiveArbitrator || hasDisputesAsArbitrator) {
                arbitratorTab = new Tab();
                arbitratorTab.setClosable(false);
                root.getTabs().add(arbitratorTab);
            }
        }
        if (mediatorTab == null) {
            // In case a mediator has become inactive he still might get disputes from pending trades
            boolean hasDisputesAsMediator = mediationManager.getDisputesAsObservableList().stream()
                    .anyMatch(d -> d.getAgentPubKeyRing().equals(myPubKeyRing));
            if (isActiveMediator || hasDisputesAsMediator) {
                mediatorTab = new Tab();
                mediatorTab.setClosable(false);
                root.getTabs().add(mediatorTab);
            }
        }

        // We might get that method called before we have the map is filled in the arbitratorManager
        if (arbitratorTab != null) {
            arbitratorTab.setText(Res.get("support.tab.ArbitratorsSupportTickets", Res.get("shared.arbitrator2")).toUpperCase());
        }
        if (mediatorTab != null) {
            mediatorTab.setText(Res.get("support.tab.ArbitratorsSupportTickets", Res.get("shared.mediator")).toUpperCase());
        }
    }

    @Override
    protected void activate() {
        arbitratorManager.updateMap();
        arbitratorManager.getObservableMap().addListener(arbitratorMapChangeListener);

        mediatorManager.updateMap();
        mediatorManager.getObservableMap().addListener(mediatorMapChangeListener);

        updateAgentTabs();

        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (root.getSelectionModel().getSelectedItem() == tradersMediationDisputesTab) {
            navigation.navigateTo(MainView.class, SupportView.class, MediationClientView.class);
        } else if (root.getSelectionModel().getSelectedItem() == tradersArbitrationDisputesTab) {
            navigation.navigateTo(MainView.class, SupportView.class, ArbitrationClientView.class);
        } else if (arbitratorTab != null) {
            navigation.navigateTo(MainView.class, SupportView.class, ArbitratorView.class);
        } else if (mediatorTab != null) {
            navigation.navigateTo(MainView.class, SupportView.class, MediatorView.class);
        }

        String key = "supportInfo";
        if (!DevEnv.isDevMode())
            new Popup<>().backgroundInfo(Res.get("support.backgroundInfo"))
                    .width(900)
                    .dontShowAgainId(key)
                    .show();
    }

    @Override
    protected void deactivate() {
        arbitratorManager.getObservableMap().removeListener(arbitratorMapChangeListener);
        mediatorManager.getObservableMap().removeListener(mediatorMapChangeListener);
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);
        currentTab = null;
    }

    private void loadView(Class<? extends View> viewClass) {
        // we want to get activate/deactivate called, so we remove the old view on tab change
        if (currentTab != null)
            currentTab.setContent(null);

        View view = viewLoader.load(viewClass);

        if (view instanceof MediationClientView) {
            currentTab = tradersMediationDisputesTab;
        } else if (view instanceof ArbitrationClientView) {
            currentTab = tradersArbitrationDisputesTab;
        } else if (view instanceof ArbitratorView) {
            currentTab = arbitratorTab;
        } else if (view instanceof MediatorView) {
            currentTab = mediatorTab;
        } else {
            currentTab = null;
        }

        if (currentTab != null) {
            currentTab.setContent(view.getRoot());
            root.getSelectionModel().select(currentTab);
        }
    }
}
