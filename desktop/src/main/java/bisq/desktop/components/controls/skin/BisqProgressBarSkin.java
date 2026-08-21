package bisq.desktop.components.controls.skin;

import bisq.desktop.components.controls.BisqJfxProgressBar;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.geometry.Insets;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Pure-JavaFX replacement for {@code com.jfoenix.skins.JFXProgressBarSkin}.
 *
 * Renders three layered StackPanes spanning the control's full inner height (matches jfoenix):
 *   - {@code .track}    full-width background
 *   - {@code .secondary-bar}  width = secondaryProgress fraction (only for BisqJfxProgressBar)
 *   - {@code .bar}      width = progress fraction
 *
 * Style classes match stock + jfoenix so existing CSS keeps working unchanged.
 */
public class BisqProgressBarSkin extends SkinBase<ProgressIndicator> {

    private final StackPane track = new StackPane();
    private final StackPane secondaryBar = new StackPane();
    private final StackPane bar = new StackPane();
    private final Region clip = new Region();
    private double barWidth;
    private double secondaryBarWidth;
    private Animation indeterminateAnimation;
    private boolean wasIndeterminate;
    private double indeterminateAnimWidth = -1;

    private final InvalidationListener widthListener = obs -> { updateProgress(); updateSecondaryProgress(); };
    private final InvalidationListener progressListener = obs -> updateProgress();
    private final InvalidationListener secondaryListener = obs -> updateSecondaryProgress();
    private final InvalidationListener visibleListener = obs -> updateAnimation();
    private final InvalidationListener parentListener = obs -> updateAnimation();
    private final InvalidationListener sceneListener = obs -> updateAnimation();
    private final InvalidationListener indeterminateListener = obs -> initialize();

