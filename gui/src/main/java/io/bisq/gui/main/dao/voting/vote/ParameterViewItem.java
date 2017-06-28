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

package io.bisq.gui.main.dao.voting.vote;

import io.bisq.common.UserThread;
import io.bisq.common.locale.Res;
import io.bisq.core.dao.vote.VoteItem;
import io.bisq.core.dao.vote.VotingDefaultValues;
import io.bisq.gui.components.InputTextField;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ParameterViewItem {
    private static final Logger log = LoggerFactory.getLogger(ParameterViewItem.class);

    private static final List<ParameterViewItem> instances = new ArrayList<>();

    private final long originalValue;
    private final ChangeListener<String> inputTextFieldChangeListener;
    private final ChangeListener<Boolean> inputTextFieldFocusListener;
    private final ChangeListener<Number> sliderListener;
    private final InputTextField inputTextField;
    private final Slider slider;
    private final Button resetButton, removeButton;
    private final Label label;
    private ChangeListener<Number> numberChangeListener;
    public final VoteItem voteItem;

    public static void attach(VoteItem voteItem, VBox vBox, DoubleProperty labelWidth, VotingDefaultValues votingDefaultValues, Runnable removeHandler) {
        instances.add(new ParameterViewItem(voteItem, vBox, labelWidth, votingDefaultValues, removeHandler));
    }

    public static void cleanupAllInstances() {
        instances.stream().forEach(ParameterViewItem::cleanupInstance);
    }

    public static boolean contains(VoteItem selectedItem) {
        return instances.stream().filter(e -> e.voteItem.getVotingType() == selectedItem.getVotingType()).findAny().isPresent();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isEmpty() {
        return instances.isEmpty();
    }

    private ParameterViewItem(VoteItem voteItem, VBox vBox, DoubleProperty labelWidth, VotingDefaultValues votingDefaultValues, Runnable removeHandler) {
        this.voteItem = voteItem;
        originalValue = votingDefaultValues.getValueByVotingType(voteItem.getVotingType());
        HBox hBox = new HBox();
        hBox.setSpacing(5);
        vBox.getChildren().add(hBox);

        label = new Label(voteItem.getName() + ":");
        HBox.setMargin(label, new Insets(4, 0, 0, 0));
        numberChangeListener = (observable, oldValue, newValue) -> {
            if ((double) newValue > 0) {
                labelWidth.set(Math.max(labelWidth.get(), (double) newValue));
                UserThread.execute(() -> label.prefWidthProperty().bind(labelWidth));
                label.widthProperty().removeListener(numberChangeListener);
            }
        };
        label.widthProperty().addListener(numberChangeListener);

        inputTextField = new InputTextField();
        inputTextField.setPrefWidth(100);
        inputTextField.setText(String.valueOf(originalValue));
        slider = new Slider();
        inputTextFieldChangeListener = (observable, oldValue, newValue) -> {
            if (!slider.isFocused()) {
                try {
                    long change = votingDefaultValues.getChange(originalValue, Long.valueOf(inputTextField.getText()));
                    slider.setValue(change);
                    voteItem.setValue((byte) change);
                } catch (Throwable ignore) {
                }
            }
        };
        inputTextField.textProperty().addListener(inputTextFieldChangeListener);
        inputTextFieldFocusListener = (observable, oldValue, newValue) -> {
            if (oldValue && !newValue) {
                // focus out
                // We adjust value to our 255 value grid
                int change = (int) Math.round(slider.getValue());
                long dataValue = votingDefaultValues.getAdjustedValue(originalValue, change);
                inputTextField.setText(String.valueOf(dataValue));
            }
        };
        inputTextField.focusedProperty().addListener(inputTextFieldFocusListener);

        slider.setPrefWidth(300);
        slider.setMin(0);
        slider.setMax(254);
        slider.setValue(127);
        slider.setShowTickLabels(true);
        HBox.setMargin(slider, new Insets(-1, 20, 0, 20));
        HBox.setHgrow(slider, Priority.ALWAYS);

        slider.setLabelFormatter(new StringConverter<Double>() {
            @Override
            public String toString(Double object) {
                return String.valueOf(votingDefaultValues.getAdjustedValue(originalValue, object.intValue()));
            }

            @Override
            public Double fromString(String string) {
                return null;
            }
        });
        sliderListener = (observable, oldValue, newValue) -> {
            if (!inputTextField.isFocused()) {
                int change = (int) Math.round(slider.getValue());
                long dataValue = votingDefaultValues.getAdjustedValue(originalValue, change);
                inputTextField.setText(String.valueOf(dataValue));
                voteItem.setValue((byte) change);
            }
        };
        slider.valueProperty().addListener(sliderListener);

        resetButton = new Button(Res.get("shared.reset"));
        resetButton.setOnAction(event -> inputTextField.setText(String.valueOf(originalValue)));

        removeButton = new Button(Res.get("shared.remove"));
        removeButton.setOnAction(event -> {
            vBox.getChildren().remove(hBox);
            cleanupInstance();
            instances.remove(this);
            removeHandler.run();
        });

        hBox.getChildren().addAll(label, inputTextField, slider, resetButton, removeButton);
    }

    public void cleanupInstance() {
        label.widthProperty().removeListener(numberChangeListener);
        inputTextField.focusedProperty().removeListener(inputTextFieldFocusListener);
        inputTextField.textProperty().removeListener(inputTextFieldChangeListener);
        slider.valueProperty().removeListener(sliderListener);
        resetButton.setOnAction(null);
        removeButton.setOnAction(null);
    }

}
