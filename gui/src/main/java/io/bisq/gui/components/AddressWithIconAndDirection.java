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

    public AddressWithIconAndDirection(String text, String address, AwesomeIcon awesomeIcon, boolean received) {
        Label directionIcon = new Label();
        directionIcon.getStyleClass().add("icon");
        directionIcon.getStyleClass().add(received ? "received-funds-icon" : "sent-funds-icon");
        AwesomeDude.setIcon(directionIcon, received ? AwesomeIcon.SIGNIN : AwesomeIcon.SIGNOUT);
        if (received)
            directionIcon.setRotate(180);
        directionIcon.setMouseTransparent(true);

        HBox hBox = new HBox();
        hBox.setSpacing(-1);
        Label label = new AutoTooltipLabel(text);
        label.setMouseTransparent(true);
        HBox.setMargin(label, new Insets(5, 0, 0, 0));
        HBox.setHgrow(label, Priority.ALWAYS);

        hyperlink = new HyperlinkWithIcon(address, awesomeIcon);
        HBox.setMargin(hyperlink, new Insets(0, 0, 0, 0));
        HBox.setHgrow(hyperlink, Priority.SOMETIMES);
        // You need to set max width to Double.MAX_VALUE to make HBox.setHgrow working like expected!
        // also pref width needs to be not default (-1)
        hyperlink.setMaxWidth(Double.MAX_VALUE);
        hyperlink.setPrefWidth(0);

        hBox.getChildren().addAll(label, hyperlink);

        AnchorPane.setLeftAnchor(directionIcon, 3.0);
        AnchorPane.setTopAnchor(directionIcon, 4.0);
        AnchorPane.setLeftAnchor(hBox, 22.0);
        AnchorPane.setRightAnchor(hBox, 15.0);
        getChildren().addAll(directionIcon, hBox);
    }

    public void setOnAction(EventHandler<ActionEvent> handler) {
        hyperlink.setOnAction(handler);
    }

    public void setTooltip(Tooltip tooltip) {
        hyperlink.setTooltip(tooltip);
    }
}
