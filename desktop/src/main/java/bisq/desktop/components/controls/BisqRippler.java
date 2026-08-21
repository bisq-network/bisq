package bisq.desktop.components.controls;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Click-time ripple animation copied from jfoenix's {@code JFXRippler.Ripple} (Apache 2.0).
 *
 * Mechanics (matches JFXRippler exactly):
 *   1. A {@link Circle} is created at the click coordinates with a PRE-COMPUTED full radius
 *      (= sqrt(w² + h²) · 1.1 + 5), capped at {@link #RIPPLE_MAX_RADIUS}.
 *   2. Both fill and stroke are set to the ripple color at 30% alpha.
 *   3. Initial state: {@code scaleX = scaleY = 0}, {@code opacity = 1}.
 *   4. inAnimation (on press): scale 0 → 0.9 over 900ms using jfoenix's custom SPLINE.
 *   5. outAnimation (on release): scale → 1, opacity → 0 over ~min(800, 0.9·500/scaleX) ms.
 *
 * The {@link #playAt(Pane, Region, double, double)} convenience runs in+out back-to-back so a
 * single click yields the full pulse. {@link #pressAt} / {@link #release} expose the press/release
 * split for skins that want sustained-press behaviour.
 */
public final class BisqRippler {

    private static final double RIPPLE_MAX_RADIUS = 300;
    private static final Interpolator RIPPLE_INTERPOLATOR =
            Interpolator.SPLINE(0.0825, 0.3025, 0.0875, 0.9975);

    private BisqRippler() {}

    /** Reverses {@link #makeOverlay} bindings so the region's width/height listeners drop the
     *  overlay reference. Call from skin {@code dispose()}. */
    public static void disposeOverlay(Pane overlay) {
        if (overlay == null) return;
        overlay.prefWidthProperty().unbind();
        overlay.prefHeightProperty().unbind();
        overlay.minWidthProperty().unbind();
        overlay.minHeightProperty().unbind();
        overlay.maxWidthProperty().unbind();
        overlay.maxHeightProperty().unbind();
        if (overlay.getClip() instanceof Rectangle r) {
            r.widthProperty().unbind();
            r.heightProperty().unbind();
        }
    }

    public static Pane makeOverlay(Region region) {
        Pane overlay = new Pane();
        overlay.setMouseTransparent(true);
        overlay.setManaged(false);
        overlay.setLayoutX(0);
        overlay.setLayoutY(0);
        overlay.prefWidthProperty().bind(region.widthProperty());
        overlay.prefHeightProperty().bind(region.heightProperty());
        overlay.minWidthProperty().bind(region.widthProperty());
        overlay.minHeightProperty().bind(region.heightProperty());
        overlay.maxWidthProperty().bind(region.widthProperty());
        overlay.maxHeightProperty().bind(region.heightProperty());
        overlay.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT,
                javafx.scene.layout.CornerRadii.EMPTY, javafx.geometry.Insets.EMPTY)));
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        overlay.setClip(clip);
        return overlay;
    }

    /** One-shot press+release ripple. */
    public static void playAt(Pane overlay, Region region, double x, double y) {
        Circle ripple = pressAt(overlay, region, x, y, contrastingFill(region));
        if (ripple != null) release(overlay, ripple);
    }

    public static Circle pressAt(Pane overlay, Region region, double x, double y) {
        return pressAt(overlay, region, x, y, contrastingFill(region));
    }

    /** Size-based overload — skip allocating a stub Region purely to feed radius math. */
    public static Circle pressAt(Pane overlay, double w, double h, double x, double y, Paint fill) {
        double radius = computeRippleRadius(w, h);
        return spawnRipple(overlay, x, y, radius, fill);
    }

    /** Spawn the ripple, play its inAnimation, return the Circle so caller can release later. */
    public static Circle pressAt(Pane overlay, Region region, double x, double y, Paint fill) {
        if (region.isDisabled()) return null;
        return spawnRipple(overlay, x, y, computeRippleRadius(region), fill);
    }

    private static Circle spawnRipple(Pane overlay, double x, double y, double radius, Paint fill) {
        Circle ripple = new Circle(x, y, radius);
        ripple.setMouseTransparent(true);
        ripple.setManaged(false);
        ripple.setSmooth(true);
        ripple.setScaleX(0);
        ripple.setScaleY(0);
        // Use the caller's Paint as-is so they control hue + alpha.
        Paint c = fill != null ? fill : new Color(1, 1, 1, 0.18);
        ripple.setFill(c);
        ripple.setStroke(c);
        overlay.getChildren().add(ripple);

        Timeline in = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(ripple.scaleXProperty(), 0, RIPPLE_INTERPOLATOR),
                        new KeyValue(ripple.scaleYProperty(), 0, RIPPLE_INTERPOLATOR),
                        new KeyValue(ripple.opacityProperty(), 1, RIPPLE_INTERPOLATOR)),
                new KeyFrame(Duration.millis(900),
                        new KeyValue(ripple.scaleXProperty(), 0.9, RIPPLE_INTERPOLATOR),
                        new KeyValue(ripple.scaleYProperty(), 0.9, RIPPLE_INTERPOLATOR)));
        ripple.getProperties().put("bisq.ripple.in", in);
        in.play();
        return ripple;
    }

    /** Stop the in-animation, run the out-animation, remove the Circle when done. */
    public static void release(Pane overlay, Circle ripple) {
        if (ripple == null) return;
        Object in = ripple.getProperties().get("bisq.ripple.in");
        if (in instanceof Animation a) a.stop();

        double scale = Math.max(ripple.getScaleX(), 0.05);
        double durationMs = Math.min(800, (0.9 * 500) / scale);

        Timeline out = new Timeline(new KeyFrame(Duration.millis(durationMs),
                new KeyValue(ripple.scaleXProperty(), 1, RIPPLE_INTERPOLATOR),
                new KeyValue(ripple.scaleYProperty(), 1, RIPPLE_INTERPOLATOR),
                new KeyValue(ripple.opacityProperty(), 0, RIPPLE_INTERPOLATOR)));
        out.setOnFinished(e -> overlay.getChildren().remove(ripple));
        out.play();
    }

    private static double computeRippleRadius(Region region) {
        return computeRippleRadius(region.getWidth(), region.getHeight());
    }

    private static double computeRippleRadius(double w, double h) {
        return Math.min(Math.sqrt(w * w + h * h), RIPPLE_MAX_RADIUS) * 1.1 + 5;
    }

    public static Paint contrastingFill(Region region) {
        Background bg = region.getBackground();
        if (bg != null && !bg.getFills().isEmpty()) {
            BackgroundFill bf = bg.getFills().get(0);
            if (bf.getFill() instanceof Color c) {
                double y = 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
                return y < 0.5
                        ? new Color(1, 1, 1, 0.18)
                        : new Color(0, 0, 0, 0.18);
            }
        }
        return new Color(1, 1, 1, 0.18);
    }

    /** Bisq primary green ripple at low opacity — used by checkbox / radio / toggle to match jfoenix. */
    public static final Color GREEN_TINT = Color.web("#43d345", 0.10);
}
