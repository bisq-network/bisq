package io.bisq.gui.main.account.content;

import io.bisq.gui.main.MainView;
import javafx.geometry.HPos;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class ContentSettings {
    public static void setDefaultSettings(GridPane node, double minWidth) {
        node.setHgap(MainView.scale(5));
        node.setVgap(MainView.scale(5));
        AnchorPane.setTopAnchor(node, MainView.scale(20));
        AnchorPane.setRightAnchor(node, MainView.scale(25));
        AnchorPane.setBottomAnchor(node, MainView.scale(0));
        AnchorPane.setLeftAnchor(node, MainView.scale(20));
        node.getColumnConstraints().add(new ColumnConstraints() {{
            setHgrow(Priority.SOMETIMES);
            setHalignment(HPos.RIGHT);
            setMinWidth(MainView.scale(minWidth));
        }});
        node.getColumnConstraints().add(new ColumnConstraints() {{
            setHgrow(Priority.ALWAYS);
            setMinWidth(MainView.scale(300));
        }});
    }
}
