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

import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.locale.Res;

import com.jfoenix.controls.JFXProgressBar;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.geometry.Pos;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SeparatedPhaseBars extends VBox {
    private double labelMinWidth = 150;
    private double breakMinWidth = 20;
    private int totalDuration;
    private List<SeparatedPhaseBarsItem> items;

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
            String text = item.phase.name().startsWith("BREAK") ? "" : Res.get("dao.phase.separatedPhaseBar." + item.phase);
            Label titleLabel = new Label(text);
            titleLabel.setEllipsisString("");
            titleLabel.setAlignment(Pos.CENTER);
            item.setTitleLabel(titleLabel);
            titlesBars.getChildren().addAll(titleLabel);

            ProgressBar progressBar = new JFXProgressBar();
            progressBar.setMinHeight(9);
            progressBar.setMaxHeight(9);
            progressBar.progressProperty().bind(item.progressProperty);
            progressBar.setOpacity(item.isShowBlocks() ? 1 : 0.25);
            progressBars.getChildren().add(progressBar);
            item.setProgressBar(progressBar);
        });

        widthProperty().addListener((observable, oldValue, newValue) -> {
            updateWidth((double) newValue);
        });
    }

    public void updateWidth() {
        updateWidth(getWidth());
    }

    private void updateWidth(double availableWidth) {
        if (availableWidth > 0) {
            totalDuration = items.stream().mapToInt(SeparatedPhaseBarsItem::getDuration).sum();
            if (totalDuration > 0) {
                // We want to have a min. width for the breaks and for the phases which are important to the user but
                // quite short (blind vote, vote reveal, result). If we display it correctly most of the space is
                // consumed by the proposal phase. We we apply a min and max width and adjust the available width so
                // we have all phases displayed so that the text is fully readable. The proposal phase is shorter as
                // it would be with correct display but we take that into account to have a better overall overview.
                final double finalAvailableWidth = availableWidth;
                AtomicReference<Double> adjustedAvailableWidth = new AtomicReference<>(availableWidth);
                items.forEach(item -> {
                    double calculatedWidth = (double) item.duration / (double) totalDuration * finalAvailableWidth;
                    double minWidth = item.phase.name().startsWith("BREAK") ? breakMinWidth : labelMinWidth;
                    double maxWidth = item.phase.name().startsWith("BREAK") ? breakMinWidth : calculatedWidth;
                    if (calculatedWidth < minWidth) {
                        double missing = minWidth - calculatedWidth;
                        adjustedAvailableWidth.set(adjustedAvailableWidth.get() - missing);
                    } else if (calculatedWidth > maxWidth) {
                        double remaining = calculatedWidth - maxWidth;
                        adjustedAvailableWidth.set(adjustedAvailableWidth.get() + remaining);
                    }
                });

                items.forEach(item -> {
                    double calculatedWidth = (double) item.duration / (double) totalDuration * adjustedAvailableWidth.get();
                    double minWidth = item.phase.name().startsWith("BREAK") ? breakMinWidth : labelMinWidth;
                    double maxWidth = item.phase.name().startsWith("BREAK") ? breakMinWidth : calculatedWidth;
                    double width = calculatedWidth;
                    if (calculatedWidth < minWidth) {
                        width = minWidth;
                    } else if (calculatedWidth > maxWidth) {
                        width = maxWidth;
                    }
                    item.getTitleLabel().setPrefWidth(width);
                    item.getProgressBar().setPrefWidth(width);
                });
            }
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
            titleLabel.getStyleClass().add("separated-phase-bar-inactive");
        }

        public void setActive() {
            titleLabel.getStyleClass().add("separated-phase-bar-active");
        }

        public void setPeriodRange(int firstBlock, int lastBlock, int duration) {
            startBlockProperty.set(firstBlock);
            lastBlockProperty.set(lastBlock);
            this.duration = duration;
        }
    }
}
