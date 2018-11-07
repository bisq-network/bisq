/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.components;

import bisq.desktop.util.FormBuilder;

import bisq.core.locale.Res;

import bisq.common.UserThread;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.Parent;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.GridPane;
import javafx.scene.text.TextFlow;

import javafx.geometry.Insets;
import javafx.geometry.VPos;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

/**
 * Convenience Component for info icon, info text and link display in a GridPane.
 * Only the properties needed are supported.
 * We need to extend from Parent so we can use it in FXML, but the InfoDisplay is not used as node,
 * but add the children nodes to the gridPane.
 */
public class InfoDisplay extends Parent {

    private final StringProperty text = new SimpleStringProperty();
    private final IntegerProperty rowIndex = new SimpleIntegerProperty(0);
    private final IntegerProperty columnIndex = new SimpleIntegerProperty(0);
    private final ObjectProperty<EventHandler<ActionEvent>> onAction = new SimpleObjectProperty<>();
    private final ObjectProperty<GridPane> gridPane = new SimpleObjectProperty<>();

    private boolean useReadMore;

    private final Label icon = FormBuilder.getIcon(AwesomeIcon.INFO_SIGN);
    private final TextFlow textFlow;
    private final Label label;
    private final Hyperlink link;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public InfoDisplay() {
        icon.setId("non-clickable-icon");
        icon.visibleProperty().bind(visibleProperty());

        GridPane.setValignment(icon, VPos.TOP);
        GridPane.setMargin(icon, new Insets(-2, 0, 0, 0));
        GridPane.setRowSpan(icon, 2);

        label = new AutoTooltipLabel();
        label.textProperty().bind(text);
        label.setTextOverrun(OverrunStyle.WORD_ELLIPSIS);
        // width is set a frame later so we hide it first
        label.setVisible(false);

        link = new Hyperlink(Res.get("shared.readMore"));
        link.setPadding(new Insets(0, 0, 0, -2));

        // We need that to know if we have a wrapping or not.
        // Did not find a way to get that from the API.
        Label testLabel = new AutoTooltipLabel();
        testLabel.textProperty().bind(text);

        textFlow = new TextFlow();
        textFlow.visibleProperty().bind(visibleProperty());
        textFlow.getChildren().addAll(testLabel);

        testLabel.widthProperty().addListener((ov, o, n) -> {
            useReadMore = (double) n > textFlow.getWidth();
            link.setText(Res.get(useReadMore ? "shared.readMore" : "shared.openHelp"));
            UserThread.execute(() -> textFlow.getChildren().setAll(label, link));
        });

        // update the width when the window gets resized
        ChangeListener<Number> listener = (ov2, oldValue2, windowWidth) -> {
            if (label.prefWidthProperty().isBound())
                label.prefWidthProperty().unbind();
            label.setPrefWidth((double) windowWidth - localToScene(0, 0).getX() - 35);
        };


        // when clicking "Read more..." we expand and change the link to the Help
        link.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                if (useReadMore) {

                    label.setWrapText(true);
                    link.setText(Res.get("shared.openHelp"));
                    getScene().getWindow().widthProperty().removeListener(listener);
                    if (label.prefWidthProperty().isBound())
                        label.prefWidthProperty().unbind();
                    label.prefWidthProperty().bind(textFlow.widthProperty());
                    link.setVisited(false);
                    // focus border is a bit confusing here so we remove it
                    link.getStyleClass().add("hide-focus");
                    link.setOnAction(onAction.get());
                    getParent().layout();
                } else {
                    onAction.get().handle(actionEvent);
                }
            }
        });

        sceneProperty().addListener((ov, oldValue, newValue) -> {
            if (oldValue == null && newValue != null && newValue.getWindow() != null) {
                newValue.getWindow().widthProperty().addListener(listener);
                // localToScene does deliver 0 instead of the correct x position when scene property gets set,
                // so we delay for 1 render cycle
                UserThread.execute(() -> {
                    label.setVisible(true);
                    label.prefWidthProperty().unbind();
                    label.setPrefWidth(newValue.getWindow().getWidth() - localToScene(0, 0).getX() - 35);
                });
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setText(String text) {
        this.text.set(text);
        UserThread.execute(() -> {
            if (getScene() != null) {
                label.setVisible(true);
                label.prefWidthProperty().unbind();
                label.setPrefWidth(getScene().getWindow().getWidth() - localToScene(0, 0).getX() - 35);
            }
        });
    }

    public void setGridPane(GridPane gridPane) {
        this.gridPane.set(gridPane);

        gridPane.getChildren().addAll(icon, textFlow);

        GridPane.setColumnIndex(icon, columnIndex.get());
        GridPane.setColumnIndex(textFlow, columnIndex.get() + 1);

        GridPane.setRowIndex(icon, rowIndex.get());
        GridPane.setRowIndex(textFlow, rowIndex.get());
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex.set(rowIndex);

        GridPane.setRowIndex(icon, rowIndex);
        GridPane.setRowIndex(textFlow, rowIndex);
    }

    public void setColumnIndex(int columnIndex) {
        this.columnIndex.set(columnIndex);

        GridPane.setColumnIndex(icon, columnIndex);
        GridPane.setColumnIndex(textFlow, columnIndex + 1);

    }

    public final void setOnAction(EventHandler<ActionEvent> eventHandler) {
        onAction.set(eventHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getText() {
        return text.get();
    }

    public StringProperty textProperty() {
        return text;
    }

    public int getColumnIndex() {
        return columnIndex.get();
    }

    public IntegerProperty columnIndexProperty() {
        return columnIndex;
    }

    public int getRowIndex() {
        return rowIndex.get();
    }

    public IntegerProperty rowIndexProperty() {
        return rowIndex;
    }

    public EventHandler<ActionEvent> getOnAction() {
        return onAction.get();
    }

    public ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }

    public GridPane getGridPane() {
        return gridPane.get();
    }

    public ObjectProperty<GridPane> gridPaneProperty() {
        return gridPane;
    }

}
