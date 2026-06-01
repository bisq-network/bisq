package bisq.desktop.components.controls.skin;

import bisq.desktop.components.controls.BisqJfxToggleButton;
import bisq.desktop.components.controls.BisqRippler;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.skin.ToggleButtonSkin;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Pure-JavaFX replacement for {@code com.jfoenix.controls.JFXToggleButton}'s skin.
 *
 * Matches {@code JFXToggleButtonSkin}:
 *  - 100ms ease-both translation of the thumb on selected/unselected.
 *  - JFXRippler-equivalent ripple expanded around the THUMB circle (not the whole control)
 *    on press, using the same {@link BisqRippler} pattern as buttons/checkboxes.
 */
public class BisqToggleButtonSkin extends ToggleButtonSkin {

    private static final double TRACK_WIDTH = 31;
    private static final double TRACK_HEIGHT = 11;
    private static final double THUMB_RADIUS = 8;
    private static final Duration ANIM_DURATION = Duration.millis(100);

    private final Region track;
    private final Region thumb;
    private final Pane rippleOverlay;
    private final Rectangle rippleClip;
    private final Timeline anim = new Timeline();
    private Circle currentRipple;
    private final javafx.beans.property.DoubleProperty thumbBaseX = new javafx.beans.property.SimpleDoubleProperty(0);
    private final javafx.event.EventHandler<MouseEvent> pressHandler;
    private final javafx.event.EventHandler<MouseEvent> releaseHandler;
    private final ChangeListener<Paint> toggleColorListener;
    private final BisqJfxToggleButton bisqToggle;

    public BisqToggleButtonSkin(ToggleButton control) {
        super(control);

        track = new Region();
        track.getStyleClass().setAll("track");
        track.setPrefSize(TRACK_WIDTH, TRACK_HEIGHT);
        track.setMaxSize(TRACK_WIDTH, TRACK_HEIGHT);
        track.setPadding(Insets.EMPTY);
        track.setManaged(false);

        thumb = new Region();
        thumb.getStyleClass().setAll("thumb");
        thumb.setPrefSize(THUMB_RADIUS * 2, THUMB_RADIUS * 2);
        thumb.setMaxSize(THUMB_RADIUS * 2, THUMB_RADIUS * 2);
        thumb.setPadding(Insets.EMPTY);
        thumb.setManaged(false);

        // Ripple overlay scoped to a circular area centred on the thumb. Size = wide enough that
        // the ripple ring extends past the thumb (matches jfoenix's circlePane padding).
        rippleOverlay = new Pane();
        rippleOverlay.setMouseTransparent(true);
        rippleOverlay.setManaged(false);
        // Fixed 40px so toggle / checkbox / radio rings have identical spread.
        double overlaySize = 40;
        rippleOverlay.setPrefSize(overlaySize, overlaySize);
        rippleOverlay.resize(overlaySize, overlaySize);
        rippleClip = new Rectangle(overlaySize, overlaySize);
        // Circular clip — make the rectangle fully rounded.
        rippleClip.setArcWidth(overlaySize);
        rippleClip.setArcHeight(overlaySize);
        rippleOverlay.setClip(rippleClip);

        getChildren().addAll(track, thumb, rippleOverlay);

        // Bind once. layoutChildren only updates the source DoubleProperty.
        rippleOverlay.layoutXProperty().bind(
                thumb.translateXProperty().add(thumbBaseX).add(THUMB_RADIUS - overlaySize / 2));

        pressHandler = e -> {
            if (control.isDisabled()) return;
            if (e.getX() > TRACK_WIDTH + 6) return;
            currentRipple = BisqRippler.pressAt(rippleOverlay,
                    rippleOverlay.getWidth(), rippleOverlay.getHeight(),
                    rippleOverlay.getWidth() / 2,
                    rippleOverlay.getHeight() / 2,
                    BisqRippler.GREEN_TINT);
        };
        releaseHandler = e -> {
            if (currentRipple != null) {
                BisqRippler.release(rippleOverlay, currentRipple);
                currentRipple = null;
            }
        };
        control.addEventFilter(MouseEvent.MOUSE_PRESSED, pressHandler);
        control.addEventFilter(MouseEvent.MOUSE_RELEASED, releaseHandler);

        registerChangeListener(control.selectedProperty(), obs -> { animate(); applyToggleColor(); });
        bisqToggle = control instanceof BisqJfxToggleButton bjt ? bjt : null;
        toggleColorListener = (o, ov, nv) -> applyToggleColor();
        if (bisqToggle != null) {
            bisqToggle.toggleColorProperty().addListener(toggleColorListener);
        }
        applyImmediate();
        applyToggleColor();
    }

