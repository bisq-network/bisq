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

package io.bitsquare.gui.view.account;

import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.pm.PresentationModel;
import io.bitsquare.gui.pm.account.AccountSettingsPM;
import io.bitsquare.gui.view.CachedCodeBehind;
import io.bitsquare.gui.view.CodeBehind;
import io.bitsquare.gui.view.account.content.ContextAware;
import io.bitsquare.util.BSFXMLLoader;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountSettingsViewCB extends CachedCodeBehind<AccountSettingsPM> {

    private static final Logger log = LoggerFactory.getLogger(AccountSettingsViewCB.class);

    public NavigationItem subMenuNavigationItem;

    public VBox leftVBox;
    public AnchorPane content;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private AccountSettingsViewCB(AccountSettingsPM presentationModel, NavigationController navigationController) {
        super(presentationModel);

        navigationController.addListener(navigationItem -> {
            if (navigationItem.length > 1) {
                NavigationItem subMenuNavigationItem1 = navigationItem[1];
                if (subMenuNavigationItem1.getLevel() == 2) {
                    AccountSettingsViewCB.this.subMenuNavigationItem = subMenuNavigationItem1;
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        ToggleGroup toggleGroup = new ToggleGroup();
        MenuItem seedWords = new MenuItem(this, content, "Wallet seed",
                NavigationItem.SEED_WORDS, toggleGroup);
        MenuItem password = new MenuItem(this, content, "Wallet password",
                NavigationItem.CHANGE_PASSWORD, toggleGroup);
        MenuItem restrictions = new MenuItem(this, content, "Trading restrictions",
                NavigationItem.RESTRICTIONS, toggleGroup);
        MenuItem fiatAccount = new MenuItem(this, content, "Payments account(s)",
                NavigationItem.FIAT_ACCOUNT, toggleGroup);
        MenuItem registration = new MenuItem(this, content, "Renew your account",
                NavigationItem.REGISTRATION, toggleGroup);

        registration.setDisable(true);

        leftVBox.getChildren().addAll(seedWords, password,
                restrictions, fiatAccount, registration);

        if (subMenuNavigationItem == null)
            subMenuNavigationItem = NavigationItem.SEED_WORDS;

        loadView(subMenuNavigationItem);

        switch (subMenuNavigationItem) {
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
                log.error(subMenuNavigationItem.getFxmlUrl() + " is no subMenuNavigationItem");
                break;
        }
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void activate() {
        super.activate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////
}

class MenuItem extends ToggleButton {
    private static final Logger log = LoggerFactory.getLogger(MenuItem.class);

    private CodeBehind<? extends PresentationModel> childController;

    private final AccountSettingsViewCB parentCB;
    private final Parent content;
    private final NavigationItem navigationItem;

    MenuItem(AccountSettingsViewCB parentCB, Parent content, String title, NavigationItem navigationItem,
             ToggleGroup toggleGroup) {
        this.parentCB = parentCB;
        this.content = content;
        this.navigationItem = navigationItem;

        setToggleGroup(toggleGroup);
        setText(title);
        setId("account-settings-item-background-active");
        setPrefHeight(40);
        setPrefWidth(200);
        setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label();
        icon.setTextFill(Paint.valueOf("#999"));
        if (navigationItem.equals(NavigationItem.SEED_WORDS))
            AwesomeDude.setIcon(icon, AwesomeIcon.INFO_SIGN);
        else if (navigationItem.equals(NavigationItem.REGISTRATION))
            AwesomeDude.setIcon(icon, AwesomeIcon.BRIEFCASE);
        else
            AwesomeDude.setIcon(icon, AwesomeIcon.EDIT_SIGN);

        setGraphic(icon);

        setOnAction((event) -> show());

        selectedProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                setId("account-settings-item-background-selected");
                icon.setTextFill(Paint.valueOf("#0096c9"));
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

    void show() {
        final BSFXMLLoader loader = new BSFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            final Pane view = loader.load();
            ((AnchorPane) content).getChildren().setAll(view);
            childController = loader.getController();
            childController.setParentController(parentCB);
            ((ContextAware) childController).useSettingsContext(true);
        } catch (IOException e) {
            log.error("Loading view failed. FxmlUrl = " + navigationItem.getFxmlUrl());
            e.getStackTrace();
        }
    }
}

