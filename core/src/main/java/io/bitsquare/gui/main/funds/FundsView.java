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

package io.bitsquare.gui.main.funds;

import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.funds.transactions.TransactionsView;
import io.bitsquare.gui.main.funds.withdrawal.WithdrawalView;
import io.bitsquare.common.viewfx.model.Activatable;
import io.bitsquare.common.viewfx.view.ActivatableViewAndModel;
import io.bitsquare.common.viewfx.view.CachingViewLoader;
import io.bitsquare.common.viewfx.view.FxmlView;
import io.bitsquare.common.viewfx.view.View;
import io.bitsquare.common.viewfx.view.ViewLoader;

import javax.inject.Inject;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;

@FxmlView
public class FundsView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML Tab withdrawalTab, transactionsTab;

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
        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(FundsView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == withdrawalTab)
                navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class);
            else if (newValue == transactionsTab)
                navigation.navigateTo(MainView.class, FundsView.class, TransactionsView.class);
        };
    }

    @Override
    public void doActivate() {
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (root.getSelectionModel().getSelectedItem() == transactionsTab)
            navigation.navigateTo(MainView.class, FundsView.class, TransactionsView.class);
        else
            navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class);
    }

    @Override
    public void doDeactivate() {
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);
        currentTab = null;
    }

    private void loadView(Class<? extends View> viewClass) {
        // we want to get activate/deactivate called, so we remove the old view on tab change
        if (currentTab != null)
            currentTab.setContent(null);

        View view = viewLoader.load(viewClass);

        if (view instanceof WithdrawalView)
            currentTab = withdrawalTab;
        else if (view instanceof TransactionsView)
            currentTab = transactionsTab;

        currentTab.setContent(view.getRoot());
        root.getSelectionModel().select(currentTab);
    }
}

