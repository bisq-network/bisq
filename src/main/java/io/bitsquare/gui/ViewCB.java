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

import java.net.URL;

import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Non caching version for code behind classes using the PM pattern
 *
 * @param <T> The PresentationModel used in that class
 */
public class ViewCB<T extends PresentationModel> implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(ViewCB.class);

    public static final String TITLE_KEY = "view.title";

    protected T presentationModel;
    protected Initializable childController;
    protected ViewCB<? extends PresentationModel> parent;

    @FXML protected Parent root;

    protected ViewCB(T presentationModel) {
        this.presentationModel = presentationModel;
    }

    protected ViewCB() {
    }

    /**
     * Get called form GUI framework when the UI is ready.
     *
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        log.trace("Lifecycle: initialize " + this.getClass().getSimpleName());
        if (root != null) {
            root.sceneProperty().addListener((ov, oldValue, newValue) -> {
                // we got removed from the scene
                // lets terminate
                if (oldValue != null && newValue == null)
                    terminate();
            });
        }

        if (presentationModel != null)
            presentationModel.initialize();
    }

    /**
     * Called automatically when view gets removed. Used for house keeping (removing listeners,
     * stopping timers or animations,...).
     */
    public void terminate() {
        log.trace("Lifecycle: terminate " + this.getClass().getSimpleName());

        if (presentationModel != null)
            presentationModel.terminate();
    }

    /**
     * @param parent Controller who has created this.getClass().getSimpleName() instance (via
     *               navigateToView/FXMLLoader).
     */
    public void setParent(ViewCB<? extends PresentationModel> parent) {
        log.trace("Lifecycle: setParentController " + this.getClass().getSimpleName() + " / parent = " +
                parent);
        this.parent = parent;
    }

    /**
     * @param navigationItem NavigationItem to be loaded.
     * @return The ViewController of the loaded view.
     */
    protected Initializable loadView(Navigation.Item navigationItem) {
        log.trace("Lifecycle: loadViewAndGetChildController " + this.getClass().getSimpleName() + " / navigationItem " +
                "= " + navigationItem);
        return null;
    }
}