    public BisqProgressBarSkin(ProgressIndicator control) {
        super(control);
        track.getStyleClass().setAll("track");
        secondaryBar.getStyleClass().setAll("secondary-bar");
        bar.getStyleClass().setAll("bar");
        track.setManaged(false);
        secondaryBar.setManaged(false);
        bar.setManaged(false);

        clip.setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));

        control.widthProperty().addListener(widthListener);
        control.progressProperty().addListener(progressListener);
        control.visibleProperty().addListener(visibleListener);
        control.parentProperty().addListener(parentListener);
        control.sceneProperty().addListener(sceneListener);
        control.indeterminateProperty().addListener(indeterminateListener);
        if (control instanceof BisqJfxProgressBar bjpb) {
            bjpb.secondaryProgressProperty().addListener(secondaryListener);
        }

        getChildren().setAll(track, secondaryBar, bar);

        updateProgress();
        updateSecondaryProgress();
    }

    private void initialize() {
        bar.setClip(null);
        getChildren().setAll(track, secondaryBar, bar);
    }

    @Override
    public double computeBaselineOffset(double topInset, double rightInset, double bottomInset, double leftInset) {
        return Node.BASELINE_OFFSET_SAME_AS_HEIGHT;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return Math.max(100, leftInset + bar.prefWidth(getSkinnable().getWidth()) + rightInset);
    }

    /** Thin default — matches the look used everywhere outside SeparatedPhaseBars, which
     *  overrides this by setting min/maxHeight explicitly. */
    private static final double DEFAULT_PREF_HEIGHT = 4;

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return topInset + DEFAULT_PREF_HEIGHT + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return getSkinnable().prefWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        ProgressIndicator c = getSkinnable();
        if (!c.isIndeterminate()) {
            barWidth = w * Math.min(1, Math.max(0, c.getProgress()));
        }
        // Bar/track/secondary span full control inner height — matches jfoenix.
        track.resizeRelocate(x, y, w, h);
        secondaryBar.resizeRelocate(x, y, secondaryBarWidth, h);
        double bw = c.isIndeterminate() ? w : barWidth;
        bar.resizeRelocate(x, y, bw, h);
        clip.resizeRelocate(0, 0, w, h);

        if (c.isIndeterminate()) {
            double effW = c.getWidth() - (snappedLeftInset() + snappedRightInset());
            if (indeterminateAnimation == null || effW != indeterminateAnimWidth) {
                createIndeterminateAnimation();
            }
            if (isTreeShowing() && indeterminateAnimation.getStatus() != Animation.Status.RUNNING) {
                indeterminateAnimation.play();
            }
            bar.setClip(clip);
        } else if (indeterminateAnimation != null) {
            clearAnimation();
            bar.setClip(null);
        }
    }

    private void updateProgress() {
        ProgressIndicator control = getSkinnable();
        boolean isIndeterminate = control.isIndeterminate();
        if (!(isIndeterminate && wasIndeterminate)) {
            double inset = snappedLeftInset() + snappedRightInset();
            barWidth = ((int) (control.getWidth() - inset) * 2
                    * Math.min(1, Math.max(0, control.getProgress()))) / 2.0;
            control.requestLayout();
        }
        wasIndeterminate = isIndeterminate;
    }

    private void updateSecondaryProgress() {
        ProgressIndicator c = getSkinnable();
        if (!(c instanceof BisqJfxProgressBar bjpb)) {
            secondaryBarWidth = 0;
            return;
        }
        double inset = snappedLeftInset() + snappedRightInset();
        secondaryBarWidth = ((int) (c.getWidth() - inset) * 2
                * Math.min(1, Math.max(0, bjpb.getSecondaryProgress()))) / 2.0;
        c.requestLayout();
    }

    private void updateAnimation() {
        if (indeterminateAnimation == null) {
            if (isTreeShowing()) createIndeterminateAnimation();
        } else {
            if (isTreeShowing()) indeterminateAnimation.play();
            else indeterminateAnimation.pause();
        }
    }

    private boolean isTreeShowing() {
        ProgressIndicator c = getSkinnable();
        return c.isVisible() && c.getParent() != null && c.getScene() != null;
    }

    private void createIndeterminateAnimation() {
        if (indeterminateAnimation != null) clearAnimation();
        double w = getSkinnable().getWidth() - (snappedLeftInset() + snappedRightInset());
        indeterminateAnimWidth = w;
        indeterminateAnimation = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(clip.scaleXProperty(), 0.0, Interpolator.EASE_IN),
                        new KeyValue(clip.translateXProperty(), -w / 2, Interpolator.LINEAR)),
                new KeyFrame(Duration.seconds(0.5),
                        new KeyValue(clip.scaleXProperty(), 0.4, Interpolator.LINEAR)),
                new KeyFrame(Duration.seconds(0.9),
                        new KeyValue(clip.translateXProperty(), w / 2, Interpolator.LINEAR)),
                new KeyFrame(Duration.seconds(1),
                        new KeyValue(clip.scaleXProperty(), 0.0, Interpolator.EASE_OUT)));
        ((Timeline) indeterminateAnimation).setCycleCount(Timeline.INDEFINITE);
    }

    private void clearAnimation() {
        indeterminateAnimation.stop();
        ((Timeline) indeterminateAnimation).getKeyFrames().clear();
        indeterminateAnimation = null;
        indeterminateAnimWidth = -1;
    }

    @Override
    public void dispose() {
        ProgressIndicator c = getSkinnable();
        if (c != null) {
            c.widthProperty().removeListener(widthListener);
            c.progressProperty().removeListener(progressListener);
            c.visibleProperty().removeListener(visibleListener);
            c.parentProperty().removeListener(parentListener);
            c.sceneProperty().removeListener(sceneListener);
            c.indeterminateProperty().removeListener(indeterminateListener);
            if (c instanceof BisqJfxProgressBar bjpb) {
                bjpb.secondaryProgressProperty().removeListener(secondaryListener);
            }
        }
        super.dispose();
        if (indeterminateAnimation != null) clearAnimation();
    }
}
