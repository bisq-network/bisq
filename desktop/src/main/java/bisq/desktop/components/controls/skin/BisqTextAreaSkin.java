package bisq.desktop.components.controls.skin;

import bisq.desktop.components.controls.BisqJfxTextArea;
import bisq.desktop.components.controls.LabelFloatable;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.skin.TextAreaSkin;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import javafx.geometry.Insets;

/**
 * Pure-JavaFX replacement for {@code bisq.desktop.components.JFXTextAreaSkinBisqStyle}.
 *
 * Adds the same {@code .input-line} / {@code .input-focused-line} underline pair as
 * {@link BisqTextFieldSkin}, plus transparent viewport background (jfoenix's text area
 * sat on its own background; the viewport itself stays clear).
 *
 * Floating prompt + validation pane intentionally omitted — defer until pixel diff demands.
 */
public class BisqTextAreaSkin extends TextAreaSkin {

    private static final Duration ANIM_DURATION = Duration.millis(200);
    private static final double LINE_HEIGHT = 1;
    private static final double FOCUSED_LINE_HEIGHT = 2;

    private final Region line = new Region();
    private final Region focusedLine = new Region();
    private final Label topLabel = new Label();
    private final Label errorLabel = new Label();
    private final BooleanBinding showTopLabel;
    private final Timeline focusAnim = new Timeline();
    private final Timeline floatAnim = new Timeline();
    private final javafx.scene.transform.Scale promptScaleTransform =
            new javafx.scene.transform.Scale(1, 1, 0, 0);
    private final javafx.beans.property.DoubleProperty promptOffsetY =
            new javafx.beans.property.SimpleDoubleProperty(0);
    private boolean viewportCleared;
    private final ChangeListener<Boolean> focusListener;
    private final ChangeListener<String> textListener;
    private final ChangeListener<Boolean> labelFloatListener;
    private final boolean floatCapableField;

    public BisqTextAreaSkin(TextArea control) {
        super(control);
        control.setWrapText(true);

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
        topLabel.textProperty().bind(control.promptTextProperty());
        topLabel.getTransforms().add(promptScaleTransform);
        topLabel.translateYProperty().bind(promptOffsetY);
        floatCapableField = control instanceof LabelFloatable;
        showTopLabel = Bindings.createBooleanBinding(() -> {
            if (!floatCapableField) return false;
            return ((LabelFloatable) control).isLabelFloat();
        }, floatCapableField ? ((LabelFloatable) control).labelFloatProperty()
                        : control.focusedProperty());
        topLabel.visibleProperty().bind(showTopLabel);

        errorLabel.getStyleClass().add("error-label");
        errorLabel.setManaged(false);
        errorLabel.setMouseTransparent(true);
        errorLabel.setWrapText(true);
        if (control instanceof BisqJfxTextArea bjta) {
            errorLabel.textProperty().bind(bjta.errorMessageProperty());
        }
        errorLabel.visibleProperty().bind(errorLabel.textProperty().isNotEmpty());

        getChildren().addAll(line, focusedLine, topLabel, errorLabel);

        focusListener = (o, ov, nv) -> { animateFocus(); updateFloatAnim(); };
        textListener = (o, ov, nv) -> updateFloatAnim();
        labelFloatListener = (o, ov, nv) -> updateFloatAnim();
        control.focusedProperty().addListener(focusListener);
        control.textProperty().addListener(textListener);
        if (floatCapableField) {
            ((LabelFloatable) control).labelFloatProperty().addListener(labelFloatListener);
        }
        TextArea init = control;
        if (init.getText() != null && !init.getText().isEmpty()) {
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
    public void dispose() {
        TextArea c = getSkinnable();
        if (c != null) {
            c.focusedProperty().removeListener(focusListener);
            c.textProperty().removeListener(textListener);
            if (floatCapableField) {
                ((LabelFloatable) c).labelFloatProperty().removeListener(labelFloatListener);
            }
        }
        topLabel.textProperty().unbind();
        topLabel.translateYProperty().unbind();
        topLabel.visibleProperty().unbind();
        errorLabel.textProperty().unbind();
        errorLabel.visibleProperty().unbind();
        showTopLabel.dispose();
        focusAnim.stop();
        floatAnim.stop();
        super.dispose();
    }

    private void updateFloatAnim() {
        TextArea c = getSkinnable();
        boolean shouldFloat = c.isFocused() || (c.getText() != null && !c.getText().isEmpty());
        double labelH = topLabel.prefHeight(-1);
        double targetY = shouldFloat ? -labelH : 0;
        double targetScale = shouldFloat ? 0.85 : 1.0;
        floatAnim.stop();
        floatAnim.getKeyFrames().setAll(new KeyFrame(ANIM_DURATION,
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
        focusAnim.getKeyFrames().setAll(new KeyFrame(ANIM_DURATION,
                new KeyValue(focusedLine.scaleXProperty(), target, Interpolator.EASE_BOTH)));
        focusAnim.play();
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);

        // Position the underline within the inset-aware area to avoid leaking past the
        // control's visible background.
        double lineY = y + h - LINE_HEIGHT;
        line.resizeRelocate(x, lineY, w, LINE_HEIGHT);
        double focusedY = y + h - FOCUSED_LINE_HEIGHT;
        focusedLine.resizeRelocate(x, focusedY, w, FOCUSED_LINE_HEIGHT);

        if (topLabel.isVisible()) {
            double labelH = topLabel.prefHeight(-1);
            // Idle position: top of the text-area content area. Float anim translates upward.
            topLabel.resizeRelocate(x, y, w, labelH);
        }

        if (errorLabel.isVisible()) {
            double cw = getSkinnable().getWidth();
            double ch = getSkinnable().getHeight();
            double errH = errorLabel.prefHeight(cw);
            errorLabel.resizeRelocate(0, ch + 3, cw, errH);
        }

        if (!viewportCleared && !getChildren().isEmpty() && getChildren().get(0) instanceof ScrollPane sp
                && !sp.getChildrenUnmodifiable().isEmpty()
                && sp.getChildrenUnmodifiable().get(0) instanceof Region vp) {
            vp.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)));
            vp.applyCss();
            viewportCleared = true;
        }
    }
}
