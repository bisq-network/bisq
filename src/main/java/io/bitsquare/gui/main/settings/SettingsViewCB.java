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

package io.bitsquare.gui.main.settings;

import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.gui.ViewLoader;
import io.bitsquare.settings.Preferences;

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

public class SettingsViewCB extends CachedViewCB {
    private static final Logger log = LoggerFactory.getLogger(SettingsViewCB.class);

    private final Navigation navigation;
    private Preferences preferences;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    @FXML Tab preferencesTab, networkSettingsTab;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    SettingsViewCB(Navigation navigation, Preferences preferences) {
        super();

        this.navigation = navigation;
        this.preferences = preferences;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        navigationListener = navigationItems -> {
            if (navigationItems != null && navigationItems.length == 3
                    && navigationItems[1] == Navigation.Item.SETTINGS)
                loadView(navigationItems[2]);
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == preferencesTab)
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.SETTINGS,
                        Navigation.Item.PREFERENCES);
            else if (newValue == networkSettingsTab)
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.SETTINGS,
                        Navigation.Item.NETWORK_SETTINGS);
        };

        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        ((TabPane) root).getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (((TabPane) root).getSelectionModel().getSelectedItem() == preferencesTab)
            navigation.navigationTo(Navigation.Item.MAIN,
                    Navigation.Item.SETTINGS,
                    Navigation.Item.PREFERENCES);
        else
            navigation.navigationTo(Navigation.Item.MAIN,
                    Navigation.Item.SETTINGS,
                    Navigation.Item.NETWORK_SETTINGS);
    }

    @Override
    public void deactivate() {
        super.deactivate();

        ((TabPane) root).getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);
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
            case PREFERENCES:
                tab = preferencesTab;
                break;
            case NETWORK_SETTINGS:
                tab = networkSettingsTab;
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

