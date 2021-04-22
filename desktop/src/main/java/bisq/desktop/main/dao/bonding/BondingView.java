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

package bisq.desktop.main.dao.bonding;

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
import bisq.desktop.main.dao.bonding.bonds.BondsView;
import bisq.desktop.main.dao.bonding.dashboard.BondingDashboardView;
import bisq.desktop.main.dao.bonding.reputation.MyReputationView;
import bisq.desktop.main.dao.bonding.roles.RolesView;

import bisq.core.dao.governance.bond.Bond;
import bisq.core.locale.Res;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

@FxmlView
public class BondingView extends ActivatableView<AnchorPane, Void> {

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    private MenuItem dashboard, bondedRoles, reputation, bonds;
    private Navigation.Listener listener;

    @FXML
    private VBox leftVBox;
    @FXML
    private AnchorPane content;

    private Class<? extends View> selectedViewClass;
    private ToggleGroup toggleGroup;

    @Inject
    private BondingView(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        listener = (viewPath, data) -> {
            if (viewPath.size() != 4 || viewPath.indexOf(bisq.desktop.main.dao.bonding.BondingView.class) != 2)
                return;

            selectedViewClass = viewPath.tip();
            loadView(selectedViewClass, data);
        };

        toggleGroup = new ToggleGroup();
        final List<Class<? extends View>> baseNavPath = Arrays.asList(MainView.class, DaoView.class, bisq.desktop.main.dao.bonding.BondingView.class);
        dashboard = new MenuItem(navigation, toggleGroup, Res.get("shared.dashboard"),
                BondingDashboardView.class, baseNavPath);
        bondedRoles = new MenuItem(navigation, toggleGroup, Res.get("dao.bond.menuItem.bondedRoles"),
                RolesView.class, baseNavPath);
        reputation = new MenuItem(navigation, toggleGroup, Res.get("dao.bond.menuItem.reputation"),
                MyReputationView.class, baseNavPath);
        bonds = new MenuItem(navigation, toggleGroup, Res.get("dao.bond.menuItem.bonds"),
                BondsView.class, baseNavPath);

        leftVBox.getChildren().addAll(dashboard, bondedRoles, reputation, bonds);
    }

    @Override
    protected void activate() {
        dashboard.activate();
        bondedRoles.activate();
        reputation.activate();
        bonds.activate();

        navigation.addListener(listener);
        ViewPath viewPath = navigation.getCurrentPath();
        if (viewPath.size() == 3 && viewPath.indexOf(BondingView.class) == 2 ||
                viewPath.size() == 2 && viewPath.indexOf(DaoView.class) == 1) {
            if (selectedViewClass == null)
                selectedViewClass = RolesView.class;

            loadView(selectedViewClass, null);

        } else if (viewPath.size() == 4 && viewPath.indexOf(BondingView.class) == 2) {
            selectedViewClass = viewPath.get(3);
            loadView(selectedViewClass, null);
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected void deactivate() {
        navigation.removeListener(listener);

        dashboard.deactivate();
        bondedRoles.deactivate();
        reputation.deactivate();
        bonds.deactivate();
    }

    private void loadView(Class<? extends View> viewClass, @Nullable Object data) {
        View view = viewLoader.load(viewClass);
        content.getChildren().setAll(view.getRoot());

        if (view instanceof BondingDashboardView) toggleGroup.selectToggle(dashboard);
        else if (view instanceof RolesView) toggleGroup.selectToggle(bondedRoles);
        else if (view instanceof MyReputationView) toggleGroup.selectToggle(reputation);
        else if (view instanceof BondsView) {
            toggleGroup.selectToggle(bonds);
            if (data instanceof Bond)
                ((BondsView) view).setSelectedBond((Bond) data);
        }

    }
}
