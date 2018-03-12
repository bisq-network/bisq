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

package bisq.desktop.main.account;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.main.MainView;
import bisq.desktop.main.account.arbitratorregistration.ArbitratorRegistrationView;
import bisq.desktop.main.account.content.fiataccounts.FiatAccountsView;
import bisq.desktop.main.account.settings.AccountSettingsView;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.common.app.DevEnv;
import bisq.common.locale.Res;
import bisq.common.util.Utilities;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import javafx.beans.value.ChangeListener;

import javafx.event.EventHandler;

@FxmlView
public class AccountView extends ActivatableView<TabPane, AccountViewModel> {

    @FXML
    Tab accountSettingsTab;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private Tab selectedTab;
    Tab arbitratorRegistrationTab;
    private ArbitratorRegistrationView arbitratorRegistrationView;
    private AccountSettingsView accountSettingsView;
    private Scene scene;
    private EventHandler<KeyEvent> keyEventEventHandler;

    @Inject
    private AccountView(AccountViewModel model, CachingViewLoader viewLoader, Navigation navigation) {
        super(model);
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(AccountView.class) == 1) {
                if (arbitratorRegistrationTab == null && viewPath.get(2).equals(ArbitratorRegistrationView.class))
                    //noinspection unchecked
                    navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, FiatAccountsView.class);
                else
                    loadView(viewPath.tip());
            }
        };

        keyEventEventHandler = event -> {
            if (Utilities.isAltOrCtrlPressed(KeyCode.R, event) &&
                    arbitratorRegistrationTab == null) {
                arbitratorRegistrationTab = new Tab(Res.get("account.tab.arbitratorRegistration"));
                arbitratorRegistrationTab.setClosable(false);
                root.getTabs().add(arbitratorRegistrationTab);
            }
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == accountSettingsTab) {
                Class<? extends View> selectedViewClass = accountSettingsView.getSelectedViewClass();
                if (selectedViewClass == null)
                    //noinspection unchecked
                    navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, FiatAccountsView.class);
                else
                    //noinspection unchecked
                    navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, selectedViewClass);
            } else if (arbitratorRegistrationTab != null) {
                //noinspection unchecked
                navigation.navigateTo(MainView.class, AccountView.class, ArbitratorRegistrationView.class);
            } else {
                //noinspection unchecked
                navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class);
            }
        };
    }

    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    protected void activate() {
        navigation.addListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        scene = root.getScene();
        if (scene != null)
            scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);

        if (navigation.getCurrentPath().size() == 2 && navigation.getCurrentPath().get(1) == AccountView.class) {
            if (root.getSelectionModel().getSelectedItem() == accountSettingsTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class);
            else if (arbitratorRegistrationTab != null)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, AccountView.class, ArbitratorRegistrationView.class);
            else
                //noinspection unchecked
                navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class);
        }

        //noinspection UnusedAssignment
        String key = "accountPrivacyInfo";
        if (!DevEnv.isDevMode())
            new Popup<>()
                    .headLine(Res.get("account.info.headline"))
                    .backgroundInfo(Res.get("account.info.msg"))
                    .dontShowAgainId(key)
                    .show();
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);

        if (scene != null)
            scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
    }

    private void loadView(Class<? extends View> viewClass) {
        View view = viewLoader.load(viewClass);

        if (view instanceof AccountSettingsView) {
            selectedTab = accountSettingsTab;
            accountSettingsView = (AccountSettingsView) view;
            selectedTab.setText(Res.get("account.tab.account"));
            if (arbitratorRegistrationTab != null) {
                arbitratorRegistrationTab.setDisable(false);
                if (arbitratorRegistrationView != null)
                    arbitratorRegistrationView.onTabSelection(false);
            }
        } else if (view instanceof ArbitratorRegistrationView) {
            if (arbitratorRegistrationTab != null) {
                selectedTab = arbitratorRegistrationTab;
                arbitratorRegistrationView = (ArbitratorRegistrationView) view;
                arbitratorRegistrationView.onTabSelection(true);
            }
        } else {
            throw new IllegalArgumentException("View not supported: " + view);
        }

        selectedTab.setContent(view.getRoot());
        root.getSelectionModel().select(selectedTab);
    }
}
