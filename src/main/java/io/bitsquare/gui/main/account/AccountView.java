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

package io.bitsquare.gui.main.account;

import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.account.arbitrator.ArbitratorSettingsView;
import io.bitsquare.gui.main.account.settings.AccountSettingsView;
import io.bitsquare.gui.main.account.setup.AccountSetupWizard;

import javax.inject.Inject;

import viewfx.view.FxmlView;
import viewfx.view.View;
import viewfx.view.ViewLoader;
import viewfx.view.ViewPath;
import viewfx.view.support.ActivatableView;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;

@FxmlView
public class AccountView extends ActivatableView<TabPane, AccountViewModel> {

    @FXML Tab accountSettingsTab, arbitratorSettingsTab;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    @Inject
    private AccountView(AccountViewModel model, ViewLoader viewLoader, Navigation navigation) {
        super(model);
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(AccountView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == accountSettingsTab)
                navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class);
            else
                navigation.navigateTo(MainView.class, AccountView.class, ArbitratorSettingsView.class);
        };
    }

    @Override
    public void activate() {
        navigation.addListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);

        if (navigation.getCurrentPath().size() == 2 &&
                navigation.getCurrentPath().get(1) == AccountView.class) {
            if (model.getNeedRegistration()) {
                navigation.navigateTo(MainView.class, AccountView.class, AccountSetupWizard.class);
            }
            else {
                if (root.getSelectionModel().getSelectedItem() == accountSettingsTab)
                    navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class);
                else
                    navigation.navigateTo(MainView.class, AccountView.class, ArbitratorSettingsView.class);
            }
        }
    }

    @Override
    public void deactivate() {
        navigation.removeListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
    }

    private void loadView(Class<? extends View> viewClass) {
        Tab tab;
        View view = viewLoader.load(viewClass);

        if (view instanceof AccountSettingsView) {
            tab = accountSettingsTab;
            tab.setText("Account settings");
            arbitratorSettingsTab.setDisable(false);
        }
        else if (view instanceof AccountSetupWizard) {
            tab = accountSettingsTab;
            tab.setText("Account setup");
            arbitratorSettingsTab.setDisable(true);
        }
        else if (view instanceof ArbitratorSettingsView) {
            tab = arbitratorSettingsTab;
        }
        else {
            throw new IllegalArgumentException("View not supported: " + view);
        }

        // for IRC demo we deactivate the arbitratorSettingsTab
        arbitratorSettingsTab.setDisable(true);

        tab.setContent(view.getRoot());
        root.getSelectionModel().select(tab);
    }
}
