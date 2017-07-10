/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.funds;

import io.bisq.common.locale.Res;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.model.Activatable;
import io.bisq.gui.common.view.*;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.funds.deposit.DepositView;
import io.bisq.gui.main.funds.locked.LockedView;
import io.bisq.gui.main.funds.reserved.ReservedView;
import io.bisq.gui.main.funds.transactions.TransactionsView;
import io.bisq.gui.main.funds.withdrawal.WithdrawalView;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;

import javax.inject.Inject;

@FxmlView
public class FundsView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML
    TabPane root;
    @FXML
    Tab depositTab, withdrawalTab, reservedTab, lockedTab, transactionsTab;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;
    private Tab currentTab;

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    @Inject
    public FundsView(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        AnchorPane.setTopAnchor(root, MainView.scale(0));
        AnchorPane.setRightAnchor(root, MainView.scale(0));
        AnchorPane.setBottomAnchor(root, MainView.scale(0));
        AnchorPane.setLeftAnchor(root, MainView.scale(0));
        depositTab.setText(Res.get("funds.tab.deposit"));
        withdrawalTab.setText(Res.get("funds.tab.withdrawal"));
        reservedTab.setText(Res.get("funds.tab.reserved"));
        lockedTab.setText(Res.get("funds.tab.locked"));
        transactionsTab.setText(Res.get("funds.tab.transactions"));

        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(FundsView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == depositTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, FundsView.class, DepositView.class);
            else if (newValue == withdrawalTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class);
            else if (newValue == reservedTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, FundsView.class, ReservedView.class);
            else if (newValue == lockedTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, FundsView.class, LockedView.class);
            else if (newValue == transactionsTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, FundsView.class, TransactionsView.class);
        };
    }

    @Override
    protected void activate() {
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (root.getSelectionModel().getSelectedItem() == depositTab)
            //noinspection unchecked
            navigation.navigateTo(MainView.class, FundsView.class, DepositView.class);
        else if (root.getSelectionModel().getSelectedItem() == withdrawalTab)
            //noinspection unchecked
            navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class);
        else if (root.getSelectionModel().getSelectedItem() == reservedTab)
            //noinspection unchecked
            navigation.navigateTo(MainView.class, FundsView.class, ReservedView.class);
        else if (root.getSelectionModel().getSelectedItem() == lockedTab)
            //noinspection unchecked
            navigation.navigateTo(MainView.class, FundsView.class, LockedView.class);
        else if (root.getSelectionModel().getSelectedItem() == transactionsTab)
            //noinspection unchecked
            navigation.navigateTo(MainView.class, FundsView.class, TransactionsView.class);
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

        if (view instanceof DepositView)
            currentTab = depositTab;
        else if (view instanceof WithdrawalView)
            currentTab = withdrawalTab;
        else if (view instanceof ReservedView)
            currentTab = reservedTab;
        else if (view instanceof LockedView)
            currentTab = lockedTab;
        else if (view instanceof TransactionsView)
            currentTab = transactionsTab;

        currentTab.setContent(view.getRoot());
        root.getSelectionModel().select(currentTab);
    }
}

