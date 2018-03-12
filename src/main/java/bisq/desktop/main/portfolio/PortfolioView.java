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

package bisq.desktop.main.portfolio;

import bisq.desktop.Navigation;
import bisq.desktop.common.model.Activatable;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.main.MainView;
import bisq.desktop.main.portfolio.closedtrades.ClosedTradesView;
import bisq.desktop.main.portfolio.failedtrades.FailedTradesView;
import bisq.desktop.main.portfolio.openoffer.OpenOffersView;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesView;

import bisq.core.trade.Trade;
import bisq.core.trade.failed.FailedTradesManager;

import bisq.common.locale.Res;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import javafx.beans.value.ChangeListener;

import javafx.collections.ListChangeListener;

@FxmlView
public class PortfolioView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML
    Tab openOffersTab, pendingTradesTab, closedTradesTab;
    private final Tab failedTradesTab = new Tab(Res.get("portfolio.tab.failed"));
    private Tab currentTab;
    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private final FailedTradesManager failedTradesManager;

    @Inject
    public PortfolioView(CachingViewLoader viewLoader, Navigation navigation, FailedTradesManager failedTradesManager) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.failedTradesManager = failedTradesManager;
    }

    @Override
    public void initialize() {
        openOffersTab.setText(Res.get("portfolio.tab.openOffers"));
        pendingTradesTab.setText(Res.get("portfolio.tab.pendingTrades"));
        closedTradesTab.setText(Res.get("portfolio.tab.history"));

        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(PortfolioView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == openOffersTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, PortfolioView.class, OpenOffersView.class);
            else if (newValue == pendingTradesTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class);
            else if (newValue == closedTradesTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, PortfolioView.class, ClosedTradesView.class);
            else if (newValue == failedTradesTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, PortfolioView.class, FailedTradesView.class);
        };
    }

    @Override
    protected void activate() {
        failedTradesManager.getFailedTrades().addListener((ListChangeListener<Trade>) c -> {
            if (failedTradesManager.getFailedTrades().size() > 0 && root.getTabs().size() == 3)
                root.getTabs().add(failedTradesTab);
        });
        if (failedTradesManager.getFailedTrades().size() > 0 && root.getTabs().size() == 3)
            root.getTabs().add(failedTradesTab);

        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (root.getSelectionModel().getSelectedItem() == openOffersTab)
            //noinspection unchecked
            navigation.navigateTo(MainView.class, PortfolioView.class, OpenOffersView.class);
        else if (root.getSelectionModel().getSelectedItem() == pendingTradesTab)
            //noinspection unchecked
            navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class);
        else if (root.getSelectionModel().getSelectedItem() == closedTradesTab)
            //noinspection unchecked
            navigation.navigateTo(MainView.class, PortfolioView.class, ClosedTradesView.class);
        else if (root.getSelectionModel().getSelectedItem() == failedTradesTab)
            //noinspection unchecked
            navigation.navigateTo(MainView.class, PortfolioView.class, FailedTradesView.class);
    }

    @Override
    protected void deactivate() {
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);
        currentTab = null;
    }

    private void loadView(Class<? extends View> viewClass) {
        // we want to get activate/deactivate called, so we remove the old view on tab change
        if (currentTab != null)
            currentTab.setContent(null);

        View view = viewLoader.load(viewClass);

        if (view instanceof OpenOffersView) currentTab = openOffersTab;
        else if (view instanceof PendingTradesView) currentTab = pendingTradesTab;
        else if (view instanceof ClosedTradesView) currentTab = closedTradesTab;
        else if (view instanceof FailedTradesView) currentTab = failedTradesTab;

        currentTab.setContent(view.getRoot());
        root.getSelectionModel().select(currentTab);
    }
}

