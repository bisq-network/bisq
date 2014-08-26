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

package io.bitsquare.gui.components;

import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.ViewController;
import io.bitsquare.storage.Persistence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.control.TabPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * That class caches the already created views in a tab pane.
 * So when switching back to an already opened tab it is faster as no fxml loading is needed anymore.
 */

//TODO remove manual caching as its done now in loader
public class CachingTabPane extends TabPane {
    private static final Logger log = LoggerFactory.getLogger(CachingTabPane.class);

    private final List<TabInfo> tabInfoList = new ArrayList<>();
    private ViewController parentController;
    private Persistence persistence;
    private int selectedTabIndex;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initialize(ViewController parentController, Persistence persistence, String... tabContentFXMLUrls) {
        if (tabContentFXMLUrls.length == 0) {
            throw new IllegalArgumentException("No tabContentFXMLUrls defined");
        }

        this.parentController = parentController;
        this.persistence = persistence;

        for (String tabContentFXMLUrl : tabContentFXMLUrls) tabInfoList.add(new TabInfo(tabContentFXMLUrl));

        getSelectionModel().selectedItemProperty().addListener((observableValue, oldTab, newTab) -> loadView());

        // use parent to read selectedTabIndex
        Object indexObject = persistence.read(parentController, "selectedTabIndex");
        selectedTabIndex = (indexObject == null) ? 0 : (int) indexObject;

        // if selectedTabIndex = 0 the the change listener will not fire so we load it manually
        if (selectedTabIndex == 0) loadView();

        getSelectionModel().select(selectedTabIndex);
    }

    public ViewController loadViewAndGetChildController(String fxmlView) {
        for (int i = 0; i < tabInfoList.size(); i++) {
            if (tabInfoList.get(i).url.equals(fxmlView)) {
                // selection will cause loadView() call
                getSelectionModel().select(i);
                return currentController();
            }
        }
        throw new IllegalArgumentException("fxmlView not defined in tabContentFXMLUrlMap.");
    }

    public void setSelectedTabIndex(int selectedTabIndex) {
        getSelectionModel().select(selectedTabIndex);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void loadView() {
        selectedTabIndex = getSelectionModel().getSelectedIndex();
        TabInfo selectedTabInfo = tabInfoList.get(selectedTabIndex);

        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(selectedTabInfo.url));
        try {
            Node view = loader.load();
            selectedTabInfo.controller = loader.getController();
            getSelectionModel().getSelectedItem().setContent(view);
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        selectedTabInfo.controller.setParentController(parentController);
        // use parent to write selectedTabIndex
        persistence.write(parentController, "selectedTabIndex", selectedTabIndex);
    }

    private ViewController currentController() {
        return tabInfoList.get(selectedTabIndex).controller;
    }
}

class TabInfo {
    ViewController controller;
    final String url;

    TabInfo(String url) {
        this.url = url;
    }
}