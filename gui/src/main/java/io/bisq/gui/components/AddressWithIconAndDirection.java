package io.bisq.gui.components;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddressWithIconAndDirection extends AnchorPane {
    private static final Logger log = LoggerFactory.getLogger(AddressWithIconAndDirection.class);
    private final Hyperlink hyperlink;
    private final Label openLinkIcon;

    public AddressWithIconAndDirection(String text, String address, AwesomeIcon awesomeIcon, boolean received) {
        Label directionIcon = new Label();
        directionIcon.setLayoutY(3);
        directionIcon.getStyleClass().add(received ? "received-funds-icon" : "sent-funds-icon");
        AwesomeDude.setIcon(directionIcon, received ? AwesomeIcon.SIGNIN : AwesomeIcon.SIGNOUT);
        directionIcon.setMouseTransparent(true);

        HBox hBox = new HBox();
        hBox.setSpacing(-1);
        Label label = new Label(text);
        label.setMouseTransparent(true);
        HBox.setMargin(label, new Insets(4, 0, 0, 0));
        HBox.setHgrow(label, Priority.ALWAYS);

        hyperlink = new Hyperlink(address);
        HBox.setMargin(hyperlink, new Insets(1, 0, 0, 0));
        HBox.setHgrow(hyperlink, Priority.SOMETIMES);
        // You need to set max width to Double.MAX_VALUE to make HBox.setHgrow working like expected!
        // also pref width needs to be not default (-1)
        hyperlink.setMaxWidth(Double.MAX_VALUE);
        hyperlink.setPrefWidth(0);

        hBox.getChildren().addAll(label, hyperlink);

        openLinkIcon = new Label();
        openLinkIcon.setLayoutY(3);
        openLinkIcon.getStyleClass().add("external-link-icon");
        openLinkIcon.setOpacity(0.7);
        AwesomeDude.setIcon(openLinkIcon, awesomeIcon);

        AnchorPane.setLeftAnchor(directionIcon, 3.0);
        AnchorPane.setTopAnchor(directionIcon, 2.0);
        AnchorPane.setLeftAnchor(hBox, 22.0);
        AnchorPane.setRightAnchor(hBox, 15.0);
        AnchorPane.setRightAnchor(openLinkIcon, 4.0);
        AnchorPane.setTopAnchor(openLinkIcon, 3.0);

        getChildren().addAll(directionIcon, hBox, openLinkIcon);
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
