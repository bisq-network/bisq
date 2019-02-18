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

package bisq.desktop.main.dao.wallet;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.common.view.ViewPath;
import bisq.desktop.components.MenuItem;
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.dao.wallet.dashboard.BsqDashboardView;
import bisq.desktop.main.dao.wallet.receive.BsqReceiveView;
import bisq.desktop.main.dao.wallet.send.BsqSendView;
import bisq.desktop.main.dao.wallet.tx.BsqTxView;

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
public class BsqWalletView extends ActivatableViewAndModel {

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    private MenuItem dashboard, send, receive, transactions;
    private Navigation.Listener listener;

    @FXML
    private VBox leftVBox;
    @FXML
    private AnchorPane content;

    private Class<? extends View> selectedViewClass;
    private ToggleGroup toggleGroup;

    @Inject
    private BsqWalletView(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        listener = viewPath -> {
            if (viewPath.size() != 4 || viewPath.indexOf(BsqWalletView.class) != 2)
                return;

            selectedViewClass = viewPath.tip();
            loadView(selectedViewClass);
        };

        toggleGroup = new ToggleGroup();
        List<Class<? extends View>> baseNavPath = Arrays.asList(MainView.class, DaoView.class, BsqWalletView.class);
        dashboard = new MenuItem(navigation, toggleGroup, Res.get("shared.dashboard"), BsqDashboardView.class, baseNavPath);
        send = new MenuItem(navigation, toggleGroup, Res.get("dao.wallet.menuItem.send"), BsqSendView.class, baseNavPath);
        receive = new MenuItem(navigation, toggleGroup, Res.get("dao.wallet.menuItem.receive"), BsqReceiveView.class, baseNavPath);
        transactions = new MenuItem(navigation, toggleGroup, Res.get("dao.wallet.menuItem.transactions"), BsqTxView.class, baseNavPath);
        leftVBox.getChildren().addAll(dashboard, send, receive, transactions);

        // TODO just until DAO is enabled
        if (!DevEnv.isDaoActivated()) {
            dashboard.setDisable(true);
            send.setDisable(true);
            transactions.setDisable(true);
        }
    }

    @Override
    protected void activate() {
        dashboard.activate();
        send.activate();
        receive.activate();
        transactions.activate();

        navigation.addListener(listener);
        ViewPath viewPath = navigation.getCurrentPath();
        if (viewPath.size() == 3 && viewPath.indexOf(BsqWalletView.class) == 2 ||
                viewPath.size() == 2 && viewPath.indexOf(DaoView.class) == 1) {
            if (selectedViewClass == null)
                selectedViewClass = BsqDashboardView.class;

            // TODO just until DAO is enabled
            if (!DevEnv.isDaoActivated())
                selectedViewClass = BsqReceiveView.class;

            loadView(selectedViewClass);
        } else if (viewPath.size() == 4 && viewPath.indexOf(BsqWalletView.class) == 2) {
            selectedViewClass = viewPath.get(3);
            loadView(selectedViewClass);
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected void deactivate() {
        navigation.removeListener(listener);

        dashboard.deactivate();
        send.deactivate();
        receive.deactivate();
        transactions.deactivate();
    }

    private void loadView(Class<? extends View> viewClass) {
        View view = viewLoader.load(viewClass);
        content.getChildren().setAll(view.getRoot());

        if (view instanceof BsqDashboardView) toggleGroup.selectToggle(dashboard);
        else if (view instanceof BsqSendView) toggleGroup.selectToggle(send);
        else if (view instanceof BsqReceiveView) toggleGroup.selectToggle(receive);
        else if (view instanceof BsqTxView) toggleGroup.selectToggle(transactions);
    }

    public Class<? extends View> getSelectedViewClass() {
        return selectedViewClass;
    }
}
