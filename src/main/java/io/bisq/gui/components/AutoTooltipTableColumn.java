package io.bisq.gui.components;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bisq.common.UserThread;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.HBox;
import org.controlsfx.control.PopOver;

import java.util.concurrent.TimeUnit;

public class AutoTooltipTableColumn<S, T> extends TableColumn<S, T> {

    private Label helpIcon;
    private Boolean hidePopover;
    private PopOver infoPopover;


    public AutoTooltipTableColumn(String text) {
        super();

        setTitle(text);
    }

    public AutoTooltipTableColumn(String text, String help) {

        setTitleWithHelpText(text, help);
    }

    public void setTitle(String title) {
        setGraphic(new AutoTooltipLabel(title));
    }

    public void setTitleWithHelpText(String title, String help) {

        final AutoTooltipLabel label = new AutoTooltipLabel(title);

        helpIcon = new Label();
        AwesomeDude.setIcon(helpIcon, AwesomeIcon.QUESTION_SIGN, "1em");
        helpIcon.setOpacity(0.4);
        helpIcon.setOnMouseEntered(e -> {
            hidePopover = false;
            final Label helpLabel = new Label(help);
            helpLabel.setMaxWidth(300);
            helpLabel.setWrapText(true);
            helpLabel.setPadding(new Insets(10));
            showInfoPopOver(helpLabel);
        });
        helpIcon.setOnMouseExited(e -> {
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

        final HBox hBox = new HBox(label, helpIcon);
        hBox.setStyle("-fx-alignment: center");
        hBox.setSpacing(4);
        setGraphic(hBox);
    }

    private void showInfoPopOver(Node node) {
        node.getStyleClass().add("default-text");

        infoPopover = new PopOver(node);
        if (helpIcon.getScene() != null) {
            infoPopover.setDetachable(false);
            infoPopover.setArrowLocation(PopOver.ArrowLocation.LEFT_CENTER);

            infoPopover.show(helpIcon, -10);
        }
    }
}
