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

import io.bitsquare.BitsquareException;
import io.bitsquare.locale.BSResources;

import java.io.IOException;

import java.net.URL;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.*;
import javafx.util.BuilderFactory;

/**
 * Guice support for fxml controllers
 * Support caching to speed up switches between UI screens.
 */
public class ViewLoader {

    private final Map<URL, Item> cache = new HashMap<>();
    private final BuilderFactory builderFactory = new JavaFXBuilderFactory();
    private final GuiceControllerFactory controllerFactory;

    @Inject
    public ViewLoader(GuiceControllerFactory controllerFactory) {
        this.controllerFactory = controllerFactory;
    }

    public Item load(URL url) {
        return load(url, true);
    }

    public Item load(URL url, boolean useCaching) {
        Item item;

        if (useCaching && cache.containsKey(url)) {
            return cache.get(url);
        }

        FXMLLoader loader = new FXMLLoader(url, BSResources.getResourceBundle(), builderFactory, controllerFactory);
        try {
            item = new Item(loader.load(), loader.getController());
            cache.put(url, item);
            return item;
        } catch (IOException e) {
            throw new BitsquareException(e, "Failed to load view at %s", url);
        }
    }

    public static class Item {
        public final Node view;
        public final Initializable controller;

        Item(Node view, Initializable controller) {
            this.view = view;
            this.controller = controller;
        }
    }
}

