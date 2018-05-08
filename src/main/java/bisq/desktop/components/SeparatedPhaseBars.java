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

import bisq.core.dao.state.period.DaoPhase;
import bisq.core.locale.Res;

import bisq.common.UserThread;

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
public class SeparatedPhaseBars extends VBox {

    private int totalDuration;
    private List<SeparatedPhaseBarsItem> items;
    private VBox vBoxLabels;

    public SeparatedPhaseBars(List<SeparatedPhaseBarsItem> items) {
        this.items = items;
        setSpacing(10);

        HBox titlesBars = new HBox();
        titlesBars.setSpacing(5);
        getChildren().add(titlesBars);

        HBox progressBars = new HBox();
        progressBars.setSpacing(5);
        getChildren().add(progressBars);

        items.forEach(item -> {
            Label titleLabel = new Label(Res.get("dao.phase.short." + item.phase));
            titleLabel.setMinWidth(10);
            titleLabel.setEllipsisString("");
            titleLabel.setAlignment(Pos.CENTER);
            item.setTitleLabel(titleLabel);
            titlesBars.getChildren().addAll(titleLabel);

            ProgressBar progressBar = new ProgressBar();
            progressBar.setMinHeight(9);
            progressBar.setMaxHeight(9);
            progressBar.setMinWidth(10);
            progressBar.setStyle("-fx-accent: -bs-green;");
            progressBar.progressProperty().bind(item.progressProperty);
            progressBar.setOpacity(item.isShowBlocks() ? 1 : 0.25);
            progressBars.getChildren().add(progressBar);
            item.setProgressBar(progressBar);
        });

        widthProperty().addListener((observable, oldValue, newValue) -> {
            updateWidth((double) newValue);
        });
        UserThread.execute(() -> updateWidth(getWidth()));
    }

    public void updateWidth() {
        updateWidth(getWidth());
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

    private void updateWidth(double availableWidth) {
        totalDuration = items.stream().mapToInt(SeparatedPhaseBarsItem::getDuration).sum();
        // availableWidth -= vBoxLabels.getWidth();
        if (availableWidth > 0 && totalDuration > 0) {
            final double finalAvailableWidth = availableWidth;
            items.forEach(item -> {
                final double width = (double) item.duration / (double) totalDuration * finalAvailableWidth;
                item.getProgressBar().setPrefWidth(width);
                item.getTitleLabel().setPrefWidth(width);
            });
        }
    }

    @Getter
    public static class SeparatedPhaseBarsItem {
        private final DaoPhase.Phase phase;
        private final boolean showBlocks;
        private final IntegerProperty startBlockProperty = new SimpleIntegerProperty();
        private final IntegerProperty lastBlockProperty = new SimpleIntegerProperty();
        private final DoubleProperty progressProperty = new SimpleDoubleProperty();
        private int duration;
        @Setter
        private javafx.scene.layout.VBox progressVBox;
        @Setter
        private Label titleLabel;
        @Setter
        private ProgressBar progressBar;

        public SeparatedPhaseBarsItem(DaoPhase.Phase phase, boolean showBlocks) {
            this.phase = phase;
            this.showBlocks = showBlocks;
        }

        public void setInActive() {
            titleLabel.setStyle("-fx-text-fill: black;");
        }

        public void setActive() {
            titleLabel.setStyle("-fx-text-fill: -fx-accent;");
        }

        public void setPeriodRange(int firstBlock, int lastBlock, int duration) {
            startBlockProperty.set(firstBlock);
            lastBlockProperty.set(lastBlock);
            this.duration = duration;
        }
    }
}
