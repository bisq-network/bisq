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

package bisq.desktop.main.account.settings;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.common.view.ViewPath;
import bisq.desktop.components.AutoTooltipToggleButton;
import bisq.desktop.main.MainView;
import bisq.desktop.main.account.AccountView;
import bisq.desktop.main.account.content.altcoinaccounts.AltCoinAccountsView;
import bisq.desktop.main.account.content.arbitratorselection.ArbitratorSelectionView;
import bisq.desktop.main.account.content.backup.BackupView;
import bisq.desktop.main.account.content.fiataccounts.FiatAccountsView;
import bisq.desktop.main.account.content.password.PasswordView;
import bisq.desktop.main.account.content.seedwords.SeedWordsView;
import bisq.desktop.util.Colors;

import bisq.common.locale.Res;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.fxml.FXML;

import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.beans.value.ChangeListener;

@FxmlView
public class AccountSettingsView extends ActivatableViewAndModel {

    private final ViewLoader viewLoader;
    private final Navigation navigation;


    private MenuItem paymentAccount, altCoinsAccountView, arbitratorSelection, password, seedWords, backup;
    private Navigation.Listener listener;

    @FXML
    private ScrollPane scrollPane;
    @FXML
    private VBox leftVBox;
    @FXML
    private AnchorPane content;

    private Class<? extends View> selectedViewClass;

    @Inject
    private AccountSettingsView(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        listener = viewPath -> {
            if (viewPath.size() != 4 || viewPath.indexOf(AccountSettingsView.class) != 2)
                return;

            selectedViewClass = viewPath.tip();
            loadView(selectedViewClass);
        };

        ToggleGroup toggleGroup = new ToggleGroup();
        paymentAccount = new MenuItem(navigation, toggleGroup, Res.get("account.menu.paymentAccount"), FiatAccountsView.class, AwesomeIcon.MONEY);
        altCoinsAccountView = new MenuItem(navigation, toggleGroup, Res.get("account.menu.altCoinsAccountView"), AltCoinAccountsView.class, AwesomeIcon.LINK);
        arbitratorSelection = new MenuItem(navigation, toggleGroup, Res.get("account.menu.arbitratorSelection"),
                ArbitratorSelectionView.class, AwesomeIcon.USER_MD);
        password = new MenuItem(navigation, toggleGroup, Res.get("account.menu.password"), PasswordView.class, AwesomeIcon.UNLOCK_ALT);
        seedWords = new MenuItem(navigation, toggleGroup, Res.get("account.menu.seedWords"), SeedWordsView.class, AwesomeIcon.KEY);
        backup = new MenuItem(navigation, toggleGroup, Res.get("account.menu.backup"), BackupView.class, AwesomeIcon.CLOUD_DOWNLOAD);

        leftVBox.getChildren().addAll(paymentAccount, altCoinsAccountView, arbitratorSelection, password, seedWords, backup);
    }

    @Override
    protected void activate() {
        paymentAccount.activate();
        altCoinsAccountView.activate();
        arbitratorSelection.activate();
        password.activate();
        seedWords.activate();
        backup.activate();

        navigation.addListener(listener);
        ViewPath viewPath = navigation.getCurrentPath();
        if (viewPath.size() == 3 && viewPath.indexOf(AccountSettingsView.class) == 2 ||
                viewPath.size() == 2 && viewPath.indexOf(AccountView.class) == 1) {
            if (selectedViewClass == null)
                selectedViewClass = FiatAccountsView.class;

            loadView(selectedViewClass);
        } else if (viewPath.size() == 4 && viewPath.indexOf(AccountSettingsView.class) == 2) {
            selectedViewClass = viewPath.get(3);
            loadView(selectedViewClass);
        }
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(listener);

        paymentAccount.deactivate();
        altCoinsAccountView.deactivate();
        arbitratorSelection.deactivate();
        password.deactivate();
        seedWords.deactivate();
        backup.deactivate();
    }

    private void loadView(Class<? extends View> viewClass) {
        View view = viewLoader.load(viewClass);
        content.getChildren().setAll(view.getRoot());

        if (view instanceof FiatAccountsView) paymentAccount.setSelected(true);
        else if (view instanceof AltCoinAccountsView) altCoinsAccountView.setSelected(true);
        else if (view instanceof ArbitratorSelectionView) arbitratorSelection.setSelected(true);
        else if (view instanceof PasswordView) password.setSelected(true);
        else if (view instanceof SeedWordsView) seedWords.setSelected(true);
        else if (view instanceof BackupView) backup.setSelected(true);
    }

    public Class<? extends View> getSelectedViewClass() {
        return selectedViewClass;
    }
}


class MenuItem extends AutoTooltipToggleButton {

    private final ChangeListener<Boolean> selectedPropertyChangeListener;
    private final ChangeListener<Boolean> disablePropertyChangeListener;
    private final Navigation navigation;
    private final Class<? extends View> viewClass;

    MenuItem(Navigation navigation, ToggleGroup toggleGroup, String title, Class<? extends View> viewClass, AwesomeIcon awesomeIcon) {
        this.navigation = navigation;
        this.viewClass = viewClass;

        setToggleGroup(toggleGroup);
        setText(title);
        setId("account-settings-item-background-active");
        setPrefHeight(40);
        setPrefWidth(240);
        setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label();
        AwesomeDude.setIcon(icon, awesomeIcon);
        icon.setTextFill(Paint.valueOf("#333"));
        icon.setPadding(new Insets(0, 5, 0, 0));
        icon.setAlignment(Pos.CENTER);
        icon.setMinWidth(25);
        icon.setMaxWidth(25);
        setGraphic(icon);

        selectedPropertyChangeListener = (ov, oldValue, newValue) -> {
            if (newValue) {
                setId("account-settings-item-background-selected");
                icon.setTextFill(Colors.BLUE);
            } else {
                setId("account-settings-item-background-active");
                icon.setTextFill(Paint.valueOf("#333"));
            }
        };

        disablePropertyChangeListener = (ov, oldValue, newValue) -> {
            if (newValue) {
                setId("account-settings-item-background-disabled");
                icon.setTextFill(Paint.valueOf("#ccc"));
            } else {
                setId("account-settings-item-background-active");
                icon.setTextFill(Paint.valueOf("#333"));
            }
        };
    }

    public void activate() {
        //noinspection unchecked
        setOnAction((event) -> navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, viewClass));
        selectedProperty().addListener(selectedPropertyChangeListener);
        disableProperty().addListener(disablePropertyChangeListener);
    }

    public void deactivate() {
        setOnAction(null);
        selectedProperty().removeListener(selectedPropertyChangeListener);
        disableProperty().removeListener(disablePropertyChangeListener);
    }
}

