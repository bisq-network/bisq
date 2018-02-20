package io.bisq.gui.components;

import io.bisq.common.util.Tuple2;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

public class ColoredDecimalPlacesWithZerosText extends TextFlow {

    public ColoredDecimalPlacesWithZerosText(String number, int numberOfZerosToColorize) {
        super();

        final String rangeSeparator = " - ";

        if (number.contains(rangeSeparator)) {
            //is range
            String[] splitNumber = number.split(rangeSeparator);
            Tuple2<Text, Text> numbers = getSplittedNumberNodes(splitNumber[0], numberOfZerosToColorize);
            getChildren().addAll(numbers.first, numbers.second);

            getChildren().add(new Text(rangeSeparator));

            numbers = getSplittedNumberNodes(splitNumber[1], numberOfZerosToColorize);
            getChildren().addAll(numbers.first, numbers.second);
        } else {
            Tuple2<Text, Text> numbers = getSplittedNumberNodes(number, numberOfZerosToColorize);
            getChildren().addAll(numbers.first, numbers.second);
        }

        setTextAlignment(TextAlignment.CENTER);
        setPrefHeight(20);

    }

    private Tuple2<Text, Text> getSplittedNumberNodes(String number, int numberOfZeros) {
        String placesBeforeZero = number.split("0{1," + Integer.toString(numberOfZeros) + "}$")[0];
        String zeroDecimalPlaces = number.substring(placesBeforeZero.length());
        Text first = new Text(placesBeforeZero);
        Text last = new Text(zeroDecimalPlaces);
        last.getStyleClass().add("zero-decimals");

        return new Tuple2<>(first, last);
    }
}
