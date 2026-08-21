package bisq.desktop.components.controls.skin;

import bisq.desktop.components.controls.BisqRippler;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.skin.RadioButtonSkin;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.util.Duration;

/**
 * Pure-JavaFX replacement for {@code bisq.desktop.components.JFXRadioButtonSkinBisqStyle}.
 *
 * Renders the radio as an outer ring ({@code .radio}) plus an inner dot ({@code .dot}). On
 * selected → dot animates scale 0 → 0.55 with EASE_BOTH (jfoenix's timing).
 *
 * Adds a click-ripple around the circle (NOT the label) matching jfoenix's JFXRippler with
 * RipplerMask.CIRCLE on the radio container. The ripple only fires when the click is within
 * the radio circle's bounds — label clicks toggle selection without a ripple, like jfoenix.
 */
public class BisqRadioButtonSkin extends RadioButtonSkin {

    private static final double RADIUS = 7;
    private static final double SELECTED_DOT_SCALE = 0.55;
    private static final Duration ANIM_DURATION = Duration.millis(200);

    private final Circle radio;
    private final Circle dot;
    private final StackPane container;
    private final Timeline selectionAnim;
    private final Pane rippleOverlay;
    private final Rectangle rippleClip;
    private Circle currentRipple;
    private final javafx.event.EventHandler<MouseEvent> pressHandler;
    private final javafx.event.EventHandler<MouseEvent> releaseHandler;

    public BisqRadioButtonSkin(RadioButton control) {
        super(control);

        radio = new Circle(RADIUS);
        radio.getStyleClass().setAll("radio");
        radio.setFill(Color.TRANSPARENT);
        radio.setStrokeWidth(1);
        radio.setStrokeType(StrokeType.INSIDE);

        dot = new Circle(RADIUS);
        dot.getStyleClass().setAll("dot");
        dot.setScaleX(0);
        dot.setScaleY(0);
        dot.setSmooth(true);

        container = new StackPane(radio, dot);
        container.getStyleClass().add("radio-container");

        // Ripple overlay scoped to a circular area centred on the radio circle.
        rippleOverlay = new Pane();
        rippleOverlay.setMouseTransparent(true);
        rippleOverlay.setManaged(false);
        // Fixed 40px so toggle / checkbox / radio rings have identical spread (jfoenix-faithful).
        double overlaySize = 40;
        rippleOverlay.setPrefSize(overlaySize, overlaySize);
        rippleOverlay.resize(overlaySize, overlaySize);
        rippleClip = new Rectangle(overlaySize, overlaySize);
        rippleClip.setArcWidth(overlaySize);
        rippleClip.setArcHeight(overlaySize);
        rippleOverlay.setClip(rippleClip);

        selectionAnim = new Timeline();
        registerChangeListener(control.selectedProperty(), obs -> playSelectionAnim());

        updateChildren();
        applySelectionImmediate();

        pressHandler = e -> {
            if (control.isDisabled()) return;
            Bounds b = container.getBoundsInParent();
            if (!b.contains(e.getX(), e.getY())) return;
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
    }

    @Override
    public void dispose() {
        RadioButton c = getSkinnable();
        if (c != null) {
            c.removeEventFilter(MouseEvent.MOUSE_PRESSED, pressHandler);
            c.removeEventFilter(MouseEvent.MOUSE_RELEASED, releaseHandler);
        }
        selectionAnim.stop();
        super.dispose();
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        if (container != null) {
            getChildren().removeIf(n -> "radio".equals(firstStyleClass(n)) || "dot".equals(firstStyleClass(n)));
            if (!getChildren().contains(container)) {
                getChildren().add(container);
            }
            if (!getChildren().contains(rippleOverlay)) {
                getChildren().add(rippleOverlay);
            }
        }
    }

    private void applySelectionImmediate() {
        boolean selected = getSkinnable().isSelected();
        double s = selected ? SELECTED_DOT_SCALE : 0;
        dot.setScaleX(s);
        dot.setScaleY(s);
    }

    private void playSelectionAnim() {
        boolean selected = getSkinnable().isSelected();
        double s = selected ? SELECTED_DOT_SCALE : 0;
        selectionAnim.stop();
        selectionAnim.getKeyFrames().setAll(new KeyFrame(ANIM_DURATION,
                new KeyValue(dot.scaleXProperty(), s, Interpolator.EASE_BOTH),
                new KeyValue(dot.scaleYProperty(), s, Interpolator.EASE_BOTH)));
        selectionAnim.play();
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        RadioButton rb = getSkinnable();
        double contW = container.prefWidth(-1);
        double contH = container.prefHeight(-1);
        double labelW = Math.min(rb.prefWidth(-1) - contW, w - contW);
        double labelH = Math.min(rb.prefHeight(labelW), h);
        double maxH = Math.max(contH, labelH);
        double xOff = computeXOffset(w, labelW + contW, rb.getAlignment().getHpos()) + x;
        double yOff = computeYOffset(h, maxH, rb.getAlignment().getVpos()) + y;
        layoutLabelInArea(xOff + contW + 4, yOff, labelW, maxH, rb.getAlignment());
        container.resize(contW, contH);
        positionInArea(container, xOff, yOff, contW, maxH, 0,
                rb.getAlignment().getHpos(), rb.getAlignment().getVpos());

        // Anchor ripple overlay to the radio circle's centre.
        double overlaySize = rippleOverlay.getWidth();
        Bounds cb = container.getBoundsInParent();
        rippleOverlay.setLayoutX(cb.getMinX() + cb.getWidth() / 2 - overlaySize / 2);
        rippleOverlay.setLayoutY(cb.getMinY() + cb.getHeight() / 2 - overlaySize / 2);
    }

    @Override
    protected double computePrefWidth(double height, double top, double right, double bottom, double left) {
        return super.computePrefWidth(height, top, right, bottom, left) + container.prefWidth(-1) + 4;
    }

    private static String firstStyleClass(Node n) {
        return n.getStyleClass().isEmpty() ? "" : n.getStyleClass().get(0);
    }

    private static double computeXOffset(double width, double contentWidth, HPos hpos) {
        return switch (hpos) {
            case LEFT -> 0;
            case CENTER -> (width - contentWidth) / 2;
            case RIGHT -> width - contentWidth;
        };
    }

    private static double computeYOffset(double height, double contentHeight, VPos vpos) {
        return switch (vpos) {
            case TOP -> 0;
            case CENTER -> (height - contentHeight) / 2;
            case BOTTOM -> height - contentHeight;
            default -> 0;
        };
    }
}
