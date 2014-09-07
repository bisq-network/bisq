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

package io.bitsquare.gui.util;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.effect.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkState;

public class Transitions {
    private static final Logger log = LoggerFactory.getLogger(Transitions.class);

    public static final int UI_ANIMATION_TIME = 800;

    public static void fadeIn(Node node) {
        fadeIn(node, UI_ANIMATION_TIME);
    }

    public static void fadeIn(Node node, int duration) {
        FadeTransition ft = new FadeTransition(Duration.millis(duration), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    public static Animation fadeOut(Node node) {
        return fadeOut(node, UI_ANIMATION_TIME);
    }

    public static Animation fadeOut(Node node, int duration) {
        FadeTransition ft = new FadeTransition(Duration.millis(duration), node);
        ft.setFromValue(node.getOpacity());
        ft.setToValue(0.0);
        ft.play();
        return ft;
    }

    public static Animation fadeOutAndRemove(Node node) {
        return fadeOutAndRemove(node, UI_ANIMATION_TIME);
    }

    public static Animation fadeOutAndRemove(Node node, int duration) {
        Animation animation = fadeOut(node, duration);
        animation.setOnFinished(actionEvent -> {
            ((Pane) (node.getParent())).getChildren().remove(node);
            Profiler.printMsgWithTime("fadeOutAndRemove");
        });
        return animation;
    }

    public static Timeline blurAndRemove(Node node) {
        return blurAndRemove(node, UI_ANIMATION_TIME);
    }

    public static Timeline blurAndRemove(Node node, int duration) {
        Timeline timeline = blur(node, duration);
        timeline.setOnFinished(actionEvent -> {
            ((Pane) (node.getParent())).getChildren().remove(node);
            Profiler.printMsgWithTime("blurOutAndRemove");
        });
        return timeline;
    }

    public static void blur(Node node) {
        blur(node, UI_ANIMATION_TIME);
    }

    public static Timeline blur(Node node, int duration) {
        GaussianBlur blur = new GaussianBlur(0.0);
        node.setEffect(blur);
        Timeline timeline = new Timeline();
        KeyValue kv = new KeyValue(blur.radiusProperty(), 10.0);
        KeyFrame kf = new KeyFrame(Duration.millis(duration), kv);
        timeline.getKeyFrames().add(kf);
        timeline.play();
        return timeline;
    }

    public static void removeBlur(Node node) {
        GaussianBlur blur = (GaussianBlur) node.getEffect();
        Timeline durationline = new Timeline();
        KeyValue kv = new KeyValue(blur.radiusProperty(), 0.0);
        KeyFrame kf = new KeyFrame(Duration.millis(UI_ANIMATION_TIME), kv);
        durationline.getKeyFrames().add(kf);
        durationline.setOnFinished(actionEvent -> node.setEffect(null));
        durationline.play();
    }

    public static void checkGuiThread() {
        checkState(Platform.isFxApplicationThread());
    }
}
