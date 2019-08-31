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
import bisq.desktop.main.disputes.disputeresolvers.ArbitratorDisputeView;
import bisq.desktop.main.disputes.trader.TraderDisputeView;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.dispute.arbitration.Arbitrator;
import bisq.core.dispute.arbitration.ArbitratorManager;
import bisq.core.dispute.arbitration.DisputeManager;
import bisq.core.dispute.mediator.Mediator;
import bisq.core.dispute.mediator.MediatorManager;
import bisq.core.locale.Res;

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

import static com.google.common.base.Preconditions.checkArgument;

// will be probably only used for arbitration communication, will be renamed and the icon changed
@FxmlView
public class DisputesView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML
    Tab tradersDisputesTab;

    private Tab disputeResolversDisputesTab;

    private final Navigation navigation;
    private final ArbitratorManager arbitratorManager;
    private final MediatorManager mediatorManager;
    private final DisputeManager disputeManager;
    private final KeyRing keyRing;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;
    private Tab currentTab;
    private final ViewLoader viewLoader;
    private MapChangeListener<NodeAddress, Arbitrator> arbitratorMapChangeListener;
    private MapChangeListener<NodeAddress, Mediator> mediatorMapChangeListener;

    @Inject
    public DisputesView(CachingViewLoader viewLoader,
                        Navigation navigation,
                        ArbitratorManager arbitratorManager,
                        MediatorManager mediatorManager,
                        DisputeManager disputeManager,
                        KeyRing keyRing) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.arbitratorManager = arbitratorManager;
        this.mediatorManager = mediatorManager;
        this.disputeManager = disputeManager;
        this.keyRing = keyRing;
    }

    @Override
    public void initialize() {
        log.debug("initialize ");
        tradersDisputesTab.setText(Res.get("support.tab.support").toUpperCase());
        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(DisputesView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == tradersDisputesTab)
                navigation.navigateTo(MainView.class, DisputesView.class, TraderDisputeView.class);
            else if (newValue == disputeResolversDisputesTab)
                navigation.navigateTo(MainView.class, DisputesView.class, ArbitratorDisputeView.class);
        };

        arbitratorMapChangeListener = change -> updateConflictResolversDisputesTabDisableState();
        mediatorMapChangeListener = change -> updateConflictResolversDisputesTabDisableState();
    }

    private void updateConflictResolversDisputesTabDisableState() {
        PubKeyRing myPubKeyRing = keyRing.getPubKeyRing();
        boolean isActiveArbitrator = arbitratorManager.getObservableMap().values().stream()
                .anyMatch(e -> e.getPubKeyRing() != null && e.getPubKeyRing().equals(myPubKeyRing));
        boolean isActiveMediator = mediatorManager.getObservableMap().values().stream()
                .anyMatch(e -> e.getPubKeyRing() != null && e.getPubKeyRing().equals(myPubKeyRing));

        if (isActiveArbitrator)
            checkArgument(!isActiveMediator, "We do not support that arbitrators are mediators as well");

        boolean hasDisputesAsArbitrator = disputeManager.getDisputesAsObservableList().stream()
                .anyMatch(d -> d.getConflictResolverPubKeyRing().equals(myPubKeyRing));

        if (disputeResolversDisputesTab == null && (isActiveArbitrator || isActiveMediator || hasDisputesAsArbitrator)) {
            String role = isActiveArbitrator ? Res.get("shared.arbitrator2") : Res.get("shared.mediator");
            disputeResolversDisputesTab = new Tab(Res.get("support.tab.ArbitratorsSupportTickets", role).toUpperCase());
            disputeResolversDisputesTab.setClosable(false);
            root.getTabs().add(disputeResolversDisputesTab);
            tradersDisputesTab.setText(Res.get("support.tab.TradersSupportTickets").toUpperCase());
        }
    }

    @Override
    protected void activate() {
        arbitratorManager.updateMap();
        arbitratorManager.getObservableMap().addListener(arbitratorMapChangeListener);

        mediatorManager.updateMap();
        mediatorManager.getObservableMap().addListener(mediatorMapChangeListener);

        updateConflictResolversDisputesTabDisableState();

        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (disputeResolversDisputesTab != null && root.getSelectionModel().getSelectedItem() == disputeResolversDisputesTab)
            navigation.navigateTo(MainView.class, DisputesView.class, ArbitratorDisputeView.class);
        else
            navigation.navigateTo(MainView.class, DisputesView.class, TraderDisputeView.class);

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

        if (disputeResolversDisputesTab != null && view instanceof ArbitratorDisputeView)
            currentTab = disputeResolversDisputesTab;
        else
            currentTab = tradersDisputesTab;

        currentTab.setContent(view.getRoot());
        root.getSelectionModel().select(currentTab);
    }
}

