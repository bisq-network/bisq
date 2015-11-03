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

import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.arbitration.ArbitratorManager;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.view.*;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.disputes.arbitrator.ArbitratorDisputeView;
import io.bitsquare.gui.main.disputes.trader.TraderDisputeView;
import io.bitsquare.p2p.Address;
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
    Tab tradersDisputesTab, arbitratorsDisputesTab;

    private final Navigation navigation;
    private final ArbitratorManager arbitratorManager;
    private final KeyRing keyRing;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;
    private Tab currentTab;
    private final ViewLoader viewLoader;
    private MapChangeListener<Address, Arbitrator> arbitratorMapChangeListener;

    @Inject
    public DisputesView(CachingViewLoader viewLoader, Navigation navigation, ArbitratorManager arbitratorManager, KeyRing keyRing) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.arbitratorManager = arbitratorManager;
        this.keyRing = keyRing;
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
        boolean isArbitrator = arbitratorManager.getArbitratorsObservableMap().values().stream()
                .filter(e -> e.getPubKeyRing() != null && e.getPubKeyRing().equals(keyRing.getPubKeyRing()))
                .findAny().isPresent();
        arbitratorsDisputesTab.setDisable(!isArbitrator);
        if (arbitratorsDisputesTab.getContent() != null)
            arbitratorsDisputesTab.getContent().setDisable(!isArbitrator);
    }

    @Override
    protected void activate() {
        arbitratorManager.applyArbitrators();
        arbitratorManager.getArbitratorsObservableMap().addListener(arbitratorMapChangeListener);
        updateArbitratorsDisputesTabDisableState();

        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (root.getSelectionModel().getSelectedItem() == tradersDisputesTab)
            navigation.navigateTo(MainView.class, DisputesView.class, TraderDisputeView.class);
        else if (root.getSelectionModel().getSelectedItem() == arbitratorsDisputesTab)
            navigation.navigateTo(MainView.class, DisputesView.class, ArbitratorDisputeView.class);
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

        if (view instanceof ArbitratorDisputeView)
            currentTab = arbitratorsDisputesTab;
        else if (view instanceof TraderDisputeView)
            currentTab = tradersDisputesTab;

        currentTab.setContent(view.getRoot());
        root.getSelectionModel().select(currentTab);
    }
}

