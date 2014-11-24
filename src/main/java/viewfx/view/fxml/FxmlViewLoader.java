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

package viewfx.view.fxml;

import java.io.IOException;

import java.net.URL;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import javax.inject.Inject;

import viewfx.view.View;
import viewfx.view.ViewFactory;
import viewfx.view.ViewLoader;

import javafx.fxml.FXMLLoader;

public class FxmlViewLoader implements ViewLoader<URL> {

    private final Map<URL, View> cache = new HashMap<>();
    private final ViewFactory viewFactory;
    private final ResourceBundle resourceBundle;

    @Inject
    public FxmlViewLoader(ViewFactory viewFactory, ResourceBundle resourceBundle) {
        this.viewFactory = viewFactory;
        this.resourceBundle = resourceBundle;
    }

    @Override
    public View load(URL url) {
        return load(url, true);
    }

    public View load(URL url, boolean useCaching) {
        if (useCaching && cache.containsKey(url))
            return cache.get(url);

        try {
            FXMLLoader loader = new FXMLLoader(url, resourceBundle);
            loader.setControllerFactory(viewFactory);
            loader.load();
            View view = loader.getController();
            cache.put(url, view);
            return view;
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load View at location " + url, ex);
        }
    }
}

