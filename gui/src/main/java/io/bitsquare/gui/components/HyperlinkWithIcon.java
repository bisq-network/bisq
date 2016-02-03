package io.bitsquare.gui.components;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;

public class HyperlinkWithIcon extends HBox {
    private final Hyperlink hyperlink;
    private final Label openLinkIcon;

    public HyperlinkWithIcon(String text, AwesomeIcon awesomeIcon) {
        this(text, awesomeIcon, false);
    }

    public HyperlinkWithIcon(String text, boolean isCentered) {
        this(text, AwesomeIcon.INFO_SIGN, isCentered);
    }

    public HyperlinkWithIcon(String text, AwesomeIcon awesomeIcon, boolean isCentered) {
        setSpacing(5);
        hyperlink = new Hyperlink(text);

        openLinkIcon = new Label();
        openLinkIcon.getStyleClass().add("external-link-icon");

        AwesomeDude.setIcon(openLinkIcon, awesomeIcon);
        openLinkIcon.setMinWidth(20);
        HBox.setMargin(openLinkIcon, new Insets(awesomeIcon == AwesomeIcon.INFO_SIGN ? 2 : 3, 0, 0, 0));

        if (isCentered) {
            Pane spacer1 = new Pane();
            spacer1.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(spacer1, Priority.ALWAYS);

            Pane spacer2 = new Pane();
            spacer2.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(spacer2, Priority.ALWAYS);

            getChildren().addAll(spacer1, hyperlink, openLinkIcon, spacer2);
        } else {
            getChildren().addAll(hyperlink, openLinkIcon);
        }
    }

    public void setOnAction(EventHandler<ActionEvent> handler) {
        hyperlink.setOnAction(handler);
        openLinkIcon.setOnMouseClicked(e -> handler.handle(null));
    }

    public void setTooltip(Tooltip tooltip) {
        hyperlink.setTooltip(tooltip);
        // TODO does not use the right style
        openLinkIcon.setTooltip(tooltip);
    }
}
