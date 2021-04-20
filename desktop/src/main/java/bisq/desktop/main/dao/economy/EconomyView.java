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
 * License for more supply.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.dao.economy;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.common.view.ViewPath;
import bisq.desktop.components.MenuItem;
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.dao.economy.dashboard.BsqDashboardView;
import bisq.desktop.main.dao.economy.supply.SupplyView;
import bisq.desktop.main.dao.economy.transactions.BSQTransactionsView;

import bisq.core.locale.Res;

import bisq.common.app.DevEnv;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;

import java.util.Arrays;
import java.util.List;

@FxmlView
public class EconomyView extends ActivatableView<AnchorPane, Void> {

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    private MenuItem dashboard, supply, transactions;
    private Navigation.Listener listener;

    @FXML
    private VBox leftVBox;
    @FXML
    private AnchorPane content;

    private Class<? extends View> selectedViewClass;
    private ToggleGroup toggleGroup;

    @Inject
    private EconomyView(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        listener = (viewPath, data) -> {
            if (viewPath.size() != 4 || viewPath.indexOf(EconomyView.class) != 2)
                return;

            selectedViewClass = viewPath.tip();
            loadView(selectedViewClass);
        };

        toggleGroup = new ToggleGroup();
        List<Class<? extends View>> baseNavPath = Arrays.asList(MainView.class, DaoView.class, EconomyView.class);
        dashboard = new MenuItem(navigation, toggleGroup, Res.get("shared.dashboard"), BsqDashboardView.class, baseNavPath);
        supply = new MenuItem(navigation, toggleGroup, Res.get("dao.factsAndFigures.menuItem.supply"), SupplyView.class, baseNavPath);
        transactions = new MenuItem(navigation, toggleGroup, Res.get("dao.factsAndFigures.menuItem.transactions"), BSQTransactionsView.class, baseNavPath);

        leftVBox.getChildren().addAll(dashboard, supply, transactions);

        if (!DevEnv.isDaoActivated()) {
            dashboard.setDisable(true);
            supply.setDisable(true);
            transactions.setDisable(true);
        }
    }

    @Override
    protected void activate() {
        dashboard.activate();
        supply.activate();
        transactions.activate();

        navigation.addListener(listener);
        ViewPath viewPath = navigation.getCurrentPath();
        if (viewPath.size() == 3 && viewPath.indexOf(EconomyView.class) == 2 ||
                viewPath.size() == 2 && viewPath.indexOf(DaoView.class) == 1) {
            if (selectedViewClass == null)
                selectedViewClass = BsqDashboardView.class;

            loadView(selectedViewClass);
        } else if (viewPath.size() == 4 && viewPath.indexOf(EconomyView.class) == 2) {
            selectedViewClass = viewPath.get(3);
            loadView(selectedViewClass);
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected void deactivate() {
        navigation.removeListener(listener);

        dashboard.deactivate();
        supply.deactivate();
        transactions.deactivate();
    }

    private void loadView(Class<? extends View> viewClass) {
        View view = viewLoader.load(viewClass);
        content.getChildren().setAll(view.getRoot());

        if (view instanceof BsqDashboardView) toggleGroup.selectToggle(dashboard);
        else if (view instanceof SupplyView) toggleGroup.selectToggle(supply);
        else if (view instanceof BSQTransactionsView) toggleGroup.selectToggle(transactions);
    }
}
