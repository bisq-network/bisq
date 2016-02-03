package io.bitsquare.gui.components;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;

public class HyperlinkWithIcon extends AnchorPane {
    private final Hyperlink hyperlink;
    private final Label openLinkIcon;

    public HyperlinkWithIcon(String text, AwesomeIcon awesomeIcon) {
        hyperlink = new Hyperlink(text);

        openLinkIcon = new Label();
        openLinkIcon.setLayoutY(3);
        openLinkIcon.getStyleClass().add("external-link-icon");
        AwesomeDude.setIcon(openLinkIcon, awesomeIcon);

        AnchorPane.setLeftAnchor(hyperlink, 0.0);
        AnchorPane.setRightAnchor(hyperlink, 15.0);
        AnchorPane.setRightAnchor(openLinkIcon, 4.0);
        AnchorPane.setTopAnchor(openLinkIcon, 3.0);

        getChildren().addAll(hyperlink, openLinkIcon);
    }

    public void setOnAction(EventHandler<ActionEvent> handler) {
        hyperlink.setOnAction(handler);
        openLinkIcon.setOnMouseClicked(e -> handler.handle(null));
    }

    public void setTooltip(Tooltip tooltip) {
        hyperlink.setTooltip(tooltip);
        openLinkIcon.setTooltip(tooltip);
    }
}
