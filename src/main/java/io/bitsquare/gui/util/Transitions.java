package io.bitsquare.gui.util;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Preconditions.checkState;

@SuppressWarnings("WeakerAccess")
public class Transitions
{

    public static final int UI_ANIMATION_TIME = 800;

    public static void fadeIn(Node ui)
    {
        fadeIn(ui, UI_ANIMATION_TIME);
    }

    @SuppressWarnings("SameParameterValue")
    public static void fadeIn(Node ui, int time)
    {
        @NotNull FadeTransition ft = new FadeTransition(Duration.millis(time), ui);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    @NotNull
    public static Animation fadeOut(@NotNull Node ui)
    {
        @NotNull FadeTransition ft = new FadeTransition(Duration.millis(UI_ANIMATION_TIME), ui);
        ft.setFromValue(ui.getOpacity());
        ft.setToValue(0.0);
        ft.play();
        return ft;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NotNull
    public static Animation fadeOutAndRemove(@NotNull Node ui)
    {
        @NotNull Animation animation = fadeOut(ui);
        animation.setOnFinished(actionEvent -> ((Pane) (ui.getParent())).getChildren().remove(ui));
        return animation;
    }

    public static void blurOut(@NotNull Node node)
    {
        blurOut(node, UI_ANIMATION_TIME);
    }

    @SuppressWarnings("SameParameterValue")
    public static void blurOut(@NotNull Node node, int time)
    {
        @NotNull GaussianBlur blur = new GaussianBlur(0.0);
        node.setEffect(blur);
        @NotNull Timeline timeline = new Timeline();
        @NotNull KeyValue kv = new KeyValue(blur.radiusProperty(), 10.0);
        @NotNull KeyFrame kf = new KeyFrame(Duration.millis(time), kv);
        timeline.getKeyFrames().add(kf);
        timeline.play();
    }

    public static void blurIn(@NotNull Node node)
    {
        @NotNull GaussianBlur blur = (GaussianBlur) node.getEffect();
        @NotNull Timeline timeline = new Timeline();
        @NotNull KeyValue kv = new KeyValue(blur.radiusProperty(), 0.0);
        @NotNull KeyFrame kf = new KeyFrame(Duration.millis(UI_ANIMATION_TIME), kv);
        timeline.getKeyFrames().add(kf);
        timeline.setOnFinished(actionEvent -> node.setEffect(null));
        timeline.play();
    }

    public static void checkGuiThread()
    {
        checkState(Platform.isFxApplicationThread());
    }
}
