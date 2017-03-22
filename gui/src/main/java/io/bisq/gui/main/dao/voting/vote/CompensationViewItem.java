/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.dao.voting.vote;

import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bisq.common.locale.Res;
import io.bisq.core.dao.compensation.CompensationRequest;
import io.bisq.core.dao.vote.CompensationRequestVoteItem;
import io.bisq.gui.components.HyperlinkWithIcon;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.dao.compensation.CompensationRequestDisplay;
import io.bisq.gui.util.Layout;
import io.bisq.protobuffer.payload.dao.compensation.CompensationRequestPayload;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CompensationViewItem {
    private static final Logger log = LoggerFactory.getLogger(CompensationViewItem.class);

    private static final List<CompensationViewItem> instances = new ArrayList<>();

    private final Button removeButton;
    private final CheckBox acceptCheckBox, declineCheckBox;
    public final CompensationRequestVoteItem compensationRequestVoteItem;
    private Pane owner;

    public static void attach(CompensationRequestVoteItem compensationRequestVoteItem, VBox vBox, DoubleProperty labelWidth, Runnable removeHandler) {
        instances.add(new CompensationViewItem(compensationRequestVoteItem, vBox, labelWidth, removeHandler));
    }

    public static void cleanupAllInstances() {
        instances.stream().forEach(CompensationViewItem::cleanupInstance);
    }

    public static boolean contains(CompensationRequestVoteItem selectedItem) {
        return instances.stream()
                .filter(e -> e.compensationRequestVoteItem.compensationRequest.getCompensationRequestPayload().uid.equals(
                        selectedItem.compensationRequest.getCompensationRequestPayload().uid))
                .findAny()
                .isPresent();
    }

    public static boolean isEmpty() {
        return instances.isEmpty();
    }

    private CompensationViewItem(CompensationRequestVoteItem compensationRequestVoteItem, VBox vBox, DoubleProperty labelWidth, Runnable removeHandler) {
        this.compensationRequestVoteItem = compensationRequestVoteItem;
        CompensationRequest compensationRequest = compensationRequestVoteItem.compensationRequest;
        CompensationRequestPayload compensationRequestPayload = compensationRequest.getCompensationRequestPayload();

        HBox hBox = new HBox();
        hBox.setSpacing(5);
        vBox.getChildren().add(hBox);

        String title = compensationRequestPayload.title + " (" + compensationRequestPayload.getShortId() + ")";

        HyperlinkWithIcon infoLabelWithLink = new HyperlinkWithIcon(title, AwesomeIcon.EXTERNAL_LINK);
        infoLabelWithLink.setPrefWidth(220);
        HBox.setMargin(infoLabelWithLink, new Insets(2, 0, 0, 0));

        infoLabelWithLink.setOnAction(e -> {
            GridPane gridPane = new GridPane();
            gridPane.setHgap(5);
            gridPane.setVgap(5);
            ColumnConstraints columnConstraints1 = new ColumnConstraints();
            columnConstraints1.setHalignment(HPos.RIGHT);
            columnConstraints1.setHgrow(Priority.SOMETIMES);
            columnConstraints1.setMinWidth(140);
            ColumnConstraints columnConstraints2 = new ColumnConstraints();
            columnConstraints2.setHgrow(Priority.ALWAYS);
            columnConstraints2.setMinWidth(300);
            gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
            AnchorPane anchorPane = new AnchorPane();
            anchorPane.getChildren().add(gridPane);
            AnchorPane.setBottomAnchor(gridPane, 25d);
            AnchorPane.setRightAnchor(gridPane, 25d);
            AnchorPane.setLeftAnchor(gridPane, 25d);
            AnchorPane.setTopAnchor(gridPane, -20d);


            CompensationRequestDisplay compensationRequestDisplay = new CompensationRequestDisplay(gridPane);
            compensationRequestDisplay.createAllFields(Res.get("dao.voting.item.title"), Layout.GROUP_DISTANCE);
            compensationRequestDisplay.setAllFieldsEditable(false);
            compensationRequestDisplay.fillWithData(compensationRequestPayload);

            Scene scene = new Scene(anchorPane);
            scene.getStylesheets().setAll(
                    "/io/bisq/gui/bisq.css",
                    "/io/bisq/gui/images.css");
            Stage stage = new Stage();
            stage.setTitle(Res.get("dao.voting.item.stage.title", compensationRequestPayload.getShortId()));
            stage.setScene(scene);
            if (owner == null)
                owner = MainView.getRootContainer();
            Scene rootScene = owner.getScene();
            stage.initOwner(rootScene.getWindow());
            stage.initModality(Modality.NONE);
            stage.initStyle(StageStyle.UTILITY);
            stage.show();


            Window window = rootScene.getWindow();
            double titleBarHeight = window.getHeight() - rootScene.getHeight();
            stage.setX(Math.round(window.getX() + (owner.getWidth() - stage.getWidth()) / 2) + 200);
            stage.setY(Math.round(window.getY() + titleBarHeight + (owner.getHeight() - stage.getHeight()) / 2) + 50);

        });

        acceptCheckBox = new CheckBox(Res.get("shared.accept"));
        HBox.setMargin(acceptCheckBox, new Insets(5, 0, 0, 0));

        declineCheckBox = new CheckBox(Res.get("shared.decline"));
        HBox.setMargin(declineCheckBox, new Insets(5, 0, 0, 0));


        acceptCheckBox.setOnAction(event -> {
            boolean selected = acceptCheckBox.isSelected();
            compensationRequestVoteItem.setAcceptedVote(selected);
            if (declineCheckBox.isSelected()) {
                declineCheckBox.setSelected(!selected);
                compensationRequestVoteItem.setDeclineVote(!selected);
            } else if (!selected) {
                compensationRequestVoteItem.setHasVoted(false);
            }

        });
        acceptCheckBox.setSelected(compensationRequestVoteItem.isAcceptedVote());

        declineCheckBox.setOnAction(event -> {
            boolean selected = declineCheckBox.isSelected();
            compensationRequestVoteItem.setDeclineVote(selected);
            if (acceptCheckBox.isSelected()) {
                acceptCheckBox.setSelected(!selected);
                compensationRequestVoteItem.setAcceptedVote(!selected);
            } else if (!selected) {
                compensationRequestVoteItem.setHasVoted(false);
            }

        });
        declineCheckBox.setSelected(compensationRequestVoteItem.isDeclineVote());

        removeButton = new Button(Res.get("shared.remove"));
        removeButton.setOnAction(event -> {
            vBox.getChildren().remove(hBox);
            cleanupInstance();
            instances.remove(this);
            removeHandler.run();
        });

        Pane spacer = new Pane();
        spacer.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        hBox.getChildren().addAll(infoLabelWithLink, acceptCheckBox, declineCheckBox, spacer, removeButton);
    }

    public void cleanupInstance() {
        acceptCheckBox.setOnAction(null);
        declineCheckBox.setOnAction(null);
        removeButton.setOnAction(null);
    }

}
