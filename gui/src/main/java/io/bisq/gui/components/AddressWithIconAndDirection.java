package io.bisq.gui.components;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bisq.gui.main.MainView;
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
        HBox.setMargin(label, new Insets(MainView.scale(4), MainView.scale(0), MainView.scale(0), MainView.scale(0)));
        HBox.setHgrow(label, Priority.ALWAYS);

        hyperlink = new Hyperlink(address);
        HBox.setMargin(hyperlink, new Insets(1, 0, 0, 0));
        HBox.setHgrow(hyperlink, Priority.SOMETIMES);
        // You need to set max width to Double.MAX_VALUE to make HBox.setHgrow working like expected!
        // also pref width needs to be not default (-1)
        hyperlink.setMaxWidth(Double.MAX_VALUE);
        hyperlink.setPrefWidth(MainView.scale(0));

        hBox.getChildren().addAll(label, hyperlink);

        openLinkIcon = new Label();
        openLinkIcon.setLayoutY(MainView.scale(3));
        openLinkIcon.getStyleClass().add("external-link-icon");
        openLinkIcon.setOpacity(0.7);
        AwesomeDude.setIcon(openLinkIcon, awesomeIcon);

        AnchorPane.setLeftAnchor(directionIcon, MainView.scale(3));
        AnchorPane.setTopAnchor(directionIcon, MainView.scale(2));
        AnchorPane.setLeftAnchor(hBox, MainView.scale(22));
        AnchorPane.setRightAnchor(hBox, MainView.scale(15));
        AnchorPane.setRightAnchor(openLinkIcon, MainView.scale(4));
        AnchorPane.setTopAnchor(openLinkIcon, MainView.scale(3));

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
