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
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.main.MainView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.support.dispute.agent.arbitration.ArbitratorView;
import bisq.desktop.main.support.dispute.agent.mediation.MediatorView;
import bisq.desktop.main.support.dispute.agent.refund.RefundAgentView;
import bisq.desktop.main.support.dispute.client.arbitration.ArbitrationClientView;
import bisq.desktop.main.support.dispute.client.mediation.MediationClientView;
import bisq.desktop.main.support.dispute.client.refund.RefundClientView;

import bisq.core.locale.Res;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.arbitration.arbitrator.Arbitrator;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.support.dispute.refund.refundagent.RefundAgent;
import bisq.core.support.dispute.refund.refundagent.RefundAgentManager;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.DevEnv;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;

import javax.inject.Inject;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import javafx.beans.value.ChangeListener;

import javafx.collections.MapChangeListener;

import javax.annotation.Nullable;

@FxmlView
public class SupportView extends ActivatableView<TabPane, Void> {

    private Tab tradersMediationDisputesTab, tradersRefundDisputesTab;
    @Nullable
    private Tab tradersArbitrationDisputesTab;
    private Tab mediatorTab, refundAgentTab;
    @Nullable
    private Tab arbitratorTab;

    private final Navigation navigation;
    private final ArbitratorManager arbitratorManager;
    private final MediatorManager mediatorManager;
    private final RefundAgentManager refundAgentManager;
    private final ArbitrationManager arbitrationManager;
    private final MediationManager mediationManager;
    private final RefundManager refundManager;
    private final KeyRing keyRing;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;
    private Tab currentTab;
    private final ViewLoader viewLoader;
    private MapChangeListener<NodeAddress, Arbitrator> arbitratorMapChangeListener;
    private MapChangeListener<NodeAddress, Mediator> mediatorMapChangeListener;
    private MapChangeListener<NodeAddress, RefundAgent> refundAgentMapChangeListener;

