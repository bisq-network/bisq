package io.bisq.gui.components;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
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
        icon.getStyleClass().addAll("icon", "highlight");
        AwesomeDude.setIcon(icon, awesomeIcon);
        icon.setMinWidth(20);
        icon.setOpacity(0.7);

        setGraphic(icon);
        setContentDisplay(ContentDisplay.RIGHT);
    }
}
