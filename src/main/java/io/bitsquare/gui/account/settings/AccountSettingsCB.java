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

package io.bitsquare.gui.account.settings;

import io.bitsquare.gui.CachedCodeBehind;
import io.bitsquare.gui.CodeBehind;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.PresentationModel;
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

public class AccountSettingsCB extends CachedCodeBehind<AccountSettingsPM> {

    private static final Logger log = LoggerFactory.getLogger(AccountSettingsCB.class);
    public VBox leftVBox;
    public AnchorPane content;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AccountSettingsCB(AccountSettingsPM presentationModel) {
        super(presentationModel);
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

        seedWords.fire();
    }

    @Override
    public void activate() {
        super.activate();
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

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
    private final AccountSettingsCB parentCB;
    private final Parent content;
    private final NavigationItem navigationItem;

    MenuItem(AccountSettingsCB parentCB, Parent content, String title, NavigationItem navigationItem,
             ToggleGroup toggleGroup) {
        this.parentCB = parentCB;
        this.content = content;
        this.navigationItem = navigationItem;

        setPrefHeight(40);
        setPrefWidth(200);
        setAlignment(Pos.CENTER_LEFT);

        setToggleGroup(toggleGroup);

        setText(title);
        setId("account-settings-item-background-active");

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
                //icon.setTextFill(Paint.valueOf("#ccc"));
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
        } catch (IOException e) {
            log.error("Loading view failed. FxmlUrl = " + navigationItem.getFxmlUrl());
            e.getStackTrace();
        }
    }


    CodeBehind<? extends PresentationModel> getChildController() {
        return childController;
    }
}

