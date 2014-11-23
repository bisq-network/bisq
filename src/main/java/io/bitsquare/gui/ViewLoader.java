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

package io.bitsquare.gui;

import io.bitsquare.locale.BSResources;

import java.net.URL;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import viewfx.view.View;
import viewfx.view.support.guice.GuiceViewFactory;

import javafx.fxml.FXMLLoader;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.util.BuilderFactory;

/**
 * Guice support for fxml controllers
 * Support caching to speed up switches between UI screens.
 */
public class ViewLoader {

    private final Map<URL, View> cache = new HashMap<>();
    private final BuilderFactory builderFactory = new JavaFXBuilderFactory();
    private final GuiceViewFactory controllerFactory;

    @Inject
    public ViewLoader(GuiceViewFactory controllerFactory) {
        this.controllerFactory = controllerFactory;
    }

    public View load(URL url) {
        return load(url, true);
    }

    public View load(URL url, boolean useCaching) {
        if (useCaching && cache.containsKey(url))
            return cache.get(url);

        FXMLLoader loader = new FXMLLoader(url, BSResources.getResourceBundle(), builderFactory, controllerFactory);
        View view = loader.getController();
        cache.put(url, view);
        return view;
    }
}

