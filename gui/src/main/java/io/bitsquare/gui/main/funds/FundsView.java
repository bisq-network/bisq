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
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.view.*;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.funds.deposit.DepositView;
import io.bitsquare.gui.main.funds.reserved.ReservedView;
import io.bitsquare.gui.main.funds.transactions.TransactionsView;
import io.bitsquare.gui.main.funds.withdrawal.WithdrawalView;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import javax.inject.Inject;

@FxmlView
public class FundsView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML
    Tab depositTab, withdrawalTab, reservedTab, transactionsTab;

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
            if (newValue == depositTab)
                navigation.navigateTo(MainView.class, FundsView.class, DepositView.class);
            else if (newValue == withdrawalTab)
                navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class);
            else if (newValue == reservedTab)
                navigation.navigateTo(MainView.class, FundsView.class, ReservedView.class);
            else if (newValue == transactionsTab)
                navigation.navigateTo(MainView.class, FundsView.class, TransactionsView.class);
        };
    }

    @Override
    protected void activate() {
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (root.getSelectionModel().getSelectedItem() == depositTab)
            navigation.navigateTo(MainView.class, FundsView.class, DepositView.class);
        else if (root.getSelectionModel().getSelectedItem() == withdrawalTab)
            navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class);
        else if (root.getSelectionModel().getSelectedItem() == reservedTab)
            navigation.navigateTo(MainView.class, FundsView.class, ReservedView.class);
        else if (root.getSelectionModel().getSelectedItem() == transactionsTab)
            navigation.navigateTo(MainView.class, FundsView.class, TransactionsView.class);

        String key = "tradeWalletInfoAtFunds";
      /*  if (!BitsquareApp.DEV_MODE)
            new Popup().backgroundInfo("Bitsquare does not use a single application wallet, but dedicated wallets for every trade.\n\n" +
                    "Funding of the wallet will be done when needed, for instance when you create or take an offer.\n" +
                    "Withdrawing funds can be done after a trade is completed.\n\n" +
                    "Dedicated wallets help protect user privacy and prevent leaking information of previous trades to other " +
                    "traders.")
                    .actionButtonText("Visit FAQ web page")
                    .onAction(() -> Utilities.openWebPage("https://bitsquare.io/faq"))
                    .closeButtonText("I understand")
                    .dontShowAgainId(key, preferences)
                    .show();*/
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
        else if (view instanceof TransactionsView)
            currentTab = transactionsTab;

        currentTab.setContent(view.getRoot());
        root.getSelectionModel().select(currentTab);
    }
}

