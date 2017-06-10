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

import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.view.*;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.settings.about.AboutView;
import io.bitsquare.gui.main.settings.network.NetworkSettingsView;
import io.bitsquare.gui.main.settings.preferences.PreferencesView;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;

import javax.inject.Inject;

@FxmlView
public class SettingsView extends ActivatableViewAndModel<TabPane, Activatable> {
    @FXML
    TabPane root;
    @FXML
    Tab preferencesTab, networkSettingsTab, aboutTab;
    @FXML
    ScrollPane prefScroll, networkScroll, aboutScroll;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    @Inject
    public SettingsView(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        AnchorPane.setTopAnchor(root, MainView.scale(0));
        AnchorPane.setRightAnchor(root, MainView.scale(0));
        AnchorPane.setBottomAnchor(root, MainView.scale(0));
        AnchorPane.setLeftAnchor(root, MainView.scale(0));
        AnchorPane.setTopAnchor(prefScroll, MainView.scale(0));
        AnchorPane.setRightAnchor(prefScroll, MainView.scale(0));
        AnchorPane.setBottomAnchor(prefScroll, MainView.scale(0));
        AnchorPane.setLeftAnchor(prefScroll, MainView.scale(0));
        AnchorPane.setTopAnchor(networkScroll, MainView.scale(0));
        AnchorPane.setRightAnchor(networkScroll, MainView.scale(0));
        AnchorPane.setBottomAnchor(networkScroll, MainView.scale(0));
        AnchorPane.setLeftAnchor(networkScroll, MainView.scale(0));
        AnchorPane.setTopAnchor(aboutScroll, MainView.scale(0));
        AnchorPane.setRightAnchor(aboutScroll, MainView.scale(0));
        AnchorPane.setBottomAnchor(aboutScroll, MainView.scale(0));
        AnchorPane.setLeftAnchor(aboutScroll, MainView.scale(0));
        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(SettingsView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == preferencesTab)
                navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class);
            else if (newValue == networkSettingsTab)
                navigation.navigateTo(MainView.class, SettingsView.class, NetworkSettingsView.class);
            else if (newValue == aboutTab)
                navigation.navigateTo(MainView.class, SettingsView.class, AboutView.class);
        };
    }

    @Override
    protected void activate() {
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        Tab selectedItem = root.getSelectionModel().getSelectedItem();
        if (selectedItem == preferencesTab)
            navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class);
        else if (selectedItem == networkSettingsTab)
            navigation.navigateTo(MainView.class, SettingsView.class, NetworkSettingsView.class);
        else if (selectedItem == aboutTab)
            navigation.navigateTo(MainView.class, SettingsView.class, AboutView.class);
    }

    @Override
    protected void deactivate() {
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);
    }

    private void loadView(Class<? extends View> viewClass) {
        final Tab tab;
        View view = viewLoader.load(viewClass);

        if (view instanceof PreferencesView) tab = preferencesTab;
        else if (view instanceof NetworkSettingsView) tab = networkSettingsTab;
        else if (view instanceof AboutView) tab = aboutTab;
        else throw new IllegalArgumentException("Navigation to " + viewClass + " is not supported");

        if (tab.getContent() != null && tab.getContent() instanceof ScrollPane) {
            ((ScrollPane) tab.getContent()).setContent(view.getRoot());
        } else {
            tab.setContent(view.getRoot());
        }
        root.getSelectionModel().select(tab);
    }
}

