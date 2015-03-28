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

import io.bitsquare.common.viewfx.model.Activatable;
import io.bitsquare.common.viewfx.view.ActivatableViewAndModel;
import io.bitsquare.common.viewfx.view.CachingViewLoader;
import io.bitsquare.common.viewfx.view.FxmlView;
import io.bitsquare.common.viewfx.view.View;
import io.bitsquare.common.viewfx.view.ViewLoader;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.settings.application.PreferencesView;
import io.bitsquare.gui.main.settings.network.NetworkSettingsView;

import javax.inject.Inject;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;

@FxmlView
public class SettingsView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML Tab preferencesTab, networkSettingsTab;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    @Inject
    public SettingsView(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(SettingsView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == preferencesTab)
                navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class);
            else if (newValue == networkSettingsTab)
                navigation.navigateTo(MainView.class, SettingsView.class, NetworkSettingsView.class);
        };
    }

    @Override
    public void doActivate() {
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (root.getSelectionModel().getSelectedItem() == preferencesTab)
            navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class);
        else
            navigation.navigateTo(MainView.class, SettingsView.class, NetworkSettingsView.class);
    }

    @Override
    public void doDeactivate() {
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);
    }

    private void loadView(Class<? extends View> viewClass) {
        final Tab tab;
        View view = viewLoader.load(viewClass);

        if (view instanceof PreferencesView) tab = preferencesTab;
        else if (view instanceof NetworkSettingsView) tab = networkSettingsTab;
        else throw new IllegalArgumentException("Navigation to " + viewClass + " is not supported");

        tab.setContent(view.getRoot());
        root.getSelectionModel().select(tab);
    }
}

