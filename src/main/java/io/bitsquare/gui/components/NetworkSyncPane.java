package io.bitsquare.gui.components;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

public class NetworkSyncPane extends HBox
{

    private final ProgressBar networkSyncProgressBar;

    private final Label networkSyncInfoLabel;

    public NetworkSyncPane()
    {
        networkSyncInfoLabel = new Label();
        networkSyncInfoLabel.setText("Synchronize with network...");
        networkSyncProgressBar = new ProgressBar();
        networkSyncProgressBar.setPrefWidth(200);
        networkSyncProgressBar.setProgress(-1);

        getChildren().addAll(new HSpacer(5), networkSyncProgressBar, networkSyncInfoLabel);
    }

    public void setProgress(double percent)
    {
        networkSyncProgressBar.setProgress(percent / 100.0);
        networkSyncInfoLabel.setText("Synchronize with network: " + (int) percent + "%");
    }

    public void downloadComplete()
    {
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
