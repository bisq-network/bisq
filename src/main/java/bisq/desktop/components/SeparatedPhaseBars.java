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

import bisq.core.dao.DaoPeriodService;

import bisq.common.UserThread;
import bisq.common.locale.Res;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SeparatedPhaseBars extends HBox {

    private int totalDuration;
    private List<SeparatedPhaseBarsItem> items;
    private VBox vBoxLabels;

    public SeparatedPhaseBars(List<SeparatedPhaseBarsItem> items) {
        this.items = items;
        setSpacing(0);

        addLabels();

        items.stream().forEach(item -> {
            Label titleLabel = new Label(Res.get("dao.phase.short." + item.phase));
            item.setTitleLabel(titleLabel);

            Label startLabel = new Label();
            item.startValueProperty.addListener((observable, oldValue, newValue) -> {
                startLabel.setText(String.valueOf((int) newValue));
            });
            startLabel.setVisible(item.isShowBlocks());
            AnchorPane startLabelPane = new AnchorPane();
            AnchorPane.setLeftAnchor(startLabel, 0d);
            startLabelPane.getChildren().add(startLabel);

            Label endLabel = new Label();
            item.endValueProperty.addListener((observable, oldValue, newValue) -> {
                endLabel.setText(String.valueOf((int) newValue));
            });
            endLabel.setVisible(item.isShowBlocks());
            AnchorPane endLabelPane = new AnchorPane();
            AnchorPane.setRightAnchor(endLabel, 0d);
            endLabelPane.getChildren().add(endLabel);

            ProgressBar progressBar = new ProgressBar();
            progressBar.setMinHeight(9);
            progressBar.setMaxHeight(9);
            progressBar.setMaxWidth(Double.MAX_VALUE);
            progressBar.setStyle("-fx-accent: -bs-green;");
            progressBar.progressProperty().bind(item.progressProperty);
            progressBar.setOpacity(item.isShowBlocks() ? 1 : 0.25);

            VBox vBox = new VBox();
            vBox.setSpacing(5);
            vBox.getChildren().addAll(titleLabel, progressBar, startLabelPane, endLabelPane);
            vBox.setAlignment(Pos.CENTER);
            getChildren().add(vBox);
            item.setVBox(vBox);
        });

        widthProperty().addListener((observable, oldValue, newValue) -> {
            adjustWidth((double) newValue);
        });
        UserThread.execute(() -> adjustWidth(getWidth()));
    }

    private void addLabels() {
        Label titleLabel = new Label(Res.get("dao.proposal.active.phase"));

        Label startLabel = new Label(Res.get("dao.proposal.active.startBlock"));
        AnchorPane startLabelPane = new AnchorPane();
        AnchorPane.setLeftAnchor(startLabel, 0d);
        startLabelPane.getChildren().add(startLabel);

        Label endLabel = new Label(Res.get("dao.proposal.active.endBlock"));
        AnchorPane endLabelPane = new AnchorPane();
        AnchorPane.setRightAnchor(endLabel, 0d);
        endLabelPane.getChildren().add(endLabel);

        ProgressBar progressBar = new ProgressBar();
        progressBar.setMinHeight(9);
        progressBar.setMaxHeight(9);
        progressBar.setVisible(false);

        vBoxLabels = new VBox();
        vBoxLabels.setSpacing(5);
        vBoxLabels.getChildren().addAll(titleLabel, progressBar, startLabelPane, endLabelPane);
        vBoxLabels.setAlignment(Pos.CENTER);
        vBoxLabels.setPadding(new Insets(0, 10, 0, 0));
        getChildren().add(vBoxLabels);
    }

    private void adjustWidth(double availableWidth) {
        totalDuration = items.stream().mapToInt(SeparatedPhaseBarsItem::getDuration).sum();
        availableWidth -= vBoxLabels.getWidth();
        if (availableWidth > 0 && totalDuration > 0) {
            final double finalAvailableWidth = availableWidth;
            items.stream().forEach(item -> {
                final double width = (double) item.duration / (double) totalDuration * finalAvailableWidth;
                item.getVBox().setPrefWidth(width);
            });
        }
    }

    @Getter
    public static class SeparatedPhaseBarsItem {
        private final DaoPeriodService.Phase phase;
        private final boolean showBlocks;
        private final IntegerProperty startValueProperty = new SimpleIntegerProperty();
        private final IntegerProperty endValueProperty = new SimpleIntegerProperty();
        private final DoubleProperty progressProperty = new SimpleDoubleProperty();
        private int duration;
        @Setter
        private javafx.scene.layout.VBox VBox;
        @Setter
        private Label titleLabel;

        public SeparatedPhaseBarsItem(DaoPeriodService.Phase phase, boolean showBlocks) {
            this.phase = phase;
            this.showBlocks = showBlocks;
        }

        public void setInActive() {
            titleLabel.setStyle("-fx-text-fill: black;");
        }

        public void setActive() {
            titleLabel.setStyle("-fx-text-fill: -fx-accent;");
        }

        public void setStartAndEnd(int startBlock, int endBlock) {
            startValueProperty.set(startBlock);
            endValueProperty.set(endBlock);
            duration = endValueProperty.get() - startValueProperty.get() + 1;
        }
    }
}
