package bisq.desktop.components;

import com.jfoenix.adapters.ReflectionHelper;
import com.jfoenix.controls.JFXTextArea;
import com.jfoenix.skins.PromptLinesWrapper;
import com.jfoenix.skins.ValidationPane;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.skin.TextAreaSkin;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import javafx.geometry.Insets;

import java.util.Arrays;

import java.lang.reflect.Field;

/**
 * Code copied and adapted from com.jfoenix.skins.JFXTextAreaSkin
 */

public class JFXTextAreaSkinBisqStyle extends TextAreaSkin {

    private boolean invalid = true;

    private ScrollPane scrollPane;
    private Text promptText;

    private ValidationPane<JFXTextArea> errorContainer;
    private PromptLinesWrapper<JFXTextArea> linesWrapper;

    public JFXTextAreaSkinBisqStyle(JFXTextArea textArea) {
        super(textArea);
        // init text area properties
        scrollPane = (ScrollPane) getChildren().get(0);
        textArea.setWrapText(true);

        linesWrapper = new PromptLinesWrapper<>(
                textArea,
                promptTextFillProperty(),
                textArea.textProperty(),
                textArea.promptTextProperty(),
                () -> promptText);

        linesWrapper.init(() -> createPromptNode(), scrollPane);
        errorContainer = new ValidationPane<>(textArea);
        getChildren().addAll(linesWrapper.line, linesWrapper.focusedLine, linesWrapper.promptContainer, errorContainer);

        registerChangeListener(textArea.disableProperty(), obs -> linesWrapper.updateDisabled());
        registerChangeListener(textArea.focusColorProperty(), obs -> linesWrapper.updateFocusColor());
        registerChangeListener(textArea.unFocusColorProperty(), obs -> linesWrapper.updateUnfocusColor());
        registerChangeListener(textArea.disableAnimationProperty(), obs -> errorContainer.updateClip());

    }


    @Override
    protected void layoutChildren(final double x, final double y, final double w, final double h) {
        super.layoutChildren(x, y, w, h);

        final double height = getSkinnable().getHeight();
        final double width = getSkinnable().getWidth();
        linesWrapper.layoutLines(x - 2, y - 2, width, h, height, promptText == null ? 0 : promptText.getLayoutBounds().getHeight() + 3);
        errorContainer.layoutPane(x, height + linesWrapper.focusedLine.getHeight(), width, h);
        linesWrapper.updateLabelFloatLayout();

        if (invalid) {
            invalid = false;
            // set the default background of text area viewport to white
            Region viewPort = (Region) scrollPane.getChildrenUnmodifiable().get(0);
            viewPort.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT,
                    CornerRadii.EMPTY,
                    Insets.EMPTY)));
            // reapply css of scroll pane in case set by the user
            viewPort.applyCss();
            errorContainer.invalid(w);
            // focus
            linesWrapper.invalid();
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
        promptText.getTransforms().add(linesWrapper.promptTextScale);
        linesWrapper.promptContainer.getChildren().add(promptText);
        if (getSkinnable().isFocused() && ((JFXTextArea) getSkinnable()).isLabelFloat()) {
            promptText.setTranslateY(-Math.floor(scrollPane.getHeight()));
            linesWrapper.promptTextScale.setX(0.85);
            linesWrapper.promptTextScale.setY(0.85);
        }

        try {
            Field field = ReflectionHelper.getField(TextAreaSkin.class, "promptNode");
            Object oldValue = field.get(this);
            if (oldValue != null) {
                removeHighlight(Arrays.asList(((Node) oldValue)));
            }
            field.set(this, promptText);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

