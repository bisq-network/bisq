package bisq.desktop.components.controls.skin;

import bisq.desktop.components.controls.BisqJfxPasswordField;
import bisq.desktop.components.controls.BisqJfxTextField;
import bisq.desktop.components.controls.LabelFloatable;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.TextFieldSkin;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * Pure-JavaFX replacement for {@code bisq.desktop.components.JFXTextFieldSkinBisqStyle}.
 *
 * Installs four extra children on top of the stock {@link TextFieldSkin}:
 *   - {@code .input-line}: always-visible 1px underline (Region).
 *   - {@code .input-focused-line}: thicker underline that scales in on focus (Region).
 *   - {@code .jfx-text-field-top-label}: floating prompt label that animates scale 1→0.85 +
 *     translateY 0→-labelHeight when focused or non-empty (driven by promptScaleTransform +
 *     promptOffsetY). Only shown when the control implements {@link LabelFloatable} with
 *     labelFloat=true.
 *   - {@code .error-label}: rendered below the input, bound to
 *     {@code BisqJfx{TextField,PasswordField}.errorMessageProperty()}, visible when non-empty.
 *
 * CSS rules in {@code theme-{dark,light}.css} and {@code bisq-controls.css} target these style
 * classes; this skin just installs the nodes and runs the focus/float animations.
 */
public class BisqTextFieldSkin extends TextFieldSkin {

    private static final Duration ANIM_DURATION = Duration.millis(200);
    private static final double LINE_HEIGHT = 1;
    private static final double FOCUSED_LINE_HEIGHT = 2;

    private final Region line = new Region();
    private final Region focusedLine = new Region();
    private final Label topLabel = new Label();
    private final Label errorLabel = new Label();
    private final Timeline focusAnim = new Timeline();
    private final Timeline floatAnim = new Timeline();
    private final javafx.scene.transform.Scale promptScaleTransform =
            new javafx.scene.transform.Scale(1, 1, 0, 0);
    private final javafx.beans.property.DoubleProperty promptOffsetY =
            new javafx.beans.property.SimpleDoubleProperty(0);
    private final double inputLineExtension;
    private final BooleanBinding showTopLabel;
    private final ChangeListener<Boolean> focusListener;
    private final ChangeListener<String> textListener;
    private final ChangeListener<Boolean> labelFloatListener;
    private final boolean isFloatCapable;

    public BisqTextFieldSkin(TextField control) {
        this(control, 0);
    }

    public BisqTextFieldSkin(TextField control, double inputLineExtension) {
        super(control);
        this.inputLineExtension = inputLineExtension;

        line.getStyleClass().add("input-line");
        line.setManaged(false);
        line.setPrefHeight(LINE_HEIGHT);

        focusedLine.getStyleClass().add("input-focused-line");
        focusedLine.setManaged(false);
        focusedLine.setPrefHeight(FOCUSED_LINE_HEIGHT);
        focusedLine.setScaleX(0);

        // Floating prompt label — when labelFloat=true the label is always present and animates
        // between two positions: INSIDE the input (idle, empty, full size, prompt colour) and
        // ABOVE the input (focused or filled, 0.85 scale, focus colour). Stock prompt is hidden
        // for label-float controls via the .jfx-text-field:floats { -fx-prompt-text-fill: transparent }
        // rule we add in bisq-controls.css.
        topLabel.getStyleClass().add("jfx-text-field-top-label");
        topLabel.setManaged(false);
        topLabel.setMouseTransparent(true);
        topLabel.textProperty().bind(control.promptTextProperty());
        topLabel.getTransforms().add(promptScaleTransform);
        topLabel.translateYProperty().bind(promptOffsetY);
        isFloatCapable = control instanceof LabelFloatable;
        showTopLabel = Bindings.createBooleanBinding(() -> {
            if (!isFloatCapable) return false;
            return ((LabelFloatable) control).isLabelFloat();
        }, isFloatCapable ? ((LabelFloatable) control).labelFloatProperty()
                          : control.focusedProperty());
        topLabel.visibleProperty().bind(showTopLabel);

        // Error label rendered below the input (matches jfoenix's ValidationPane). Unmanaged so
        // it sits at a computed y in layoutChildren without growing the field's prefHeight; the
        // surrounding form is expected to leave row-gap room.
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setManaged(false);
        errorLabel.setMouseTransparent(true);
        errorLabel.setWrapText(true);
        if (control instanceof BisqJfxTextField bjtf) {
            errorLabel.textProperty().bind(bjtf.errorMessageProperty());
        } else if (control instanceof BisqJfxPasswordField bjpf) {
            errorLabel.textProperty().bind(bjpf.errorMessageProperty());
        }
        errorLabel.visibleProperty().bind(errorLabel.textProperty().isNotEmpty());

        getChildren().addAll(line, focusedLine, topLabel, errorLabel);

        // Float / colour transitions when focus or content changes.
        focusListener = (o, ov, nv) -> { animateFocus(); updateFloatAnim(); };
        textListener = (o, ov, nv) -> updateFloatAnim();
        labelFloatListener = (o, ov, nv) -> updateFloatAnim();
        control.focusedProperty().addListener(focusListener);
        control.textProperty().addListener(textListener);
        if (isFloatCapable) {
            ((LabelFloatable) control).labelFloatProperty().addListener(labelFloatListener);
        }
        applyInitialFloatState();
    }

