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

import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.gui.main.account.content.ContextAware;
import io.bitsquare.gui.util.Colors;
import io.bitsquare.util.ViewLoader;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountSettingsViewCB extends CachedViewCB {

    private static final Logger log = LoggerFactory.getLogger(AccountSettingsViewCB.class);

    private MenuItem seedWords, password, restrictions, fiatAccount, registration;
    private final Navigation navigation;
    private Navigation.Listener listener;

    @FXML VBox leftVBox;
    @FXML AnchorPane content;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private AccountSettingsViewCB(Navigation navigation) {
        super();

        this.navigation = navigation;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        listener = navigationItems -> {
            if (navigationItems != null &&
                    navigationItems.length == 4 &&
                    navigationItems[2] == Navigation.Item.ACCOUNT_SETTINGS) {
                loadView(navigationItems[3]);
                selectMainMenuButton(navigationItems[3]);
            }
        };

        ToggleGroup toggleGroup = new ToggleGroup();
        seedWords = new MenuItem(navigation, "Wallet seed",
                Navigation.Item.SEED_WORDS, toggleGroup);
        password = new MenuItem(navigation, "Wallet password",
                Navigation.Item.CHANGE_PASSWORD, toggleGroup);
        restrictions = new MenuItem(navigation, "Trading restrictions",
                Navigation.Item.RESTRICTIONS, toggleGroup);
        fiatAccount = new MenuItem(navigation, "Payments account(s)",
                Navigation.Item.FIAT_ACCOUNT, toggleGroup);
        registration = new MenuItem(navigation, "Renew your account",
                Navigation.Item.REGISTRATION, toggleGroup);

        registration.setDisable(true);

        leftVBox.getChildren().addAll(seedWords, password,
                restrictions, fiatAccount, registration);

        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        navigation.addListener(listener);
        Navigation.Item[] items = navigation.getCurrentItems();
        if (items.length == 3 &&
                items[2] == Navigation.Item.ACCOUNT_SETTINGS) {
            navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT,
                    Navigation.Item.ACCOUNT_SETTINGS, Navigation.Item.SEED_WORDS);
        }
        else {
            if (items.length == 4 &&
                    items[2] == Navigation.Item.ACCOUNT_SETTINGS) {
                loadView(items[3]);
                selectMainMenuButton(items[3]);
            }
        }
    }

    @Override
    public void deactivate() {
        super.deactivate();

        navigation.removeListener(listener);
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected Initializable loadView(Navigation.Item navigationItem) {
        final ViewLoader loader = new ViewLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            final Pane view = loader.load();
            content.getChildren().setAll(view);
            childController = loader.getController();
            ((ViewCB<? extends PresentationModel>) childController).setParent(this);
            ((ContextAware) childController).useSettingsContext(true);
            return childController;
        } catch (IOException e) {
            log.error("Loading view failed. FxmlUrl = " + navigationItem.getFxmlUrl());
            e.printStackTrace();
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void selectMainMenuButton(Navigation.Item item) {
        switch (item) {
            case SEED_WORDS:
                seedWords.setSelected(true);
                break;
            case CHANGE_PASSWORD:
                password.setSelected(true);
                break;
            case RESTRICTIONS:
                restrictions.setSelected(true);
                break;
            case FIAT_ACCOUNT:
                fiatAccount.setSelected(true);
                break;
            case REGISTRATION:
                registration.setSelected(true);
                break;
            default:
                log.error(item.getFxmlUrl() + " is invalid");
                break;
        }
    }
}

class MenuItem extends ToggleButton {
    private static final Logger log = LoggerFactory.getLogger(MenuItem.class);

    MenuItem(Navigation navigation, String title, Navigation.Item navigationItem,
             ToggleGroup toggleGroup) {

        setToggleGroup(toggleGroup);
        setText(title);
        setId("account-settings-item-background-active");
        setPrefHeight(40);
        setPrefWidth(200);
        setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label();
        icon.setTextFill(Paint.valueOf("#999"));
        if (navigationItem.equals(Navigation.Item.SEED_WORDS))
            AwesomeDude.setIcon(icon, AwesomeIcon.INFO_SIGN);
        else if (navigationItem.equals(Navigation.Item.REGISTRATION))
            AwesomeDude.setIcon(icon, AwesomeIcon.BRIEFCASE);
        else
            AwesomeDude.setIcon(icon, AwesomeIcon.EDIT_SIGN);

        setGraphic(icon);

        setOnAction((event) -> navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT,
                Navigation.Item.ACCOUNT_SETTINGS, navigationItem));

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

