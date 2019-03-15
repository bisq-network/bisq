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

package bisq.desktop.main.dao;

import bisq.desktop.Navigation;
import bisq.desktop.common.model.Activatable;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.bonding.BondingView;
import bisq.desktop.main.dao.burnbsq.BurnBsqView;
import bisq.desktop.main.dao.governance.GovernanceView;
import bisq.desktop.main.dao.monitor.MonitorView;
import bisq.desktop.main.dao.news.NewsView;
import bisq.desktop.main.dao.wallet.BsqWalletView;
import bisq.desktop.main.dao.wallet.dashboard.BsqDashboardView;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.dao.governance.votereveal.VoteRevealService;
import bisq.core.locale.Res;

import bisq.common.app.DevEnv;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import javafx.beans.value.ChangeListener;

@FxmlView
public class DaoView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML
    private Tab bsqWalletTab, proposalsTab, bondingTab, burnBsqTab, daoNewsTab, monitor;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private Tab selectedTab;
    private BsqWalletView bsqWalletView;

    @Inject
    private DaoView(CachingViewLoader viewLoader, VoteRevealService voteRevealService, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;

        voteRevealService.addVoteRevealTxPublishedListener(txId -> {
            new Popup<>().headLine(Res.get("dao.voteReveal.txPublished.headLine"))
                    .feedback(Res.get("dao.voteReveal.txPublished", txId))
                    .show();
        });
    }

    @Override
    public void initialize() {
        bsqWalletTab = new Tab(Res.get("dao.tab.bsqWallet").toUpperCase());
        proposalsTab = new Tab(Res.get("dao.tab.proposals").toUpperCase());
        bondingTab = new Tab(Res.get("dao.tab.bonding").toUpperCase());
        burnBsqTab = new Tab(Res.get("dao.tab.proofOfBurn").toUpperCase());
        monitor = new Tab(Res.get("dao.tab.monitor").toUpperCase());

        bsqWalletTab.setClosable(false);
        proposalsTab.setClosable(false);
        bondingTab.setClosable(false);
        burnBsqTab.setClosable(false);
        monitor.setClosable(false);

        if (!DevEnv.isDaoActivated()) {
            bsqWalletTab.setDisable(true);
            proposalsTab.setDisable(true);
            bondingTab.setDisable(true);
            burnBsqTab.setDisable(true);
            monitor.setDisable(true);

            daoNewsTab = new Tab(Res.get("dao.tab.news").toUpperCase());

            root.getTabs().add(daoNewsTab);
        } else {
            root.getTabs().addAll(bsqWalletTab, proposalsTab, bondingTab, burnBsqTab, monitor);
        }

        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(DaoView.class) == 1) {
                if (proposalsTab == null && viewPath.get(2).equals(BsqWalletView.class))
                    navigation.navigateTo(MainView.class, DaoView.class, BsqWalletView.class);
                else
                    loadView(viewPath.tip());
            }
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == bsqWalletTab) {
                Class<? extends View> selectedViewClass = bsqWalletView.getSelectedViewClass();
                if (selectedViewClass == null)
                    navigation.navigateTo(MainView.class, DaoView.class, BsqWalletView.class, BsqDashboardView.class);
                else
                    navigation.navigateTo(MainView.class, DaoView.class, BsqWalletView.class, selectedViewClass);
            } else if (newValue == proposalsTab) {
                navigation.navigateTo(MainView.class, DaoView.class, GovernanceView.class);
            } else if (newValue == bondingTab) {
                navigation.navigateTo(MainView.class, DaoView.class, BondingView.class);
            } else if (newValue == burnBsqTab) {
                navigation.navigateTo(MainView.class, DaoView.class, BurnBsqView.class);
            } else if (newValue == monitor) {
                navigation.navigateTo(MainView.class, DaoView.class, MonitorView.class);
            }
        };
    }

    @Override
    protected void activate() {
        if (DevEnv.isDaoActivated()) {
            navigation.addListener(navigationListener);
            root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);

            if (navigation.getCurrentPath().size() == 2 && navigation.getCurrentPath().get(1) == DaoView.class) {
                Tab selectedItem = root.getSelectionModel().getSelectedItem();
                if (selectedItem == bsqWalletTab)
                    navigation.navigateTo(MainView.class, DaoView.class, BsqWalletView.class);
                else if (selectedItem == proposalsTab)
                    navigation.navigateTo(MainView.class, DaoView.class, GovernanceView.class);
                else if (selectedItem == bondingTab)
                    navigation.navigateTo(MainView.class, DaoView.class, BondingView.class);
                else if (selectedItem == burnBsqTab)
                    navigation.navigateTo(MainView.class, DaoView.class, BurnBsqView.class);
                else if (selectedItem == monitor)
                    navigation.navigateTo(MainView.class, DaoView.class, MonitorView.class);
            }
        } else {
            loadView(NewsView.class);
        }
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
    }

    private void loadView(Class<? extends View> viewClass) {

        if (selectedTab != null && selectedTab.getContent() != null) {
            if (selectedTab.getContent() instanceof ScrollPane) {
                ((ScrollPane) selectedTab.getContent()).setContent(null);
            } else {
                selectedTab.setContent(null);
            }
        }

        View view = viewLoader.load(viewClass);
        if (view instanceof BsqWalletView) {
            selectedTab = bsqWalletTab;
            bsqWalletView = (BsqWalletView) view;
        } else if (view instanceof GovernanceView) {
            selectedTab = proposalsTab;
        } else if (view instanceof BondingView) {
            selectedTab = bondingTab;
        } else if (view instanceof BurnBsqView) {
            selectedTab = burnBsqTab;
        } else if (view instanceof MonitorView) {
            selectedTab = monitor;
        } else if (view instanceof NewsView) {
            selectedTab = daoNewsTab;
        }

        selectedTab.setContent(view.getRoot());
        root.getSelectionModel().select(selectedTab);
    }
}