    @Override
    public void dispose() {
        TextField c = getSkinnable();
        if (c != null) {
            c.focusedProperty().removeListener(focusListener);
            c.textProperty().removeListener(textListener);
            if (isFloatCapable) {
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

    private void applyInitialFloatState() {
        if (!isFloating()) return;
        // Defer so prefHeight is measurable.
        javafx.application.Platform.runLater(() -> {
            double labelH = topLabel.prefHeight(-1);
            promptScaleTransform.setX(0.85);
            promptScaleTransform.setY(0.85);
            promptOffsetY.set(-labelH);
            topLabel.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("floating"), true);
        });
    }

    private void updateFloatAnim() {
        boolean shouldFloat = isFloating();
        double targetScale = shouldFloat ? 0.85 : 1.0;
        // Negative translateY lifts the label above the field's content area.
        double labelH = topLabel.prefHeight(-1);
        double targetY = shouldFloat ? -labelH : 0;
        floatAnim.stop();
        floatAnim.getKeyFrames().setAll(new KeyFrame(ANIM_DURATION,
                new KeyValue(promptScaleTransform.xProperty(), targetScale, Interpolator.EASE_BOTH),
                new KeyValue(promptScaleTransform.yProperty(), targetScale, Interpolator.EASE_BOTH),
                new KeyValue(promptOffsetY, targetY, Interpolator.EASE_BOTH)));
        floatAnim.play();
        topLabel.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("floating"),
                shouldFloat);
    }

    private boolean isFloating() {
        TextField c = getSkinnable();
        if (c == null) return false;
        if (c.isFocused()) return true;
        String t = c.getText();
        return t != null && !t.isEmpty();
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
        double cw = getSkinnable().getWidth();
        double ch = getSkinnable().getHeight();
        double lineY = ch - LINE_HEIGHT;
        line.resizeRelocate(-inputLineExtension, lineY,
                cw + inputLineExtension * 2, LINE_HEIGHT);
        double focusedY = ch - FOCUSED_LINE_HEIGHT;
        focusedLine.resizeRelocate(-inputLineExtension, focusedY,
                cw + inputLineExtension * 2, FOCUSED_LINE_HEIGHT);
        // Floating label baseline INSIDE the field. translateY (driven by promptOffsetY) lifts
        // it above when focused/filled.
        if (topLabel.isVisible()) {
            double labelH = topLabel.prefHeight(-1);
            topLabel.resizeRelocate(x, y + (h - labelH) / 2, w, labelH);
        }
        if (errorLabel.isVisible()) {
            double errH = errorLabel.prefHeight(cw);
            errorLabel.resizeRelocate(0, ch + 3, cw, errH);
        }
    }
}
