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

package io.bitsquare.gui.components.dialogs;

import io.bitsquare.gui.OverlayManager;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomPopups {
    private static final Logger log = LoggerFactory.getLogger(CustomPopups.class);

    private final Stage rootStage;
    private final OverlayManager overlayManager;
    private final Stage stage = new Stage();
    private StackPane sceneRootPane;

    public CustomPopups(Stage rootStage, OverlayManager overlayManager) {
        this.rootStage = rootStage;
        this.overlayManager = overlayManager;

        setupStage();
    }

    public void showInfoPopup(String title, String message) {
        BorderPane borderPane = new BorderPane();
        borderPane.setMinWidth(400);
        borderPane.setMinHeight(150);
        borderPane.setMaxWidth(rootStage.getWidth() / 2);
        borderPane.setMaxHeight(rootStage.getHeight() / 2);
        borderPane.setPadding(new Insets(20, 20, 20, 20));
        borderPane.setStyle("-fx-background-color: #ffffff;");
       
        Label titleLabel = new Label(title);
        titleLabel.setMouseTransparent(true);
        BorderPane.setAlignment(titleLabel, Pos.TOP_CENTER);
        borderPane.setTop(titleLabel);
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #333333;");
       
        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setMouseTransparent(true);
        borderPane.setCenter(messageLabel);
        messageLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #000000;");
        messageLabel.setPadding(new Insets(20, 0, 30, 0));

        Button closeButton =  getCloseButton();
        BorderPane.setAlignment(closeButton, Pos.BOTTOM_RIGHT);
        borderPane.setBottom(closeButton);
        
        show(borderPane);
    }

    private void setupStage() {
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.initOwner(rootStage);

        sceneRootPane = new StackPane();
        Scene scene = new Scene(sceneRootPane);
        scene.getStylesheets().setAll(getClass().getResource("/io/bitsquare/gui/bitsquare.css").toExternalForm(),
                getClass().getResource("/io/bitsquare/gui/images.css").toExternalForm());
        stage.setScene(scene);
    }

    private void hide() {
        stage.hide();
    }

    private void show(Pane pane) {
        sceneRootPane.getChildren().setAll(pane);
        stage.show();
    }

    private Button getCloseButton() {
        Button closeButton = new Button("Close");
        closeButton.setDefaultButton(true);
        closeButton.setOnAction(e -> hide());
        return closeButton;
    }
}
