package io.bisq.gui.components;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bisq.common.UserThread;
import io.bisq.common.locale.Res;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import org.controlsfx.control.PopOver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class InfoTextField extends AnchorPane {
    public static final Logger log = LoggerFactory.getLogger(InfoTextField.class);

    private final StringProperty text = new SimpleStringProperty();
    protected final Label infoIcon;
    protected final TextField textField;
    private Boolean hidePopover;
    private PopOver infoPopover;

    public InfoTextField() {
        textField = new TextField();
        textField.setEditable(false);
        textField.textProperty().bind(text);
        textField.setFocusTraversable(false);

        infoIcon = new Label();
        infoIcon.setLayoutY(3);
        infoIcon.getStyleClass().addAll("icon", "info");
        AwesomeDude.setIcon(infoIcon, AwesomeIcon.INFO_SIGN);

        AnchorPane.setRightAnchor(infoIcon, 7.0);
        AnchorPane.setRightAnchor(textField, 0.0);
        AnchorPane.setLeftAnchor(textField, 0.0);

        getChildren().addAll(textField, infoIcon);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setContentForInfoPopOver(Node node) {
        // As we don't use binding here we need to recreate it on mouse over to reflect the current state
        infoIcon.setOnMouseEntered(e -> {
            hidePopover = false;
            showInfoPopOver(node);
        });
        infoIcon.setOnMouseExited(e -> {
            if (infoPopover != null)
                infoPopover.hide();
            hidePopover = true;
            UserThread.runAfter(() -> {
                if (hidePopover) {
                    infoPopover.hide();
                    hidePopover = false;
                }
            }, 250, TimeUnit.MILLISECONDS);
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void showInfoPopOver(Node node) {
        node.getStyleClass().add("default-text");

        infoPopover = new PopOver(node);
        if (infoIcon.getScene() != null) {
            infoPopover.setDetachable(false);
            infoPopover.setArrowLocation(PopOver.ArrowLocation.RIGHT_TOP);
            infoPopover.setArrowIndent(5);

            infoPopover.show(infoIcon, -17);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters/Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setText(String text) {
        this.text.set(text);
    }

    public String getText() {
        return text.get();
    }

    public StringProperty textProperty() {
        return text;
    }
}
