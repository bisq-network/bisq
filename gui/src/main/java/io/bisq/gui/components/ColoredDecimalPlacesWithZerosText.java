package io.bisq.gui.components;

import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

public class ColoredDecimalPlacesWithZerosText extends TextFlow {

    public ColoredDecimalPlacesWithZerosText(String number) {
        super();

        String placesBeforeZero = number.split("0*$")[0];
        String zeroDecimalPlaces = number.substring(placesBeforeZero.length());
        Text first = new Text(placesBeforeZero);
        Text last = new Text(zeroDecimalPlaces);
        last.getStyleClass().add("zero-decimals");
        setTextAlignment(TextAlignment.CENTER);
        setPrefHeight(20);

        getChildren().addAll(first, last);
    }
}
