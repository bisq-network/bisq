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

package io.bitsquare.gui.main.dao;

import io.bitsquare.btc.wallet.SquWalletService;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.view.*;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.dao.compensation.CompensationView;
import io.bitsquare.gui.main.dao.voting.VotingView;
import io.bitsquare.gui.main.dao.wallet.TokenWalletView;
import io.bitsquare.gui.main.dao.wallet.dashboard.TokenDashboardView;
import io.bitsquare.user.Preferences;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import javax.inject.Inject;

@FxmlView
public class DaoView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML
    Tab tokenWalletTab, CompensationTab, votingTab;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private Preferences preferences;
    private SquWalletService squWalletService;
    private Tab selectedTab;
    private TokenWalletView tokenWalletView;


    @Inject
    private DaoView(CachingViewLoader viewLoader, Navigation navigation, Preferences preferences, SquWalletService squWalletService) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.preferences = preferences;
        this.squWalletService = squWalletService;
    }

    @Override
    public void initialize() {
        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(DaoView.class) == 1) {
                if (CompensationTab == null && viewPath.get(2).equals(CompensationView.class))
                    navigation.navigateTo(MainView.class, DaoView.class, TokenWalletView.class, TokenDashboardView.class);
                else
                    loadView(viewPath.tip());
            }
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == tokenWalletTab) {
                Class<? extends View> selectedViewClass = tokenWalletView.getSelectedViewClass();
                if (selectedViewClass == null)
                    navigation.navigateTo(MainView.class, DaoView.class, TokenWalletView.class, TokenDashboardView.class);
                else
                    navigation.navigateTo(MainView.class, DaoView.class, TokenWalletView.class, selectedViewClass);
            } else if (newValue == CompensationTab) {
                navigation.navigateTo(MainView.class, DaoView.class, CompensationView.class);
            } else if (newValue == votingTab) {
                navigation.navigateTo(MainView.class, DaoView.class, VotingView.class);
            }
        };
    }

    @Override
    protected void activate() {
        navigation.addListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);

        if (navigation.getCurrentPath().size() == 2 && navigation.getCurrentPath().get(1) == DaoView.class) {
            Tab selectedItem = root.getSelectionModel().getSelectedItem();
            if (selectedItem == tokenWalletTab)
                navigation.navigateTo(MainView.class, DaoView.class, TokenWalletView.class);
            else if (selectedItem == CompensationTab)
                navigation.navigateTo(MainView.class, DaoView.class, CompensationView.class);
            else if (selectedItem == votingTab)
                navigation.navigateTo(MainView.class, DaoView.class, VotingView.class);
        }
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
    }

    private void loadView(Class<? extends View> viewClass) {
        View view = viewLoader.load(viewClass);
        if (view instanceof TokenWalletView) {
            selectedTab = tokenWalletTab;
            tokenWalletView = (TokenWalletView) view;
        } else if (view instanceof CompensationView) {
            selectedTab = CompensationTab;
        } else if (view instanceof VotingView) {
            selectedTab = votingTab;
        }

        selectedTab.setContent(view.getRoot());
        root.getSelectionModel().select(selectedTab);
    }
}

