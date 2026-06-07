package bisq.desktop.components.controls.skin;

import bisq.desktop.components.controls.BisqRippler;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.skin.CheckBoxSkin;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Stock {@link CheckBoxSkin} + jfoenix-faithful behaviours:
 *
 * 1. Mark animation = JFXCheckBoxSkin.CheckBoxTransition compressed to 120ms (opacity 0→1 first,
 *    then scale 0.5→1). Reverses on uncheck.
 * 2. Ripple confined to the box (label clicks don't trigger one).
 * 3. Box fill animated via {@link #fillIntensity} — directly drives the box's own
 *    backgroundProperty to color #0f9d58 with animated alpha. No child Region (avoids the
 *    1px gap / double-rect issue when the fill overlay leaves the border edges uncovered).
 */
public class BisqCheckBoxSkin extends CheckBoxSkin {

    private static final Duration MARK_DURATION = Duration.millis(120);
    private static final Duration MARK_DELAY = Duration.millis(50);
    private static final double OPACITY_FRACTION = 0.4;

    /** Darker primary green — matches jfoenix's `-jfx-checked-color: #0F9D58`. */
    private static final Color FILL_COLOR = Color.web("#0f9d58");

    private final Pane rippleOverlay;
    private final Rectangle clipShape;
    private final Timeline animation = new Timeline();
    private final DoubleProperty fillIntensity = new SimpleDoubleProperty(0);
    private Node mark;
    private Region box;
    private final Region indeterminateMark = new Region();
    private Circle currentRipple;
    private boolean applyingFill;
    private final ChangeListener<Background> boxBackgroundListener;
    private final ChangeListener<Bounds> boxBoundsListener;
    private final javafx.event.EventHandler<MouseEvent> pressHandler;
    private final javafx.event.EventHandler<MouseEvent> releaseHandler;
    private final ChangeListener<Boolean> selectedListener;

    public BisqCheckBoxSkin(CheckBox control) {
        super(control);

        rippleOverlay = new Pane();
        rippleOverlay.setMouseTransparent(true);
        rippleOverlay.setManaged(false);
        clipShape = new Rectangle();
        rippleOverlay.setClip(clipShape);
        getChildren().add(rippleOverlay);

        // Indeterminate state mark — a small filled square shown inside the box only while the
        // checkbox is indeterminate. Styled via the ".jfx-check-box ... .indeterminate-mark" CSS.
        // Installed into the box (a StackPane) in layoutChildren so it is centered automatically.
        indeterminateMark.getStyleClass().add("indeterminate-mark");
        indeterminateMark.setMouseTransparent(true);
        indeterminateMark.visibleProperty().bind(control.indeterminateProperty());

        boxBoundsListener = (o, oldB, newB) -> syncOverlayToBox();

        // CSS declares `-fx-background-color: transparent` on the box in every state, so any
        // pseudo-class change (hover / armed / focused) re-runs CSS on the box and clobbers the
        // inline green fill set in applyFillIntensity. Re-assert our fill whenever that happens.
        // The applyingFill guard prevents the re-entrant set from looping.
        boxBackgroundListener = (o, oldB, newB) -> {
            if (!applyingFill) {
                applyFillIntensity(fillIntensity.get());
            }
        };

        // Drive the box background from the fillIntensity property — single source of truth
        // for the green fill, animatable, no child overlay required.
        fillIntensity.addListener((o, oldV, newV) -> applyFillIntensity(newV.doubleValue()));

        pressHandler = e -> {
            if (control.isDisabled() || box == null) return;
            Bounds b = box.getBoundsInParent();
            if (!b.contains(e.getX(), e.getY())) return;
            double cx = b.getMinX() + b.getWidth() / 2 - rippleOverlay.getLayoutX();
            double cy = b.getMinY() + b.getHeight() / 2 - rippleOverlay.getLayoutY();
            currentRipple = BisqRippler.pressAt(rippleOverlay,
                    rippleOverlay.getWidth(), rippleOverlay.getHeight(),
                    cx, cy, BisqRippler.GREEN_TINT);
        };
        releaseHandler = e -> {
            if (currentRipple != null) {
                BisqRippler.release(rippleOverlay, currentRipple);
                currentRipple = null;
            }
        };
        control.addEventFilter(MouseEvent.MOUSE_PRESSED, pressHandler);
        control.addEventFilter(MouseEvent.MOUSE_RELEASED, releaseHandler);

        selectedListener = (o, oldV, newV) -> playSelect(newV);
        control.selectedProperty().addListener(selectedListener);
    }

    @Override
    public void dispose() {
        CheckBox c = getSkinnable();
        if (c != null) {
            c.removeEventFilter(MouseEvent.MOUSE_PRESSED, pressHandler);
            c.removeEventFilter(MouseEvent.MOUSE_RELEASED, releaseHandler);
            c.selectedProperty().removeListener(selectedListener);
        }
        if (box != null) {
            box.boundsInParentProperty().removeListener(boxBoundsListener);
            box.backgroundProperty().removeListener(boxBackgroundListener);
        }
        indeterminateMark.visibleProperty().unbind();
        animation.stop();
        super.dispose();
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        if (mark == null) {
            mark = getSkinnable().lookup(".mark");
            if (mark != null) applySelectImmediate(getSkinnable().isSelected());
        }
        if (box == null) {
            Node node = getSkinnable().lookup(".box");
            if (node instanceof Region rb) {
                box = rb;
                box.boundsInParentProperty().addListener(boxBoundsListener);
                box.backgroundProperty().addListener(boxBackgroundListener);
                if (box instanceof Pane bp && !bp.getChildren().contains(indeterminateMark)) {
                    bp.getChildren().add(indeterminateMark);
                }
                applyFillIntensity(getSkinnable().isSelected() ? 1 : 0);
                syncOverlayToBox();
            }
        } else {
            syncOverlayToBox();
        }
    }

    private void applyFillIntensity(double a) {
        if (box == null) return;
        // Inline background overrides CSS — without it every pseudo-class change re-applies
        // CSS's `-fx-background-color: transparent` and competes with our Timeline (visible
        // double-flash on uncheck). Direct setBackground avoids the per-tick CSS reparse that
        // setStyle would force at 60fps during the animation.
        double clamped = Math.min(1, Math.max(0, a));
        Color fill = clamped <= 0 ? Color.TRANSPARENT
                : Color.color(FILL_COLOR.getRed(), FILL_COLOR.getGreen(), FILL_COLOR.getBlue(), clamped);
        applyingFill = true;
        box.setBackground(new Background(new BackgroundFill(fill, CornerRadii.EMPTY, Insets.EMPTY)));
        applyingFill = false;
    }

    private void syncOverlayToBox() {
        if (box == null) return;
        Bounds b = box.getBoundsInParent();
        double size = 40;
        rippleOverlay.setLayoutX(b.getMinX() + b.getWidth() / 2 - size / 2);
        rippleOverlay.setLayoutY(b.getMinY() + b.getHeight() / 2 - size / 2);
        rippleOverlay.setPrefSize(size, size);
        rippleOverlay.resize(size, size);
        clipShape.setWidth(size);
        clipShape.setHeight(size);
        clipShape.setArcWidth(size);
        clipShape.setArcHeight(size);
    }

    private void applySelectImmediate(boolean selected) {
        if (mark != null) {
            mark.setScaleX(selected ? 1 : 0.5);
            mark.setScaleY(selected ? 1 : 0.5);
            mark.setOpacity(selected ? 1 : 0);
        }
        fillIntensity.set(selected ? 1 : 0);
    }

    private void playSelect(boolean selected) {
        if (mark == null) {
            mark = getSkinnable().lookup(".mark");
            if (mark == null) return;
        }
        animation.stop();
        animation.setDelay(MARK_DELAY);
        double opaqueMs = MARK_DURATION.toMillis() * OPACITY_FRACTION;
        double totalMs = MARK_DURATION.toMillis();

        if (selected) {
            animation.getKeyFrames().setAll(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(mark.opacityProperty(), 0, Interpolator.EASE_OUT),
                            new KeyValue(mark.scaleXProperty(), 0.5, Interpolator.EASE_OUT),
                            new KeyValue(mark.scaleYProperty(), 0.5, Interpolator.EASE_OUT),
                            fillKv(0)),
                    new KeyFrame(Duration.millis(opaqueMs),
                            new KeyValue(mark.opacityProperty(), 1, Interpolator.EASE_OUT),
                            fillKv(1)),
                    new KeyFrame(Duration.millis(totalMs),
                            new KeyValue(mark.scaleXProperty(), 1, Interpolator.EASE_OUT),
                            new KeyValue(mark.scaleYProperty(), 1, Interpolator.EASE_OUT)));
        } else {
            // Uncheck: fill fades smoothly from current → 0 over the full duration. No
            // intermediate hold (intermediate keyframe was producing a visible flash because
            // the fill had to spike back up to 1 before fading).
            double shrinkMs = totalMs - opaqueMs;
            animation.getKeyFrames().setAll(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(mark.opacityProperty(), mark.getOpacity(), Interpolator.EASE_OUT),
                            new KeyValue(mark.scaleXProperty(), mark.getScaleX(), Interpolator.EASE_OUT),
                            new KeyValue(mark.scaleYProperty(), mark.getScaleY(), Interpolator.EASE_OUT),
                            fillKv(fillIntensity.get())),
                    new KeyFrame(Duration.millis(shrinkMs),
                            new KeyValue(mark.scaleXProperty(), 0.5, Interpolator.EASE_OUT),
                            new KeyValue(mark.scaleYProperty(), 0.5, Interpolator.EASE_OUT),
                            new KeyValue(mark.opacityProperty(), 1, Interpolator.EASE_OUT)),
                    new KeyFrame(Duration.millis(totalMs),
                            new KeyValue(mark.opacityProperty(), 0, Interpolator.EASE_OUT),
                            fillKv(0)));
        }
        animation.play();
    }

    private KeyValue fillKv(double v) {
        return new KeyValue(fillIntensity, v, Interpolator.EASE_OUT);
    }
}
