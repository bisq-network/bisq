package io.bisq.gui.components;

import io.bisq.common.locale.Res;
import io.bisq.core.dao.DaoPeriodService;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class SeparatedPhaseBars extends HBox {

    private int totalDuration;
    private List<SeparatedPhaseBarsItem> items;

    public SeparatedPhaseBars(List<SeparatedPhaseBarsItem> items) {
        this.items = items;
        setSpacing(0);
        // SeparatedPhaseBarsItem last = items.get(items.size() - 1);
        items.stream().forEach(item -> {
            Label titleLabel = new Label(Res.get("dao.phase.short." + item.phase));
            item.setTitleLabel(titleLabel);

            Label startValue = new Label();
            item.startValueProperty.addListener((observable, oldValue, newValue) -> {
                startValue.setText(String.valueOf(newValue));
            });
            Label endValue = new Label();
            item.endValueProperty.addListener((observable, oldValue, newValue) -> {
                endValue.setText(String.valueOf(newValue));
            });
            // endValue.setManaged(last == item);
            // endValue.setVisible(last == item);
            Pane spacer = new Pane();

            HBox hbox = new HBox();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            hbox.getChildren().addAll(startValue, spacer, endValue);

            ProgressBar progressBar = new ProgressBar();
            progressBar.setOpacity(0.7);
            progressBar.setMinHeight(9);
            progressBar.setMaxHeight(9);
            progressBar.setMaxWidth(Double.MAX_VALUE);
            progressBar.setStyle("-fx-accent: -bs-green;");
            progressBar.progressProperty().bind(item.progressProperty);

            VBox vBox = new VBox();
            vBox.setSpacing(5);
            vBox.getChildren().addAll(titleLabel, progressBar, hbox);
            vBox.setAlignment(Pos.CENTER);
            getChildren().add(vBox);
            item.setVBox(vBox);
        });


        widthProperty().addListener((observable, oldValue, newValue) -> {
            adjustWidth((double) newValue);
        });
        // adjustWidth(widthProperty().get());*/
    }

    private void adjustWidth(double availableWidth) {
        totalDuration = items.stream().mapToInt(SeparatedPhaseBarsItem::getDuration).sum();
        if (availableWidth > 0 && totalDuration > 0) {
            items.stream().forEach(item -> {
                final double width = (double) item.duration / (double) totalDuration * availableWidth;
                item.getVBox().setPrefWidth(width);
            });
        }
    }

    @Getter
    public static class SeparatedPhaseBarsItem {
        private final DaoPeriodService.Phase phase;
        private final IntegerProperty startValueProperty = new SimpleIntegerProperty();
        private final IntegerProperty endValueProperty = new SimpleIntegerProperty();
        private final DoubleProperty progressProperty = new SimpleDoubleProperty();
        private int duration;
        @Setter
        private javafx.scene.layout.VBox VBox;
        @Setter
        private Label titleLabel;

        public SeparatedPhaseBarsItem(DaoPeriodService.Phase phase) {
            this.phase = phase;

            endValueProperty.addListener(new ChangeListener<Number>() {
                @Override
                public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                    duration = endValueProperty.get() - startValueProperty.get();
                }
            });
        }

        public void setInActive() {
            titleLabel.setStyle("-fx-text-fill: black;");
        }

        public void setActive() {
            titleLabel.setStyle("-fx-text-fill: -fx-accent;");
        }
    }
}
