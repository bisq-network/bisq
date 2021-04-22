package bisq.desktop.components;

import com.jfoenix.adapters.ReflectionHelper;
import com.jfoenix.controls.base.IFXLabelFloatControl;
import com.jfoenix.skins.PromptLinesWrapper;
import com.jfoenix.skins.ValidationPane;

import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.TextFieldSkin;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;

import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ObservableDoubleValue;

import java.lang.reflect.Field;

/**
 * Code copied and adapted from com.jfoenix.skins.JFXTextFieldSkin
 */

public class JFXTextFieldSkinBisqStyle<T extends TextField & IFXLabelFloatControl> extends TextFieldSkin {

    private double inputLineExtension;
    private boolean invalid = true;

    private Text promptText;
    private Pane textPane;
    private Node textNode;
    private ObservableDoubleValue textRight;
    private DoubleProperty textTranslateX;

    private ValidationPane<T> errorContainer;
    private PromptLinesWrapper<T> linesWrapper;

    public JFXTextFieldSkinBisqStyle(T textField, double inputLineExtension) {
        super(textField);
        textPane = (Pane) this.getChildren().get(0);
        this.inputLineExtension = inputLineExtension;

        // get parent fields
        textNode = ReflectionHelper.getFieldContent(TextFieldSkin.class, this, "textNode");
        textTranslateX = ReflectionHelper.getFieldContent(TextFieldSkin.class, this, "textTranslateX");
        textRight = ReflectionHelper.getFieldContent(TextFieldSkin.class, this, "textRight");

        linesWrapper = new PromptLinesWrapper<T>(
                textField,
                promptTextFillProperty(),
                textField.textProperty(),
                textField.promptTextProperty(),
                () -> promptText);

        linesWrapper.init(() -> createPromptNode(), textPane);

        ReflectionHelper.setFieldContent(TextFieldSkin.class, this, "usePromptText", linesWrapper.usePromptText);

        errorContainer = new ValidationPane<>(textField);

        getChildren().addAll(linesWrapper.line, linesWrapper.focusedLine, linesWrapper.promptContainer, errorContainer);

        registerChangeListener(textField.disableProperty(), obs -> linesWrapper.updateDisabled());
        registerChangeListener(textField.focusColorProperty(), obs -> linesWrapper.updateFocusColor());
        registerChangeListener(textField.unFocusColorProperty(), obs -> linesWrapper.updateUnfocusColor());
        registerChangeListener(textField.disableAnimationProperty(), obs -> errorContainer.updateClip());
    }

    @Override
    protected void layoutChildren(final double x, final double y, final double w, final double h) {
        super.layoutChildren(x, y, w, h);

        final double height = getSkinnable().getHeight();
        final double width = getSkinnable().getWidth() + inputLineExtension;
        final double paddingLeft = getSkinnable().getPadding().getLeft();
        linesWrapper.layoutLines(x, y, width, h, height, Math.floor(h));
        errorContainer.layoutPane(x - paddingLeft, height + linesWrapper.focusedLine.getHeight(), width, h);

        if (getSkinnable().getWidth() > 0) {
            updateTextPos();
        }

        linesWrapper.updateLabelFloatLayout();

        if (invalid) {
            invalid = false;
            // update validation container
            errorContainer.invalid(w);
            // focus
            linesWrapper.invalid();
        }
    }


    private void updateTextPos() {
        double textWidth = textNode.getLayoutBounds().getWidth();
        final double promptWidth = promptText == null ? 0 : promptText.getLayoutBounds().getWidth();
        switch (getSkinnable().getAlignment().getHpos()) {
            case CENTER:
                linesWrapper.promptTextScale.setPivotX(promptWidth / 2);
                double midPoint = textRight.get() / 2;
                double newX = midPoint - textWidth / 2;
                if (newX + textWidth <= textRight.get()) {
                    textTranslateX.set(newX);
                }
                break;
            case LEFT:
                linesWrapper.promptTextScale.setPivotX(0);
                break;
            case RIGHT:
                linesWrapper.promptTextScale.setPivotX(promptWidth);
                break;
        }

    }

    private void createPromptNode() {
        if (promptText != null || !linesWrapper.usePromptText.get()) {
            return;
        }
        promptText = new Text();
        promptText.setManaged(false);
        promptText.getStyleClass().add("text");
        promptText.visibleProperty().bind(linesWrapper.usePromptText);
        promptText.fontProperty().bind(getSkinnable().fontProperty());
        promptText.textProperty().bind(getSkinnable().promptTextProperty());
        promptText.fillProperty().bind(linesWrapper.animatedPromptTextFill);
        promptText.setLayoutX(1);
        promptText.getTransforms().add(linesWrapper.promptTextScale);
        linesWrapper.promptContainer.getChildren().add(promptText);
        if (getSkinnable().isFocused() && ((IFXLabelFloatControl) getSkinnable()).isLabelFloat()) {
            promptText.setTranslateY(-Math.floor(textPane.getHeight()));
            linesWrapper.promptTextScale.setX(0.85);
            linesWrapper.promptTextScale.setY(0.85);
        }

        try {
            Field field = ReflectionHelper.getField(TextFieldSkin.class, "promptNode");
            Object oldValue = field.get(this);
            if (oldValue != null) {
                textPane.getChildren().remove(oldValue);
            }
            field.set(this, promptText);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
