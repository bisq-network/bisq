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
import bisq.desktop.main.account.content.altcoinaccounts.AltCoinAccountsView;
import bisq.desktop.main.account.content.backup.BackupView;
import bisq.desktop.main.account.content.fiataccounts.FiatAccountsView;
import bisq.desktop.main.account.content.notifications.MobileNotificationsView;
import bisq.desktop.main.account.content.password.PasswordView;
import bisq.desktop.main.account.content.seedwords.SeedWordsView;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.locale.Res;

import bisq.common.app.DevEnv;
import bisq.common.util.Utilities;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import javafx.beans.value.ChangeListener;

import javafx.event.EventHandler;

import javafx.collections.ListChangeListener;

import java.util.List;

@FxmlView
public class AccountView extends ActivatableView<TabPane, Void> {

    @FXML
    Tab fiatAccountsTab, altcoinAccountsTab, notificationTab,
            passwordTab, seedwordsTab, backupTab;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private Tab selectedTab;
    private Tab arbitratorRegistrationTab;
    private ArbitratorRegistrationView arbitratorRegistrationView;
    private Scene scene;
    private EventHandler<KeyEvent> keyEventEventHandler;
    private ListChangeListener<Tab> tabListChangeListener;

    @Inject
    private AccountView(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {

        root.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        fiatAccountsTab.setText(Res.get("account.menu.paymentAccount").toUpperCase());
        altcoinAccountsTab.setText(Res.get("account.menu.altCoinsAccountView").toUpperCase());
        notificationTab.setText(Res.get("account.menu.notifications").toUpperCase());
        passwordTab.setText(Res.get("account.menu.password").toUpperCase());
        seedwordsTab.setText(Res.get("account.menu.seedWords").toUpperCase());
        backupTab.setText(Res.get("account.menu.backup").toUpperCase());

        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(AccountView.class) == 1) {
                if (arbitratorRegistrationTab == null && viewPath.get(2).equals(ArbitratorRegistrationView.class))
                    navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
                else
                    loadView(viewPath.tip());
            } else {
                resetSelectedTab();
            }
        };

        keyEventEventHandler = event -> {
            if (Utilities.isAltOrCtrlPressed(KeyCode.R, event) &&
                    arbitratorRegistrationTab == null) {
                arbitratorRegistrationTab = new Tab(Res.get("account.tab.arbitratorRegistration").toUpperCase());
                arbitratorRegistrationTab.setClosable(true);
                root.getTabs().add(arbitratorRegistrationTab);

                navigation.navigateTo(MainView.class, AccountView.class, ArbitratorRegistrationView.class);
            }
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (arbitratorRegistrationTab != null && selectedTab != arbitratorRegistrationTab) {
                navigation.navigateTo(MainView.class, AccountView.class, ArbitratorRegistrationView.class);
            } else if (newValue == fiatAccountsTab && selectedTab != fiatAccountsTab) {
                navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
            } else if (newValue == altcoinAccountsTab && selectedTab != altcoinAccountsTab) {
                navigation.navigateTo(MainView.class, AccountView.class, AltCoinAccountsView.class);
            } else if (newValue == notificationTab && selectedTab != notificationTab) {
                navigation.navigateTo(MainView.class, AccountView.class, MobileNotificationsView.class);
            } else if (newValue == passwordTab && selectedTab != passwordTab) {
                navigation.navigateTo(MainView.class, AccountView.class, PasswordView.class);
            } else if (newValue == seedwordsTab && selectedTab != seedwordsTab) {
                navigation.navigateTo(MainView.class, AccountView.class, SeedWordsView.class);
            } else if (newValue == backupTab && selectedTab != backupTab) {
                navigation.navigateTo(MainView.class, AccountView.class, BackupView.class);
            }
        };

        tabListChangeListener = change -> {
            change.next();
            List<? extends Tab> removedTabs = change.getRemoved();
            if (removedTabs.size() == 1 && removedTabs.get(0).equals(arbitratorRegistrationTab))
                onArbitratorRegistrationTabRemoved();
        };
    }

    private void onArbitratorRegistrationTabRemoved() {
        arbitratorRegistrationTab = null;

        navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
    }

    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    protected void activate() {
        navigation.addListener(navigationListener);

        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        root.getTabs().addListener(tabListChangeListener);

        scene = root.getScene();
        if (scene != null)
            scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);

        if (navigation.getCurrentPath().size() == 2 && navigation.getCurrentPath().get(1) == AccountView.class) {
            if (arbitratorRegistrationTab != null)
                navigation.navigateTo(MainView.class, AccountView.class, ArbitratorRegistrationView.class);
            else if (root.getSelectionModel().getSelectedItem() == fiatAccountsTab)
                navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
            else if (root.getSelectionModel().getSelectedItem() == altcoinAccountsTab)
                navigation.navigateTo(MainView.class, AccountView.class, AltCoinAccountsView.class);
            else if (root.getSelectionModel().getSelectedItem() == notificationTab)
                navigation.navigateTo(MainView.class, AccountView.class, MobileNotificationsView.class);
            else if (root.getSelectionModel().getSelectedItem() == passwordTab)
                navigation.navigateTo(MainView.class, AccountView.class, PasswordView.class);
            else if (root.getSelectionModel().getSelectedItem() == seedwordsTab)
                navigation.navigateTo(MainView.class, AccountView.class, SeedWordsView.class);
            else if (root.getSelectionModel().getSelectedItem() == backupTab)
                navigation.navigateTo(MainView.class, AccountView.class, BackupView.class);
            else
                navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
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
        root.getTabs().removeListener(tabListChangeListener);

        if (scene != null)
            scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
    }

    private void loadView(Class<? extends View> viewClass) {
        View view = viewLoader.load(viewClass);

        resetSelectedTab();

        if (view instanceof ArbitratorRegistrationView) {
            if (arbitratorRegistrationTab != null) {
                selectedTab = arbitratorRegistrationTab;
                arbitratorRegistrationView = (ArbitratorRegistrationView) view;
                arbitratorRegistrationView.onTabSelection(true);
            }
        } else if (view instanceof FiatAccountsView) {
            selectedTab = fiatAccountsTab;
        } else if (view instanceof AltCoinAccountsView) {
            selectedTab = altcoinAccountsTab;
        } else if (view instanceof MobileNotificationsView) {
            selectedTab = notificationTab;
        } else if (view instanceof PasswordView) {
            selectedTab = passwordTab;
        } else if (view instanceof SeedWordsView) {
            selectedTab = seedwordsTab;
        } else if (view instanceof BackupView) {
            selectedTab = backupTab;
        } else {
            throw new IllegalArgumentException("View not supported: " + view);
        }

        if (selectedTab.getContent() != null && selectedTab.getContent() instanceof ScrollPane) {
            ((ScrollPane) selectedTab.getContent()).setContent(view.getRoot());
        } else {
            selectedTab.setContent(view.getRoot());
        }
        root.getSelectionModel().select(selectedTab);
    }

    private void resetSelectedTab() {
        if (selectedTab != null && selectedTab.getContent() != null) {
            if (selectedTab.getContent() instanceof ScrollPane) {
                ((ScrollPane) selectedTab.getContent()).setContent(null);
            } else {
                selectedTab.setContent(null);
            }
        }
    }
}