    @Inject
    public SupportView(CachingViewLoader viewLoader,
                       Navigation navigation,
                       ArbitratorManager arbitratorManager,
                       MediatorManager mediatorManager,
                       RefundAgentManager refundAgentManager,
                       ArbitrationManager arbitrationManager,
                       MediationManager mediationManager,
                       RefundManager refundManager,
                       KeyRing keyRing) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.arbitratorManager = arbitratorManager;
        this.mediatorManager = mediatorManager;
        this.refundAgentManager = refundAgentManager;
        this.arbitrationManager = arbitrationManager;
        this.mediationManager = mediationManager;
        this.refundManager = refundManager;
        this.keyRing = keyRing;
    }

    @Override
    public void initialize() {
        tradersMediationDisputesTab = new Tab();
        tradersMediationDisputesTab.setClosable(false);
        root.getTabs().add(tradersMediationDisputesTab);

        tradersRefundDisputesTab = new Tab();
        tradersRefundDisputesTab.setClosable(false);
        root.getTabs().add(tradersRefundDisputesTab);

        // We only show tradersArbitrationDisputesTab if we have cases
        if (!arbitrationManager.getDisputesAsObservableList().isEmpty()) {
            tradersArbitrationDisputesTab = new Tab();
            tradersArbitrationDisputesTab.setClosable(false);
            root.getTabs().add(tradersArbitrationDisputesTab);
        }

        // Has to be called before loadView
        updateAgentTabs();

        tradersMediationDisputesTab.setText(Res.get("support.tab.mediation.support").toUpperCase());
        tradersRefundDisputesTab.setText(Res.get("support.tab.arbitration.support").toUpperCase());
        if (tradersArbitrationDisputesTab != null) {
            tradersArbitrationDisputesTab.setText(Res.get("support.tab.legacyArbitration.support").toUpperCase());
        }
        navigationListener = (viewPath, data) -> {
            if (viewPath.size() == 3 && viewPath.indexOf(SupportView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == tradersArbitrationDisputesTab)
                navigation.navigateTo(MainView.class, SupportView.class, ArbitrationClientView.class);
            else if (newValue == tradersMediationDisputesTab)
                navigation.navigateTo(MainView.class, SupportView.class, MediationClientView.class);
            else if (newValue == tradersRefundDisputesTab)
                navigation.navigateTo(MainView.class, SupportView.class, RefundClientView.class);
            else if (newValue == arbitratorTab)
                navigation.navigateTo(MainView.class, SupportView.class, ArbitratorView.class);
            else if (newValue == mediatorTab)
                navigation.navigateTo(MainView.class, SupportView.class, MediatorView.class);
            else if (newValue == refundAgentTab)
                navigation.navigateTo(MainView.class, SupportView.class, RefundAgentView.class);
        };

        arbitratorMapChangeListener = change -> updateAgentTabs();
        mediatorMapChangeListener = change -> updateAgentTabs();
        refundAgentMapChangeListener = change -> updateAgentTabs();
    }

    private void updateAgentTabs() {
        PubKeyRing myPubKeyRing = keyRing.getPubKeyRing();

        boolean hasArbitrationCases = !arbitrationManager.getDisputesAsObservableList().isEmpty();
        if (hasArbitrationCases) {
            boolean isActiveArbitrator = arbitratorManager.getObservableMap().values().stream()
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
        }

        boolean isActiveMediator = mediatorManager.getObservableMap().values().stream()
                .anyMatch(e -> e.getPubKeyRing() != null && e.getPubKeyRing().equals(myPubKeyRing));
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

        boolean isActiveRefundAgent = refundAgentManager.getObservableMap().values().stream()
                .anyMatch(e -> e.getPubKeyRing() != null && e.getPubKeyRing().equals(myPubKeyRing));
        if (refundAgentTab == null) {
            // In case a refundAgent has become inactive he still might get disputes from pending trades
            boolean hasDisputesAsRefundAgent = refundManager.getDisputesAsObservableList().stream()
                    .anyMatch(d -> d.getAgentPubKeyRing().equals(myPubKeyRing));
            if (isActiveRefundAgent || hasDisputesAsRefundAgent) {
                refundAgentTab = new Tab();
                refundAgentTab.setClosable(false);
                root.getTabs().add(refundAgentTab);
            }
        }

        // We might get that method called before we have the map is filled in the arbitratorManager
        if (arbitratorTab != null) {
            arbitratorTab.setText(Res.get("support.tab.ArbitratorsSupportTickets", Res.get("shared.arbitrator")).toUpperCase());
        }
        if (mediatorTab != null) {
            mediatorTab.setText(Res.get("support.tab.ArbitratorsSupportTickets", Res.get("shared.mediator")).toUpperCase());
        }
        if (refundAgentTab != null) {
            refundAgentTab.setText(Res.get("support.tab.ArbitratorsSupportTickets", Res.get("shared.refundAgentForSupportStaff")).toUpperCase());
        }
    }

    @Override
    protected void activate() {
        arbitratorManager.updateMap();
        arbitratorManager.getObservableMap().addListener(arbitratorMapChangeListener);

        mediatorManager.updateMap();
        mediatorManager.getObservableMap().addListener(mediatorMapChangeListener);

        refundAgentManager.updateMap();
        refundAgentManager.getObservableMap().addListener(refundAgentMapChangeListener);

        updateAgentTabs();

        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (root.getSelectionModel().getSelectedItem() == tradersMediationDisputesTab) {
            navigation.navigateTo(MainView.class, SupportView.class, MediationClientView.class);
        } else if (root.getSelectionModel().getSelectedItem() == tradersArbitrationDisputesTab) {
            navigation.navigateTo(MainView.class, SupportView.class, ArbitrationClientView.class);
        } else if (root.getSelectionModel().getSelectedItem() == tradersRefundDisputesTab) {
            navigation.navigateTo(MainView.class, SupportView.class, RefundClientView.class);
        } else if (arbitratorTab != null) {
            navigation.navigateTo(MainView.class, SupportView.class, ArbitratorView.class);
        } else if (mediatorTab != null) {
            navigation.navigateTo(MainView.class, SupportView.class, MediatorView.class);
        } else if (refundAgentTab != null) {
            navigation.navigateTo(MainView.class, SupportView.class, RefundAgentView.class);
        }

        String key = "supportInfo";
        if (!DevEnv.isDevMode()) {
            new Popup().backgroundInfo(Res.get("support.backgroundInfo"))
                    .width(900)
                    .dontShowAgainId(key)
                    .show();
        }
    }

    @Override
    protected void deactivate() {
        arbitratorManager.getObservableMap().removeListener(arbitratorMapChangeListener);
        mediatorManager.getObservableMap().removeListener(mediatorMapChangeListener);
        refundAgentManager.getObservableMap().removeListener(refundAgentMapChangeListener);
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
        } else if (view instanceof RefundClientView) {
            currentTab = tradersRefundDisputesTab;
        } else if (view instanceof ArbitratorView) {
            currentTab = arbitratorTab;
        } else if (view instanceof MediatorView) {
            currentTab = mediatorTab;
        } else if (view instanceof RefundAgentView) {
            currentTab = refundAgentTab;
        } else {
            currentTab = null;
        }

        if (currentTab != null) {
            currentTab.setContent(view.getRoot());
            root.getSelectionModel().select(currentTab);
        }
    }
}
