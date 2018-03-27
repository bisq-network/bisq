package bisq.desktop.components;

import bisq.common.UserThread;

import de.jensd.fx.fontawesome.AwesomeIcon;

import org.controlsfx.control.PopOver;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.concurrent.TimeUnit;

import static bisq.desktop.util.FormBuilder.getIcon;

public class InfoInputTextField extends AnchorPane {

    private final StringProperty text = new SimpleStringProperty();

    private final InputTextField textField;
    private final Label infoIcon;
    private final Label warningIcon;
    private Label currentIcon;
    private PopOver popover;
    private boolean hidePopover;

    public InfoInputTextField() {
        super();

        textField = new InputTextField();

        infoIcon = getIcon(AwesomeIcon.INFO_SIGN);
        infoIcon.setLayoutY(3);
        infoIcon.getStyleClass().addAll("icon", "info");

        warningIcon = getIcon(AwesomeIcon.WARNING_SIGN);
        warningIcon.setLayoutY(3);
        warningIcon.getStyleClass().addAll("icon", "warning");

        AnchorPane.setLeftAnchor(infoIcon, 7.0);
        AnchorPane.setLeftAnchor(warningIcon, 7.0);
        AnchorPane.setRightAnchor(textField, 0.0);
        AnchorPane.setLeftAnchor(textField, 0.0);

        hideIcons();

        getChildren().addAll(textField, infoIcon, warningIcon);
    }

    private void hideIcons() {
        infoIcon.setManaged(false);
        infoIcon.setVisible(false);
        warningIcon.setManaged(false);
        warningIcon.setVisible(false);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setContentForInfoPopOver(Node node) {
        currentIcon = infoIcon;

        hideIcons();
        setActionHandlers(node);
    }

    public void setContentForWarningPopOver(Node node) {
        currentIcon = warningIcon;

        hideIcons();
        setActionHandlers(node);
    }

    private void setActionHandlers(Node node) {

        currentIcon.setManaged(true);
        currentIcon.setVisible(true);

        // As we don't use binding here we need to recreate it on mouse over to reflect the current state
        currentIcon.setOnMouseEntered(e -> {
            hidePopover = false;
            showPopOver(node);
        });
        currentIcon.setOnMouseExited(e -> {
            if (popover != null)
                popover.hide();
            hidePopover = true;
            UserThread.runAfter(() -> {
                if (hidePopover) {
                    popover.hide();
                    hidePopover = false;
                }
            }, 250, TimeUnit.MILLISECONDS);
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void showPopOver(Node node) {
        node.getStyleClass().add("default-text");

        popover = new PopOver(node);
        if (currentIcon.getScene() != null) {
            popover.setDetachable(false);
            popover.setArrowLocation(PopOver.ArrowLocation.LEFT_TOP);
            popover.setArrowIndent(5);

            popover.show(currentIcon, -17);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters/Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public InputTextField getTextField() { return textField; }

    public void setText(String text) {
        this.text.set(text);
    }

    public String getText() {
        return text.get();
    }

}
