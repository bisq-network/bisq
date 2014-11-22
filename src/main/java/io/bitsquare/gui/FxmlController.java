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
import javafx.scene.*;

public abstract class FxmlController<R extends Node, M extends XModel> extends Controller<M> {

    protected @FXML R root;

    protected FxmlController(M model) {
        super(model);
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        root.sceneProperty().addListener((ov, oldValue, newValue) -> {
            // root node has been removed the scene
            if (oldValue != null && newValue == null)
                terminate();
        });
        super.initialize(url, rb);
    }
}
