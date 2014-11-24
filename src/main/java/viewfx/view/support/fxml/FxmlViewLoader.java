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

package viewfx.view.support.fxml;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import viewfx.ViewfxException;
import viewfx.view.FxmlView;
import viewfx.view.View;
import viewfx.view.ViewFactory;
import viewfx.view.ViewLoader;

import javafx.fxml.FXMLLoader;

import org.springframework.core.annotation.AnnotationUtils;

public class FxmlViewLoader implements ViewLoader {

    private final ViewFactory viewFactory;
    private final ResourceBundle resourceBundle;

    @Inject
    public FxmlViewLoader(ViewFactory viewFactory, ResourceBundle resourceBundle) {
        this.viewFactory = viewFactory;
        this.resourceBundle = resourceBundle;
    }

    public View load(Class<?> clazz) {
        if (!View.class.isAssignableFrom(clazz))
            throw new IllegalArgumentException("Class must be of generic type Class<? extends View>: " + clazz);

        @SuppressWarnings("unchecked")
        Class<? extends View> viewClass = (Class<? extends View>) clazz;

        FxmlView fxmlView = AnnotationUtils.getAnnotation(viewClass, FxmlView.class);
        try {
            String path = fxmlView.convention().newInstance().apply(viewClass);
            return load(viewClass.getClassLoader().getResource(path));
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new ViewfxException(ex, "Failed to load View from class %s", viewClass);
        }
    }

    public View load(URL url) {
        try {
            FXMLLoader loader = new FXMLLoader(url, resourceBundle);
            loader.setControllerFactory(viewFactory);
            loader.load();
            return loader.getController();
        } catch (IOException ex) {
            throw new ViewfxException(ex, "Failed to load View at location %s", url);
        }
    }

    public View load(Object location) {
        if (location instanceof URL)
            return load((URL) location);
        if (location instanceof Class<?>)
            return load((Class) location);

        throw new IllegalArgumentException("Argument is not of type URL or Class: " + location);

    }
}

