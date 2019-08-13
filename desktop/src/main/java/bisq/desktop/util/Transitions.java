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

package bisq.desktop.util;

import bisq.core.user.Preferences;

import bisq.common.UserThread;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;

import javafx.scene.Node;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

import javafx.util.Duration;

@Singleton
public class Transitions {

    public final static int DEFAULT_DURATION = 600;

    private final Preferences preferences;
    private Timeline removeEffectTimeLine;

    @Inject
    public Transitions(Preferences preferences) {
        this.preferences = preferences;
    }

    private int getDuration(int duration) {
        return preferences.isUseAnimations() ? duration : 1;
    }

    // Fade
    public void fadeIn(Node node) {
        fadeIn(node, DEFAULT_DURATION);
    }

    public void fadeIn(Node node, int duration) {
        FadeTransition fade = new FadeTransition(Duration.millis(getDuration(duration)), node);
        fade.setFromValue(node.getOpacity());
        fade.setToValue(1.0);
        fade.play();
    }

    public FadeTransition fadeOut(Node node) {
        return fadeOut(node, DEFAULT_DURATION);
    }

    private FadeTransition fadeOut(Node node, int duration) {
        FadeTransition fade = new FadeTransition(Duration.millis(getDuration(duration)), node);
        fade.setFromValue(node.getOpacity());
        fade.setToValue(0.0);
        fade.play();
        return fade;
    }

    public void fadeOutAndRemove(Node node) {
        fadeOutAndRemove(node, DEFAULT_DURATION);
    }

    public void fadeOutAndRemove(Node node, int duration) {
        fadeOutAndRemove(node, duration, null);
    }

    public void fadeOutAndRemove(Node node, int duration, EventHandler<ActionEvent> handler) {
        FadeTransition fade = fadeOut(node, getDuration(duration));
        fade.setInterpolator(Interpolator.EASE_IN);
        fade.setOnFinished(actionEvent -> {
            ((Pane) (node.getParent())).getChildren().remove(node);
            //Profiler.printMsgWithTime("fadeOutAndRemove");
            if (handler != null)
                handler.handle(actionEvent);
        });
    }

    // Blur
    public void blur(Node node) {
        blur(node, DEFAULT_DURATION, -0.1, false, 15);
    }

    public void blur(Node node, int duration, double brightness, boolean removeNode, double blurRadius) {
        if (removeEffectTimeLine != null)
            removeEffectTimeLine.stop();

        node.setMouseTransparent(true);
        GaussianBlur blur = new GaussianBlur(0.0);
        Timeline timeline = new Timeline();
        KeyValue kv1 = new KeyValue(blur.radiusProperty(), blurRadius);
        KeyFrame kf1 = new KeyFrame(Duration.millis(getDuration(duration)), kv1);
        ColorAdjust darken = new ColorAdjust();
        darken.setBrightness(0.0);
        blur.setInput(darken);
        KeyValue kv2 = new KeyValue(darken.brightnessProperty(), brightness);
        KeyFrame kf2 = new KeyFrame(Duration.millis(getDuration(duration)), kv2);
        timeline.getKeyFrames().addAll(kf1, kf2);
        node.setEffect(blur);
        if (removeNode) timeline.setOnFinished(actionEvent -> UserThread.execute(() -> ((Pane) (node.getParent()))
                .getChildren().remove(node)));
        timeline.play();
    }

    // Darken
    public void darken(Node node, int duration, boolean removeNode) {
        blur(node, duration, -0.2, removeNode, 0);
    }

    public void removeEffect(Node node) {
        removeEffect(node, DEFAULT_DURATION);
    }

    private void removeEffect(Node node, int duration) {
        if (node != null) {
            node.setMouseTransparent(false);
            removeEffectTimeLine = new Timeline();
            GaussianBlur blur = (GaussianBlur) node.getEffect();
            if (blur != null) {
                KeyValue kv1 = new KeyValue(blur.radiusProperty(), 0.0);
                KeyFrame kf1 = new KeyFrame(Duration.millis(getDuration(duration)), kv1);
                removeEffectTimeLine.getKeyFrames().add(kf1);

                ColorAdjust darken = (ColorAdjust) blur.getInput();
                KeyValue kv2 = new KeyValue(darken.brightnessProperty(), 0.0);
                KeyFrame kf2 = new KeyFrame(Duration.millis(getDuration(duration)), kv2);
                removeEffectTimeLine.getKeyFrames().add(kf2);
                removeEffectTimeLine.setOnFinished(actionEvent -> {
                    node.setEffect(null);
                    removeEffectTimeLine = null;
                });
                removeEffectTimeLine.play();
            } else {
                node.setEffect(null);
                removeEffectTimeLine = null;
            }
        }
    }
}
