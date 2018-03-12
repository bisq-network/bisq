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

package bisq.desktop.main.settings;

import bisq.desktop.Navigation;
import bisq.desktop.common.model.Activatable;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.main.MainView;
import bisq.desktop.main.settings.about.AboutView;
import bisq.desktop.main.settings.network.NetworkSettingsView;
import bisq.desktop.main.settings.preferences.PreferencesView;

import bisq.common.locale.Res;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import javafx.beans.value.ChangeListener;

@FxmlView
public class SettingsView extends ActivatableViewAndModel<TabPane, Activatable> {
    @FXML
    Tab preferencesTab, networkTab, aboutTab;
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
        preferencesTab.setText(Res.get("settings.tab.preferences"));
        networkTab.setText(Res.get("settings.tab.network"));
        aboutTab.setText(Res.get("settings.tab.about"));

        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(SettingsView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == preferencesTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class);
            else if (newValue == networkTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, SettingsView.class, NetworkSettingsView.class);
            else if (newValue == aboutTab)
                //noinspection unchecked
                navigation.navigateTo(MainView.class, SettingsView.class, AboutView.class);
        };
    }

    @Override
    protected void activate() {
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        Tab selectedItem = root.getSelectionModel().getSelectedItem();
        if (selectedItem == preferencesTab)
            //noinspection unchecked
            navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class);
        else if (selectedItem == networkTab)
            //noinspection unchecked
            navigation.navigateTo(MainView.class, SettingsView.class, NetworkSettingsView.class);
        else if (selectedItem == aboutTab)
            //noinspection unchecked
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
        else if (view instanceof NetworkSettingsView) tab = networkTab;
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

