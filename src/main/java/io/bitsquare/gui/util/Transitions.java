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

import io.bitsquare.preferences.ApplicationPreferences;

import javax.inject.Inject;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
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

public class Transitions {
    private static final Logger log = LoggerFactory.getLogger(Transitions.class);

    public final static int DEFAULT_DURATION = 400;

    private ApplicationPreferences settings;
    private Timeline removeBlurTimeLine;

    @Inject
    public Transitions(ApplicationPreferences settings) {
        this.settings = settings;
    }

    private int evaluateDuration(int duration) {
        return settings.getUseAnimations() ? duration : 1;
    }

    // Fade
    public void fadeIn(Node node) {
        fadeIn(node, DEFAULT_DURATION);
    }

    public void fadeIn(Node node, int duration) {
        if (settings.getUseEffects()) {
            FadeTransition fade = new FadeTransition(Duration.millis(evaluateDuration(duration)), node);
            fade.setFromValue(node.getOpacity());
            fade.setToValue(1.0);
            fade.play();
        }
    }

    public FadeTransition fadeOut(Node node) {
        return fadeOut(node, DEFAULT_DURATION);
    }

    public FadeTransition fadeOut(Node node, int duration) {
        if (!settings.getUseEffects())
            duration = 1;

        FadeTransition fade = new FadeTransition(Duration.millis(evaluateDuration(duration)), node);
        fade.setFromValue(node.getOpacity());
        fade.setToValue(0.0);
        fade.play();
        return fade;
    }

    public void fadeOutAndRemove(Node node) {
        fadeOutAndRemove(node, DEFAULT_DURATION);
    }

    public void fadeOutAndRemove(Node node, int duration) {
        if (!settings.getUseEffects())
            duration = 1;

        FadeTransition fade = fadeOut(node, evaluateDuration(duration));
        fade.setInterpolator(Interpolator.EASE_IN);
        fade.setOnFinished(actionEvent -> {
            ((Pane) (node.getParent())).getChildren().remove(node);
            Profiler.printMsgWithTime("fadeOutAndRemove");
        });
    }

    // Blur
    public void blur(Node node) {
        blur(node, DEFAULT_DURATION, true, false);
    }

    public void blur(Node node, int duration, boolean useDarken, boolean removeNode) {
        if (settings.getUseEffects()) {
            if (removeBlurTimeLine != null)
                removeBlurTimeLine.stop();

            GaussianBlur blur = new GaussianBlur(0.0);
            Timeline timeline = new Timeline();
            KeyValue kv1 = new KeyValue(blur.radiusProperty(), 15.0);
            KeyFrame kf1 = new KeyFrame(Duration.millis(evaluateDuration(duration)), kv1);

            if (useDarken) {
                ColorAdjust darken = new ColorAdjust();
                darken.setBrightness(0.0);
                blur.setInput(darken);

                KeyValue kv2 = new KeyValue(darken.brightnessProperty(), -0.1);
                KeyFrame kf2 = new KeyFrame(Duration.millis(evaluateDuration(duration)), kv2);
                timeline.getKeyFrames().addAll(kf1, kf2);
            }
            else {
                timeline.getKeyFrames().addAll(kf1);
            }
            node.setEffect(blur);
            if (removeNode) timeline.setOnFinished(actionEvent -> Platform.runLater(() -> ((Pane) (node.getParent()))
                    .getChildren().remove(node)));
            timeline.play();
        }
    }

    public void removeBlur(Node node) {
        removeBlur(node, DEFAULT_DURATION, false);
    }

    public void removeBlur(Node node, int duration, boolean useDarken) {
        if (settings.getUseEffects()) {
            if (node != null) {
                GaussianBlur blur = (GaussianBlur) node.getEffect();
                if (blur != null) {
                    removeBlurTimeLine = new Timeline();
                    KeyValue kv1 = new KeyValue(blur.radiusProperty(), 0.0);
                    KeyFrame kf1 = new KeyFrame(Duration.millis(evaluateDuration(duration)), kv1);


                    if (useDarken) {
                        ColorAdjust darken = (ColorAdjust) blur.getInput();

                        KeyValue kv2 = new KeyValue(darken.brightnessProperty(), 0.0);
                        KeyFrame kf2 = new KeyFrame(Duration.millis(evaluateDuration(duration)), kv2);
                        removeBlurTimeLine.getKeyFrames().addAll(kf1, kf2);
                    }
                    else {
                        removeBlurTimeLine.getKeyFrames().addAll(kf1);
                    }

                    removeBlurTimeLine.setOnFinished(actionEvent -> {
                        node.setEffect(null);
                        removeBlurTimeLine = null;
                    });
                    removeBlurTimeLine.play();
                }
            }
        }
    }
}
