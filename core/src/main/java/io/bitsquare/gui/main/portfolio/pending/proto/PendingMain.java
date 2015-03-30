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

package io.bitsquare.gui.main.portfolio.pending.proto;

import io.bitsquare.gui.main.portfolio.pending.PendingTradesOffererAsBuyerView;
import io.bitsquare.gui.util.Layout;

import javafx.application.Application;
import javafx.scene.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingMain extends Application {
    private static final Logger log = LoggerFactory.getLogger(PendingMain.class);

    @Override
    public void start(Stage primaryStage) {
        AnchorPane root = new AnchorPane();
        PendingTradesOffererAsBuyerView pendingTradesOffererAsBuyerView = new PendingTradesOffererAsBuyerView();
        AnchorPane.setLeftAnchor(pendingTradesOffererAsBuyerView, Layout.PADDING_WINDOW);
        AnchorPane.setRightAnchor(pendingTradesOffererAsBuyerView, Layout.PADDING_WINDOW);
        AnchorPane.setTopAnchor(pendingTradesOffererAsBuyerView, Layout.PADDING_WINDOW);
        AnchorPane.setBottomAnchor(pendingTradesOffererAsBuyerView, Layout.PADDING_WINDOW);

        root.getChildren().add(pendingTradesOffererAsBuyerView);

        Scene scene = new Scene(root, 1000, 600);
        scene.getStylesheets().setAll(
                "/io/bitsquare/gui/bitsquare.css",
                "/io/bitsquare/gui/images.css");
        primaryStage.setScene(scene);
        primaryStage.show();

    }
}
