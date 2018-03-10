package io.bisq.gui.components;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import javafx.geometry.Insets;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;

public class HyperlinkWithIcon extends Hyperlink {

    public HyperlinkWithIcon(String text) {
        this(text, AwesomeIcon.INFO_SIGN);
    }

    public HyperlinkWithIcon(String text, AwesomeIcon awesomeIcon) {

        super(text);

        Label icon = new Label();
        AwesomeDude.setIcon(icon, awesomeIcon);
        icon.setMinWidth(20);
        icon.setOpacity(0.7);
        icon.getStyleClass().add("hyperlink");
        icon.setPadding(new Insets(0));

        setGraphic(icon);
        setContentDisplay(ContentDisplay.RIGHT);
        setGraphicTextGap(7.0);

        //TODO: replace workaround of setting the style this way
        tooltipProperty().addListener((observable, oldValue, newValue) -> newValue.setStyle("-fx-text-fill: -bs-black"));
    }

    public void clear() {
        setText("");
        setGraphic(null);
    }
}
