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
import bisq.desktop.main.account.content.altcoinaccounts.AltCoinAccountsView;
import bisq.desktop.main.account.content.backup.BackupView;
import bisq.desktop.main.account.content.fiataccounts.FiatAccountsView;
import bisq.desktop.main.account.content.notifications.MobileNotificationsView;
import bisq.desktop.main.account.content.password.PasswordView;
import bisq.desktop.main.account.content.seedwords.SeedWordsView;
import bisq.desktop.main.account.content.walletinfo.WalletInfoView;
import bisq.desktop.main.account.register.arbitrator.ArbitratorRegistrationView;
import bisq.desktop.main.account.register.mediator.MediatorRegistrationView;
import bisq.desktop.main.account.register.refundagent.RefundAgentRegistrationView;
import bisq.desktop.main.account.register.signing.SigningView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.presentation.AccountPresentation;

import bisq.core.locale.Res;
import bisq.core.user.DontShowAgainLookup;

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
            passwordTab, seedWordsTab, walletInfoTab, backupTab;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private Tab selectedTab;
    private Tab arbitratorRegistrationTab;
    private Tab mediatorRegistrationTab;
    private Tab refundAgentRegistrationTab;
    private Tab signingTab;
    private ArbitratorRegistrationView arbitratorRegistrationView;
    private MediatorRegistrationView mediatorRegistrationView;
    private RefundAgentRegistrationView refundAgentRegistrationView;
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
        seedWordsTab.setText(Res.get("account.menu.seedWords").toUpperCase());
        walletInfoTab.setText(Res.get("account.menu.walletInfo").toUpperCase());
        backupTab.setText(Res.get("account.menu.backup").toUpperCase());

        navigationListener = (viewPath, data) -> {
            if (viewPath.size() == 3 && viewPath.indexOf(AccountView.class) == 1) {
                if (arbitratorRegistrationTab == null && viewPath.get(2).equals(ArbitratorRegistrationView.class)) {
                    navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
                } else if (mediatorRegistrationTab == null && viewPath.get(2).equals(MediatorRegistrationView.class)) {
                    navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
                } else if (refundAgentRegistrationTab == null && viewPath.get(2).equals(RefundAgentRegistrationView.class)) {
                    navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
                } else if (signingTab == null && viewPath.get(2).equals(SigningView.class)) {
                    navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
                } else {
                    loadView(viewPath.tip());
                }
            } else {
                resetSelectedTab();
            }
        };

        keyEventEventHandler = event -> {
            if (Utilities.isAltOrCtrlPressed(KeyCode.D, event) && mediatorRegistrationTab == null) {
                closeOtherExtraTabs(mediatorRegistrationTab);
                mediatorRegistrationTab = new Tab(Res.get("account.tab.mediatorRegistration").toUpperCase());
                mediatorRegistrationTab.setClosable(true);
                root.getTabs().add(mediatorRegistrationTab);
                navigation.navigateTo(MainView.class, AccountView.class, MediatorRegistrationView.class);
            } else if (Utilities.isAltOrCtrlPressed(KeyCode.N, event) && refundAgentRegistrationTab == null) {
                closeOtherExtraTabs(refundAgentRegistrationTab);
                refundAgentRegistrationTab = new Tab(Res.get("account.tab.refundAgentRegistration").toUpperCase());
                refundAgentRegistrationTab.setClosable(true);
                root.getTabs().add(refundAgentRegistrationTab);
                navigation.navigateTo(MainView.class, AccountView.class, RefundAgentRegistrationView.class);
            } else if (Utilities.isAltOrCtrlPressed(KeyCode.I, event) && signingTab == null) {
                closeOtherExtraTabs(signingTab);
                signingTab = new Tab(Res.get("account.tab.signing").toUpperCase());
                signingTab.setClosable(true);
                root.getTabs().add(signingTab);
                navigation.navigateTo(MainView.class, AccountView.class, SigningView.class);
            }
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (arbitratorRegistrationTab != null && selectedTab != arbitratorRegistrationTab) {
                navigation.navigateTo(MainView.class, AccountView.class, ArbitratorRegistrationView.class);
            } else if (mediatorRegistrationTab != null && selectedTab != mediatorRegistrationTab) {
                navigation.navigateTo(MainView.class, AccountView.class, MediatorRegistrationView.class);
            } else if (refundAgentRegistrationTab != null && selectedTab != refundAgentRegistrationTab) {
                navigation.navigateTo(MainView.class, AccountView.class, RefundAgentRegistrationView.class);
            } else if (signingTab != null && !selectedTab.equals(signingTab)) {
                navigation.navigateTo(MainView.class, AccountView.class, SigningView.class);
            } else if (newValue == fiatAccountsTab && selectedTab != fiatAccountsTab) {
                navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
            } else if (newValue == altcoinAccountsTab && selectedTab != altcoinAccountsTab) {
                navigation.navigateTo(MainView.class, AccountView.class, AltCoinAccountsView.class);
            } else if (newValue == notificationTab && selectedTab != notificationTab) {
                navigation.navigateTo(MainView.class, AccountView.class, MobileNotificationsView.class);
            } else if (newValue == passwordTab && selectedTab != passwordTab) {
                navigation.navigateTo(MainView.class, AccountView.class, PasswordView.class);
            } else if (newValue == seedWordsTab && selectedTab != seedWordsTab) {
                navigation.navigateTo(MainView.class, AccountView.class, SeedWordsView.class);
            } else if (newValue == walletInfoTab && selectedTab != walletInfoTab) {
                navigation.navigateTo(MainView.class, AccountView.class, WalletInfoView.class);
            } else if (newValue == backupTab && selectedTab != backupTab) {
                navigation.navigateTo(MainView.class, AccountView.class, BackupView.class);
            }
        };

        tabListChangeListener = change -> {
            change.next();
            List<? extends Tab> removedTabs = change.getRemoved();
            if (removedTabs.size() == 1 && removedTabs.get(0).equals(arbitratorRegistrationTab))
                onArbitratorRegistrationTabRemoved();

            if (removedTabs.size() == 1 && removedTabs.get(0).equals(mediatorRegistrationTab))
                onMediatorRegistrationTabRemoved();

            if (removedTabs.size() == 1 && removedTabs.get(0).equals(refundAgentRegistrationTab))
                onRefundAgentRegistrationTabRemoved();

            if (removedTabs.size() == 1 && removedTabs.get(0).equals(signingTab))
                onSigningTabRemoved();
        };
    }

    private void closeOtherExtraTabs(Tab newTab) {
        if (arbitratorRegistrationTab != null && !arbitratorRegistrationTab.equals(newTab)) {
            root.getTabs().remove(arbitratorRegistrationTab);
        }
        if (mediatorRegistrationTab != null && !mediatorRegistrationTab.equals(newTab)) {
            root.getTabs().remove(mediatorRegistrationTab);
        }
        if (refundAgentRegistrationTab != null && !refundAgentRegistrationTab.equals(newTab)) {
            root.getTabs().remove(refundAgentRegistrationTab);
        }
        if (signingTab != null && !signingTab.equals(newTab)) {
            root.getTabs().remove(signingTab);
        }
    }

    private void onArbitratorRegistrationTabRemoved() {
        arbitratorRegistrationTab = null;
        navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
    }

    private void onMediatorRegistrationTabRemoved() {
        mediatorRegistrationTab = null;
        navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
    }

    private void onRefundAgentRegistrationTabRemoved() {
        refundAgentRegistrationTab = null;
        navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
    }

    private void onSigningTabRemoved() {
        signingTab = null;
        navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
    }

    @Override
    protected void activate() {
        // Hide account new badge if user saw this section
        DontShowAgainLookup.dontShowAgain(AccountPresentation.ACCOUNT_NEWS, true);

        navigation.addListener(navigationListener);

        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        root.getTabs().addListener(tabListChangeListener);

        scene = root.getScene();
        if (scene != null)
            scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);

        if (navigation.getCurrentPath().size() == 2 && navigation.getCurrentPath().get(1) == AccountView.class) {
            if (arbitratorRegistrationTab != null)
                navigation.navigateTo(MainView.class, AccountView.class, ArbitratorRegistrationView.class);
            else if (mediatorRegistrationTab != null)
                navigation.navigateTo(MainView.class, AccountView.class, MediatorRegistrationView.class);
            else if (refundAgentRegistrationTab != null)
                navigation.navigateTo(MainView.class, AccountView.class, RefundAgentRegistrationView.class);
            else if (signingTab != null)
                navigation.navigateTo(MainView.class, AccountView.class, SigningView.class);
            else if (root.getSelectionModel().getSelectedItem() == fiatAccountsTab)
                navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
            else if (root.getSelectionModel().getSelectedItem() == altcoinAccountsTab)
                navigation.navigateTo(MainView.class, AccountView.class, AltCoinAccountsView.class);
            else if (root.getSelectionModel().getSelectedItem() == notificationTab)
                navigation.navigateTo(MainView.class, AccountView.class, MobileNotificationsView.class);
            else if (root.getSelectionModel().getSelectedItem() == passwordTab)
                navigation.navigateTo(MainView.class, AccountView.class, PasswordView.class);
            else if (root.getSelectionModel().getSelectedItem() == seedWordsTab)
                navigation.navigateTo(MainView.class, AccountView.class, SeedWordsView.class);
            else if (root.getSelectionModel().getSelectedItem() == walletInfoTab)
                navigation.navigateTo(MainView.class, AccountView.class, WalletInfoView.class);
            else if (root.getSelectionModel().getSelectedItem() == backupTab)
                navigation.navigateTo(MainView.class, AccountView.class, BackupView.class);
            else
                navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
        }

        String key = "accountPrivacyInfo";
        if (!DevEnv.isDevMode())
            new Popup()
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
        } else if (view instanceof MediatorRegistrationView) {
            if (mediatorRegistrationTab != null) {
                selectedTab = mediatorRegistrationTab;
                mediatorRegistrationView = (MediatorRegistrationView) view;
                mediatorRegistrationView.onTabSelection(true);
            }
        } else if (view instanceof RefundAgentRegistrationView) {
            if (refundAgentRegistrationTab != null) {
                selectedTab = refundAgentRegistrationTab;
                refundAgentRegistrationView = (RefundAgentRegistrationView) view;
                refundAgentRegistrationView.onTabSelection(true);
            }
        } else if (view instanceof SigningView) {
            if (signingTab != null) {
                selectedTab = signingTab;
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
            selectedTab = seedWordsTab;
        } else if (view instanceof WalletInfoView) {
            selectedTab = walletInfoTab;
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
