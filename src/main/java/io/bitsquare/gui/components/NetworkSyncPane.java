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

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

// TODO replace with new notification component from lighthouse/bitcoinJ

public class NetworkSyncPane extends HBox {

    private final ProgressBar networkSyncProgressBar;

    private final Label networkSyncInfoLabel;

    public NetworkSyncPane() {
        networkSyncInfoLabel = new Label();
        networkSyncInfoLabel.setText("Synchronize with network...");
        networkSyncProgressBar = new ProgressBar();
        networkSyncProgressBar.setPrefWidth(200);
        networkSyncProgressBar.setProgress(-1);

        getChildren().addAll(new HSpacer(5), networkSyncProgressBar, networkSyncInfoLabel);
    }

    public void setProgress(double percent) {
        networkSyncProgressBar.setProgress(percent / 100.0);
        networkSyncInfoLabel.setText("Synchronize with network: " + (int) percent + "%");
    }

    public void downloadComplete() {
        networkSyncInfoLabel.setText("Sync with network: Done");
        networkSyncProgressBar.setProgress(1);

        FadeTransition fade = new FadeTransition(Duration.millis(700), this);
        fade.setToValue(0.0);
        fade.setCycleCount(1);
        fade.setInterpolator(Interpolator.EASE_BOTH);
        fade.play();
        fade.setOnFinished(e -> getChildren().clear());
    }
}

class HSpacer extends Pane {
    public HSpacer(double width) {
        setPrefWidth(width);
    }

    @Override
    protected double computePrefWidth(double width) {
        return getPrefWidth();
    }
}

