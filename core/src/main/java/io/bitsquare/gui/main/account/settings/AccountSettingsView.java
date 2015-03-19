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

package io.bitsquare.gui.main.account.settings;

import io.bitsquare.BitsquareException;
import io.bitsquare.common.viewfx.view.ActivatableViewAndModel;
import io.bitsquare.common.viewfx.view.CachingViewLoader;
import io.bitsquare.common.viewfx.view.FxmlView;
import io.bitsquare.common.viewfx.view.View;
import io.bitsquare.common.viewfx.view.ViewLoader;
import io.bitsquare.common.viewfx.view.ViewPath;
import io.bitsquare.common.viewfx.view.Wizard;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.account.AccountView;
import io.bitsquare.gui.main.account.content.changepassword.ChangePasswordView;
import io.bitsquare.gui.main.account.content.irc.IrcAccountView;
import io.bitsquare.gui.main.account.content.registration.RegistrationView;
import io.bitsquare.gui.main.account.content.restrictions.RestrictionsView;
import io.bitsquare.gui.main.account.content.seedwords.SeedWordsView;
import io.bitsquare.gui.util.Colors;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

@FxmlView
public class AccountSettingsView extends ActivatableViewAndModel {

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    private MenuItem seedWords, password, restrictions, ircAccount, registration;
    private Navigation.Listener listener;

    @FXML private VBox leftVBox;
    @FXML private AnchorPane content;

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

            loadView(viewPath.tip());
        };

        ToggleGroup toggleGroup = new ToggleGroup();
        seedWords = new MenuItem(navigation, toggleGroup, "Wallet seed", SeedWordsView.class);
        password = new MenuItem(navigation, toggleGroup, "Wallet password", ChangePasswordView.class);
        restrictions = new MenuItem(navigation, toggleGroup, "Arbitrator selection", RestrictionsView.class);
        ircAccount = new MenuItem(navigation, toggleGroup, "Payments account(s)", IrcAccountView.class);
        registration = new MenuItem(navigation, toggleGroup, "Renew your account", RegistrationView.class);

        seedWords.setDisable(true);
        password.setDisable(true);
        restrictions.setDisable(true);
        registration.setDisable(true);

        leftVBox.getChildren().addAll(seedWords, password, restrictions, ircAccount, registration);
    }

    @Override
    public void doActivate() {
        navigation.addListener(listener);
        ViewPath viewPath = navigation.getCurrentPath();
        if (viewPath.size() == 3 && viewPath.indexOf(AccountSettingsView.class) == 2) {
            navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, IrcAccountView.class);
        }
        else if (viewPath.size() == 4 && viewPath.indexOf(AccountSettingsView.class) == 2) {
            loadView(viewPath.get(3));
        }
    }

    @Override
    public void doDeactivate() {
        navigation.removeListener(listener);
    }

    private void loadView(Class<? extends View> viewClass) {
        View view = viewLoader.load(viewClass);
        content.getChildren().setAll(view.getRoot());
        if (view instanceof Wizard.Step)
            ((Wizard.Step) view).hideWizardNavigation();

        if (view instanceof SeedWordsView) seedWords.setSelected(true);
        else if (view instanceof ChangePasswordView) password.setSelected(true);
        else if (view instanceof RestrictionsView) restrictions.setSelected(true);
        else if (view instanceof IrcAccountView) ircAccount.setSelected(true);
        else if (view instanceof RegistrationView) registration.setSelected(true);
        else throw new BitsquareException("Selecting main menu button for view " + view + " is not supported");
    }
}


class MenuItem extends ToggleButton {

    MenuItem(Navigation navigation, ToggleGroup toggleGroup, String title, Class<? extends View> viewClass) {

        setToggleGroup(toggleGroup);
        setText(title);
        setId("account-settings-item-background-active");
        setPrefHeight(40);
        setPrefWidth(200);
        setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label();
        icon.setTextFill(Paint.valueOf("#999"));
        if (viewClass == SeedWordsView.class)
            AwesomeDude.setIcon(icon, AwesomeIcon.INFO_SIGN);
        else if (viewClass == RegistrationView.class)
            AwesomeDude.setIcon(icon, AwesomeIcon.BRIEFCASE);
        else
            AwesomeDude.setIcon(icon, AwesomeIcon.EDIT_SIGN);

        setGraphic(icon);

        setOnAction((event) ->
                navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, viewClass));

        selectedProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                setId("account-settings-item-background-selected");
                icon.setTextFill(Colors.BLUE);
            }
            else {
                setId("account-settings-item-background-active");
                icon.setTextFill(Paint.valueOf("#999"));
            }
        });

        disableProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                setId("account-settings-item-background-disabled");
                icon.setTextFill(Paint.valueOf("#ccc"));
            }
            else {
                setId("account-settings-item-background-active");
                icon.setTextFill(Paint.valueOf("#999"));
            }
        });
    }
}

