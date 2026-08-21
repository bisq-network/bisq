package bisq.desktop.components.controls.skin;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SkinBase;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

/**
 * Pure-JavaFX replacement for {@code com.jfoenix.controls.JFXSpinner}'s skin.
 * Rotating open arc with gentle length pulsation, centred in the control's layout area.
 *
 * Centre is updated in {@link #layoutChildren} (not via property bindings) so it reflects
 * post-layout dimensions reliably, including for controls whose width/height aren't pinned
 * via setPrefSize at construction time.
 */
public class BisqSpinnerSkin extends SkinBase<ProgressIndicator> {

    private static final double DEFAULT_RADIUS = 10;
    private static final double STROKE_WIDTH = 2;
    private static final double MAX_RADIUS = 14;

    private final Arc arc;
    private final Timeline rotation;
    private final Timeline arcLength;
    private final ChangeListener<Scene> sceneListener;
    private final javafx.beans.InvalidationListener layoutBoundsListener;
    private final javafx.beans.InvalidationListener widthListener;
    private final javafx.beans.InvalidationListener heightListener;
    private final javafx.beans.InvalidationListener stateListener;

    public BisqSpinnerSkin(ProgressIndicator control) {
        super(control);

        arc = new Arc();
        arc.getStyleClass().add("arc");
        arc.setType(ArcType.OPEN);
        arc.setFill(null);
        arc.setStrokeWidth(STROKE_WIDTH);
        arc.setStrokeLineCap(StrokeLineCap.ROUND);
        arc.setStroke(Color.web("#43d345"));
        arc.setLength(120);
        arc.setManaged(false);
        // Sensible initial centre + radius so the very first paint isn't at (0,0).
        arc.setCenterX(DEFAULT_RADIUS + STROKE_WIDTH);
        arc.setCenterY(DEFAULT_RADIUS + STROKE_WIDTH);
        arc.setRadiusX(DEFAULT_RADIUS);
        arc.setRadiusY(DEFAULT_RADIUS);

        getChildren().add(arc);

        // Force a re-centre on the next pulse, in case layoutBounds never changes
        // (e.g. the control already has its final size by the time we attach).
        javafx.application.Platform.runLater(() -> centerNow());

        // Re-centre + re-size the arc whenever the control's layoutBounds change. This is
        // the reliable signal — layoutChildren() in SkinBase can fire with stale dimensions
        // depending on container resize order; layoutBoundsProperty fires AFTER the size
        // is finalised every time.
        layoutBoundsListener = obs -> centerNow();
        widthListener = obs -> centerNow();
        heightListener = obs -> centerNow();
        control.layoutBoundsProperty().addListener(layoutBoundsListener);
        control.widthProperty().addListener(widthListener);
        control.heightProperty().addListener(heightListener);

        rotation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(arc.startAngleProperty(), 0)),
                new KeyFrame(Duration.seconds(1.4), new KeyValue(arc.startAngleProperty(), -360,
                        Interpolator.LINEAR)));
        rotation.setCycleCount(Timeline.INDEFINITE);

        arcLength = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(arc.lengthProperty(), 90)),
                new KeyFrame(Duration.seconds(0.7), new KeyValue(arc.lengthProperty(), 180,
                        Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.seconds(1.4), new KeyValue(arc.lengthProperty(), 90,
                        Interpolator.EASE_BOTH)));
        arcLength.setCycleCount(Timeline.INDEFINITE);

        sceneListener = (o, oldScene, newScene) -> syncAnimationState();
        stateListener = obs -> syncAnimationState();
        control.sceneProperty().addListener(sceneListener);
        control.indeterminateProperty().addListener(stateListener);
        control.visibleProperty().addListener(stateListener);
        control.parentProperty().addListener(stateListener);

        syncAnimationState();
    }

    private void syncAnimationState() {
        ProgressIndicator c = getSkinnable();
        if (c == null) return;
        boolean shouldRun = c.isIndeterminate() && c.isVisible()
                && c.getScene() != null && c.getParent() != null;
        if (shouldRun) {
            if (rotation.getStatus() != javafx.animation.Animation.Status.RUNNING) rotation.play();
            if (arcLength.getStatus() != javafx.animation.Animation.Status.RUNNING) arcLength.play();
        } else {
            rotation.pause();
            arcLength.pause();
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        centerNow();
    }

    private void centerNow() {
        if (getSkinnable() == null) return;
        double cw = getSkinnable().getWidth();
        double ch = getSkinnable().getHeight();
        if (cw <= 0 || ch <= 0) return;
        double r = Math.max(4, Math.min(MAX_RADIUS, Math.min(cw, ch) / 2 - STROKE_WIDTH));
        arc.setRadiusX(r);
        arc.setRadiusY(r);
        arc.setCenterX(cw / 2);
        arc.setCenterY(ch / 2);
    }

    @Override
    protected double computePrefWidth(double height, double top, double right, double bottom, double left) {
        return DEFAULT_RADIUS * 2 + STROKE_WIDTH * 2 + left + right + 4;
    }

    @Override
    protected double computePrefHeight(double width, double top, double right, double bottom, double left) {
        return DEFAULT_RADIUS * 2 + STROKE_WIDTH * 2 + top + bottom + 4;
    }

    @Override
    protected double computeMaxWidth(double height, double top, double right, double bottom, double left) {
        return getSkinnable().prefWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width, double top, double right, double bottom, double left) {
        return getSkinnable().prefHeight(width);
    }

    @Override
    public void dispose() {
        ProgressIndicator c = getSkinnable();
        if (c != null) {
            c.sceneProperty().removeListener(sceneListener);
            c.layoutBoundsProperty().removeListener(layoutBoundsListener);
            c.widthProperty().removeListener(widthListener);
            c.heightProperty().removeListener(heightListener);
            c.indeterminateProperty().removeListener(stateListener);
            c.visibleProperty().removeListener(stateListener);
            c.parentProperty().removeListener(stateListener);
        }
        if (rotation != null) rotation.stop();
        if (arcLength != null) arcLength.stop();
        super.dispose();
    }
}