    /**
     * Honour the CSS-styleable {@code -jfx-toggle-color} on {@link BisqJfxToggleButton}. When
     * set, override the selected-state thumb (solid color) and track (faded version) backgrounds
     * via inline-style {@code setStyle} so they win over the author CSS rules in bisq-controls.css.
     * When null, clear the override and let CSS take over again.
     */
    private void applyToggleColor() {
        if (bisqToggle == null) return;
        Paint p = bisqToggle.getToggleColor();
        if (!(p instanceof Color c) || !getSkinnable().isSelected()) {
            // Use inline-style reset so the author CSS rules in bisq-controls.css resume.
            thumb.setStyle(null);
            track.setStyle(null);
            return;
        }
        // Inline style — origin USER beats author CSS (`-fx-background-color: -bs-color-primary`
        // on `.jfx-toggle-button:selected > .thumb`), so per-instance override wins.
        thumb.setStyle("-fx-background-color: " + toRgba(c, 1.0) + ";");
        track.setStyle("-fx-background-color: " + toRgba(c, 0.55) + ";");
    }

    private static String toRgba(Color c, double alpha) {
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        // Locale.ROOT: comma-decimal locales (e.g. de_DE) would otherwise emit `1,000` →
        // invalid CSS, breaking the per-instance toggle-color override on those locales.
        return String.format(java.util.Locale.ROOT, "rgba(%d, %d, %d, %.3f)",
                r, g, b, alpha * c.getOpacity());
    }

    @Override
    public void dispose() {
        ToggleButton c = getSkinnable();
        if (c != null) {
            c.removeEventFilter(MouseEvent.MOUSE_PRESSED, pressHandler);
            c.removeEventFilter(MouseEvent.MOUSE_RELEASED, releaseHandler);
        }
        if (bisqToggle != null) {
            bisqToggle.toggleColorProperty().removeListener(toggleColorListener);
        }
        anim.stop();
        rippleOverlay.layoutXProperty().unbind();
        super.dispose();
    }

    private void applyImmediate() {
        double tx = getSkinnable().isSelected() ? TRACK_WIDTH - THUMB_RADIUS * 2 : 0;
        thumb.setTranslateX(tx);
    }

    private void animate() {
        double targetX = getSkinnable().isSelected() ? TRACK_WIDTH - THUMB_RADIUS * 2 : 0;
        anim.stop();
        anim.getKeyFrames().setAll(new KeyFrame(ANIM_DURATION,
                new KeyValue(thumb.translateXProperty(), targetX, Interpolator.EASE_BOTH)));
        anim.play();
    }

    // Match jfoenix's JFXToggleButtonSkin: circlePane has Insets(circleRadius * 1.5) = 12px
    // padding on every side of the thumb circle. Reproduce that 12px breathing room around our
    // entire toggle visual (track + thumb travel area), plus the same graphicTextGap default (4)
    // between the toggle graphic and the text label.
    private static final double TOGGLE_PAD = 12;
    private static final double TOGGLE_GAP = 4;

    @Override
    protected double computePrefWidth(double height, double top, double right, double bottom, double left) {
        double label = super.computePrefWidth(height, top, right, bottom, left);
        return TOGGLE_PAD * 2 + TRACK_WIDTH + TOGGLE_GAP + label;
    }

    @Override
    protected double computePrefHeight(double width, double top, double right, double bottom, double left) {
        return Math.max(THUMB_RADIUS * 2 + TOGGLE_PAD * 2,
                super.computePrefHeight(width, top, right, bottom, left));
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double cy = y + h / 2;
        double tx = x + TOGGLE_PAD;
        track.resizeRelocate(tx, cy - TRACK_HEIGHT / 2, TRACK_WIDTH, TRACK_HEIGHT);
        thumb.resizeRelocate(tx, cy - THUMB_RADIUS, THUMB_RADIUS * 2, THUMB_RADIUS * 2);

        thumbBaseX.set(tx);
        rippleOverlay.setLayoutY(cy - rippleOverlay.getWidth() / 2);

        double toggleW = TOGGLE_PAD * 2 + TRACK_WIDTH + TOGGLE_GAP;
        super.layoutChildren(x + toggleW, y, Math.max(0, w - toggleW), h);
    }
}
