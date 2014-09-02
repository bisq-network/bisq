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

package io.bitsquare.gui.registration.uimock;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.event.EventHandler;
import javafx.fxml.Initializable;
import javafx.scene.input.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegistrationControllerUIMock implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(RegistrationControllerUIMock.class);
    public HBox prefBox;
    public Pane content;


    @Inject
    private RegistrationControllerUIMock() {
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        prefBox.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                content.getChildren().remove(0);

            }
        });

    }


}

