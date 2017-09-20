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

package io.bisq.gui.main.dao;

import io.bisq.common.app.DevEnv;
import io.bisq.common.locale.Res;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.model.Activatable;
import io.bisq.gui.common.view.*;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.dao.compensation.CompensationView;
import io.bisq.gui.main.dao.voting.VotingView;
import io.bisq.gui.main.dao.wallet.BsqWalletView;
import io.bisq.gui.main.dao.wallet.dashboard.BsqDashboardView;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import javax.inject.Inject;

@FxmlView
public class DaoView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML
    Tab bsqWalletTab, compensationTab, votingTab;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private Tab selectedTab;
    private BsqWalletView bsqWalletView;


    @Inject
    private DaoView(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        compensationTab = new Tab(Res.get("dao.tab.compensation"));
        votingTab = new Tab(Res.get("dao.tab.voting"));
        compensationTab.setClosable(false);
        votingTab.setClosable(false);
        root.getTabs().addAll(compensationTab, votingTab);

        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq() && !DevEnv.DAO_PHASE2_ACTIVATED) {
            votingTab.setDisable(true);
            compensationTab.setDisable(true);
        }

        bsqWalletTab.setText(Res.get("dao.tab.bsqWallet"));

        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(DaoView.class) == 1) {
                if (compensationTab == null && viewPath.get(2).equals(BsqWalletView.class))
                    //noinspection unchecked
                    navigation.navigateTo(MainView.class, DaoView.class, BsqWalletView.class);
                else
                    loadView(viewPath.tip());
            }
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == bsqWalletTab) {
                Class<? extends View> selectedViewClass = bsqWalletView.getSelectedViewClass();
                if (selectedViewClass == null)
                    //noinspection unchecked
                    navigation.navigateTo(MainView.class, DaoView.class, BsqWalletView.class, BsqDashboardView.class);
                else
                    //noinspection unchecked
                    navigation.navigateTo(MainView.class, DaoView.class, BsqWalletView.class, selectedViewClass);
            } else if (newValue == compensationTab) {
                //noinspection unchecked
                navigation.navigateTo(MainView.class, DaoView.class, CompensationView.class);
            } else if (newValue == votingTab) {
                //noinspection unchecked
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
            if (selectedItem == bsqWalletTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, DaoView.class, BsqWalletView.class);
            else if (selectedItem == compensationTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, DaoView.class, CompensationView.class);
            else if (selectedItem == votingTab)
                //noinspection unchecked
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
        if (view instanceof BsqWalletView) {
            selectedTab = bsqWalletTab;
            bsqWalletView = (BsqWalletView) view;
        } else if (view instanceof CompensationView) {
            selectedTab = compensationTab;
        } else if (view instanceof VotingView) {
            selectedTab = votingTab;
        }

        selectedTab.setContent(view.getRoot());
        root.getSelectionModel().select(selectedTab);
    }
}

