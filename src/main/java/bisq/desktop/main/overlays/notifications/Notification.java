/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.overlays.notifications;

import bisq.desktop.main.overlays.Overlay;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.locale.Res;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;

import javafx.stage.Modality;
import javafx.stage.Window;

import javafx.scene.Camera;
import javafx.scene.PerspectiveCamera;
import javafx.scene.transform.Rotate;

import javafx.geometry.Insets;

import javafx.collections.ObservableList;

import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Notification extends Overlay<Notification> {
    private static final Logger log = LoggerFactory.getLogger(Notification.class);
    private boolean hasBeenDisplayed;
    private boolean autoClose;
    private Timer autoCloseTimer;

    public Notification() {
        width = 345; // 320 visible bg because of insets
        NotificationCenter.add(this);
        type = Type.Notification;
    }

    public void onReadyForDisplay() {
        super.display();
        if (autoClose && autoCloseTimer == null)
            autoCloseTimer = UserThread.runAfter(this::doClose, 4);
    }

    @Override
    public void hide() {
        animateHide(() -> {
            removeEffectFromBackground();

            if (stage != null)
                stage.hide();
            else
                log.warn("Stage is null");

            cleanup();
            onHidden();
        });
    }

    @Override
    protected void onShow() {
        NotificationManager.queueForDisplay(this);
    }

    @Override
    protected void onHidden() {
        NotificationManager.onHidden(this);
    }

    public Notification tradeHeadLine(String tradeId) {
        return headLine(Res.get("notification.trade.headline", tradeId));
    }

    public Notification disputeHeadLine(String tradeId) {
        return headLine(Res.get("notification.ticket.headline", tradeId));
    }

    @Override
    protected void addSeparator() {
        // dont show a separator
    }

    @Override
    public void show() {
        super.show();
        hasBeenDisplayed = true;
    }


    public Notification autoClose() {
        autoClose = true;
        return this;
    }

    @Override
    protected void animateHide(Runnable onFinishedHandler) {
        if (autoCloseTimer != null) {
            autoCloseTimer.stop();
            autoCloseTimer = null;
        }

        if (NotificationCenter.useAnimations) {
            double duration = getDuration(400);
            Interpolator interpolator = Interpolator.SPLINE(0.25, 0.1, 0.25, 1);

            gridPane.setRotationAxis(Rotate.X_AXIS);
            Camera camera = gridPane.getScene().getCamera();
            gridPane.getScene().setCamera(new PerspectiveCamera());

            Timeline timeline = new Timeline();
            ObservableList<KeyFrame> keyFrames = timeline.getKeyFrames();
            keyFrames.add(new KeyFrame(Duration.millis(0),
                    new KeyValue(gridPane.rotateProperty(), 0, interpolator),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator)
            ));
            keyFrames.add(new KeyFrame(Duration.millis(duration),
                    new KeyValue(gridPane.rotateProperty(), -90, interpolator),
                    new KeyValue(gridPane.opacityProperty(), 0, interpolator)
            ));
            timeline.setOnFinished(event -> {
                gridPane.setRotate(0);
                gridPane.setRotationAxis(Rotate.Z_AXIS);
                gridPane.getScene().setCamera(camera);
                onFinishedHandler.run();
            });
            timeline.play();
        } else {
            onFinishedHandler.run();
        }
    }

    @Override
    protected void animateDisplay() {
        if (NotificationCenter.useAnimations) {
            double startX = 320;
            double duration = getDuration(600);
            Interpolator interpolator = Interpolator.SPLINE(0.25, 0.1, 0.25, 1);

            Timeline timeline = new Timeline();
            ObservableList<KeyFrame> keyFrames = timeline.getKeyFrames();
            keyFrames.add(new KeyFrame(Duration.millis(0),
                    new KeyValue(gridPane.opacityProperty(), 0, interpolator),
                    new KeyValue(gridPane.translateXProperty(), startX, interpolator)
            ));
            //bouncing
         /*   keyFrames.add(new KeyFrame(Duration.millis(duration * 0.6),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator),
                    new KeyValue(gridPane.translateXProperty(), -12, interpolator)
            ));
            keyFrames.add(new KeyFrame(Duration.millis(duration * 0.8),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator),
                    new KeyValue(gridPane.translateXProperty(), 4, interpolator)
            ));*/
            keyFrames.add(new KeyFrame(Duration.millis(duration),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator),
                    new KeyValue(gridPane.translateXProperty(), 0, interpolator)
            ));

            timeline.play();
        }
    }


    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setPadding(new Insets(15, 15, 30, 30));
    }

    @Override
    protected void addCloseButton() {
        buttonDistance = 10;
        super.addCloseButton();
    }

    @Override
    protected void applyStyles() {
        gridPane.setId("notification-popup-bg");
        if (headLineLabel != null)
            headLineLabel.setId("notification-popup-headline");
    }

    @Override
    protected void setModality() {
        stage.initOwner(owner.getScene().getWindow());
        stage.initModality(Modality.NONE);
    }

    @Override
    protected void layout() {
        Window window = owner.getScene().getWindow();
        double titleBarHeight = window.getHeight() - owner.getScene().getHeight();
        stage.setX(Math.round(window.getX() + window.getWidth() - stage.getWidth()));
        stage.setY(Math.round(window.getY() + titleBarHeight));
    }

    @Override
    protected void addEffectToBackground() {
    }

    @Override
    protected void removeEffectFromBackground() {
    }

   /* @Override
    protected void addCloseButton() {
        closeButton = new AutoTooltipButton(Res.get("shared.close"));
        closeButton.setOnAction(event -> {
            hide();
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
        });
        GridPane.setHalignment(closeButton, HPos.RIGHT);
        GridPane.setRowIndex(closeButton, ++rowIndex);
        GridPane.setColumnIndex(closeButton, 1);
        gridPane.getChildren().add(closeButton);
    }*/

    public boolean isHasBeenDisplayed() {
        return hasBeenDisplayed;
    }
}
