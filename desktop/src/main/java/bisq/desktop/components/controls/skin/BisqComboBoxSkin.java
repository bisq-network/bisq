package bisq.desktop.components.controls.skin;

import bisq.desktop.components.controls.LabelFloatable;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * Stock {@link ComboBoxListViewSkin} + jfoenix-style underline ({@code .input-line},
 * {@code .input-focused-line}) + floating prompt label ({@code .jfx-text-field-top-label}).
 */
public class BisqComboBoxSkin<T> extends ComboBoxListViewSkin<T> {

    private static final Duration FOCUS_ANIM = Duration.millis(200);
    private static final double LINE_HEIGHT = 1;
    private static final double FOCUSED_LINE_HEIGHT = 2;

    private final Region line = new Region();
    private final Region focusedLine = new Region();
    private final Label topLabel = new Label();
    private final Timeline focusAnim = new Timeline();
    private final Timeline floatAnim = new Timeline();
    private final javafx.scene.transform.Scale promptScaleTransform =
            new javafx.scene.transform.Scale(1, 1, 0, 0);
    private final javafx.beans.property.DoubleProperty promptOffsetY =
            new javafx.beans.property.SimpleDoubleProperty(0);
    private final BooleanBinding showTopLabel;
    private final ChangeListener<Boolean> focusListener;
    private final ChangeListener<T> valueListener;
    private final ChangeListener<Boolean> labelFloatListener;
    private final boolean floatCapableField;

    public BisqComboBoxSkin(ComboBox<T> combo) {
        super(combo);

        line.getStyleClass().add("input-line");
        line.setManaged(false);
        line.setPrefHeight(LINE_HEIGHT);

        focusedLine.getStyleClass().add("input-focused-line");
        focusedLine.setManaged(false);
        focusedLine.setPrefHeight(FOCUSED_LINE_HEIGHT);
        focusedLine.setScaleX(0);

        topLabel.getStyleClass().add("jfx-text-field-top-label");
        topLabel.setManaged(false);
        topLabel.setMouseTransparent(true);
        topLabel.textProperty().bind(combo.promptTextProperty());
        topLabel.getTransforms().add(promptScaleTransform);
        topLabel.translateYProperty().bind(promptOffsetY);
        floatCapableField = combo instanceof LabelFloatable;
        showTopLabel = Bindings.createBooleanBinding(() -> {
            if (!floatCapableField) return false;
            return ((LabelFloatable) combo).isLabelFloat();
        }, floatCapableField ? ((LabelFloatable) combo).labelFloatProperty()
                        : combo.focusedProperty());
        topLabel.visibleProperty().bind(showTopLabel);

        getChildren().addAll(line, focusedLine, topLabel);

        focusListener = (o, ov, nv) -> { animateFocus(); updateFloatAnim(); };
        valueListener = (o, ov, nv) -> updateFloatAnim();
        labelFloatListener = (o, ov, nv) -> updateFloatAnim();
        combo.focusedProperty().addListener(focusListener);
        combo.valueProperty().addListener(valueListener);
        if (floatCapableField) {
            ((LabelFloatable) combo).labelFloatProperty().addListener(labelFloatListener);
        }
        if (combo.getValue() != null) {
            javafx.application.Platform.runLater(() -> {
                double labelH = topLabel.prefHeight(-1);
                promptScaleTransform.setX(0.85);
                promptScaleTransform.setY(0.85);
                promptOffsetY.set(-labelH);
                topLabel.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("floating"), true);
            });
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void dispose() {
        ComboBox<T> c = (ComboBox<T>) getSkinnable();
        if (c != null) {
            c.focusedProperty().removeListener(focusListener);
            c.valueProperty().removeListener(valueListener);
            if (floatCapableField) {
                ((LabelFloatable) c).labelFloatProperty().removeListener(labelFloatListener);
            }
        }
        topLabel.textProperty().unbind();
        topLabel.translateYProperty().unbind();
        topLabel.visibleProperty().unbind();
        showTopLabel.dispose();
        focusAnim.stop();
        floatAnim.stop();
        super.dispose();
    }

    private void updateFloatAnim() {
        ComboBox<T> c = (ComboBox<T>) getSkinnable();
        boolean shouldFloat = c.isFocused() || c.getValue() != null;
        double labelH = topLabel.prefHeight(-1);
        double targetY = shouldFloat ? -labelH : 0;
        double targetScale = shouldFloat ? 0.85 : 1.0;
        floatAnim.stop();
        floatAnim.getKeyFrames().setAll(new KeyFrame(FOCUS_ANIM,
                new KeyValue(promptScaleTransform.xProperty(), targetScale, Interpolator.EASE_BOTH),
                new KeyValue(promptScaleTransform.yProperty(), targetScale, Interpolator.EASE_BOTH),
                new KeyValue(promptOffsetY, targetY, Interpolator.EASE_BOTH)));
        floatAnim.play();
        topLabel.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("floating"),
                shouldFloat);
    }

    private void animateFocus() {
        double target = getSkinnable().isFocused() ? 1 : 0;
        focusAnim.stop();
        focusAnim.getKeyFrames().setAll(new KeyFrame(FOCUS_ANIM,
                new KeyValue(focusedLine.scaleXProperty(), target, Interpolator.EASE_BOTH)));
        focusAnim.play();
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        double cw = getSkinnable().getWidth();
        double ch = getSkinnable().getHeight();
        double lineY = ch - LINE_HEIGHT;
        line.resizeRelocate(0, lineY, cw, LINE_HEIGHT);
        double focusedY = ch - FOCUSED_LINE_HEIGHT;
        focusedLine.resizeRelocate(0, focusedY, cw, FOCUSED_LINE_HEIGHT);
        if (topLabel.isVisible()) {
            double labelH = topLabel.prefHeight(-1);
            // Idle position: vertically centered inside the combo (prompt position).
            topLabel.resizeRelocate(x, y + (h - labelH) / 2, w, labelH);
        }
    }
}
