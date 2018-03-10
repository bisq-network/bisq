package io.bisq.gui.components;

import javafx.scene.control.TableColumn;

public class AutoTooltipTableColumn<S,T> extends TableColumn<S,T> {

    public AutoTooltipTableColumn(String text) {
        super();

        setGraphic(new AutoTooltipLabel(text));
    }
}
