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

package io.bitsquare.gui.main.preferences;

import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.gui.ViewLoader;
import io.bitsquare.preferences.ApplicationPreferences;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.*;
import javafx.scene.control.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreferencesViewCB extends CachedViewCB {
    private static final Logger log = LoggerFactory.getLogger(PreferencesViewCB.class);

    private final Navigation navigation;
    private ApplicationPreferences applicationPreferences;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    @FXML Tab applicationTab, networkTab;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    PreferencesViewCB(Navigation navigation, ApplicationPreferences applicationPreferences) {
        super();

        this.navigation = navigation;
        this.applicationPreferences = applicationPreferences;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        navigationListener = navigationItems -> {
            if (navigationItems != null && navigationItems.length == 3
                    && navigationItems[1] == Navigation.Item.PREFERENCES)
                loadView(navigationItems[2]);
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == applicationTab)
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.PREFERENCES,
                        Navigation.Item.APPLICATION_PREFERENCES);
            else if (newValue == networkTab)
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.PREFERENCES,
                        Navigation.Item.NETWORK_PREFERENCES);
        };

        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        ((TabPane) root).getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (((TabPane) root).getSelectionModel().getSelectedItem() == applicationTab)
            navigation.navigationTo(Navigation.Item.MAIN,
                    Navigation.Item.PREFERENCES,
                    Navigation.Item.APPLICATION_PREFERENCES);
        else
            navigation.navigationTo(Navigation.Item.MAIN,
                    Navigation.Item.PREFERENCES,
                    Navigation.Item.NETWORK_PREFERENCES);
    }

    @Override
    public void deactivate() {
        super.deactivate();

        ((TabPane) root).getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected Initializable loadView(Navigation.Item navigationItem) {
        super.loadView(navigationItem);

        final ViewLoader loader = new ViewLoader(navigationItem);
        Parent view = loader.load();
        Tab tab = null;
        switch (navigationItem) {
            case APPLICATION_PREFERENCES:
                tab = applicationTab;
                break;
            case NETWORK_PREFERENCES:
                tab = networkTab;
                break;
        }
        tab.setContent(view);
        ((TabPane) root).getSelectionModel().select(tab);
        Initializable childController = loader.getController();
        if (childController instanceof ViewCB)
            ((ViewCB) childController).setParent(this);

        return childController;
    }

}

