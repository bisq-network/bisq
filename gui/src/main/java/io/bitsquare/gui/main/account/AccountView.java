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
import io.bitsquare.gui.common.view.*;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.account.arbitratorregistration.ArbitratorRegistrationView;
import io.bitsquare.gui.main.account.settings.AccountSettingsView;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import javax.inject.Inject;

@FxmlView
public class AccountView extends ActivatableView<TabPane, AccountViewModel> {

    @FXML
    Tab accountSettingsTab, arbitratorRegistrationTab;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private View accountSetupWizardView;
    private Tab tab;
    private ArbitratorRegistrationView arbitratorRegistrationView;

    @Inject
    private AccountView(AccountViewModel model, CachingViewLoader viewLoader, Navigation navigation) {
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
                navigation.navigateTo(MainView.class, AccountView.class, ArbitratorRegistrationView.class);
        };
    }

    @Override
    protected void activate() {
        navigation.addListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);

        if (navigation.getCurrentPath().size() == 2 && navigation.getCurrentPath().get(1) == AccountView.class) {
           /* if (model.getNeedRegistration()) {
                navigation.navigateTo(MainView.class, AccountView.class, AccountSetupWizard.class);
            }
            else {*/
            if (root.getSelectionModel().getSelectedItem() == accountSettingsTab)
                navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class);
            else
                navigation.navigateTo(MainView.class, AccountView.class, ArbitratorRegistrationView.class);
            // }
        }
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);

       /* if (accountSetupWizardView != null)
            tab.setContent(null);*/
    }

    private void loadView(Class<? extends View> viewClass) {
        View view = viewLoader.load(viewClass);

        if (view instanceof AccountSettingsView) {
            tab = accountSettingsTab;
            tab.setText("Account settings");
            arbitratorRegistrationTab.setDisable(false);
            if (arbitratorRegistrationView != null)
                arbitratorRegistrationView.onTabSelection(false);
        }
       /* else if (view instanceof AccountSetupWizard) {
            tab = accountSettingsTab;
            tab.setText("Account setup");
            arbitratorRegistrationTab.setDisable(true);
            accountSetupWizardView = view;
        }*/
        else if (view instanceof ArbitratorRegistrationView) {
            tab = arbitratorRegistrationTab;
            arbitratorRegistrationView = (ArbitratorRegistrationView) view;
            arbitratorRegistrationView.onTabSelection(true);
        }
        else {
            throw new IllegalArgumentException("View not supported: " + view);
        }

        tab.setContent(view.getRoot());
        root.getSelectionModel().select(tab);
    }
}
