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

import bisq.core.util.FormattingUtils;

import bisq.common.util.Tuple2;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import javafx.geometry.Pos;

public class ColoredDecimalPlacesWithZerosText extends HBox {

    public ColoredDecimalPlacesWithZerosText(String number, int numberOfZerosToColorize) {
        super();

        if (numberOfZerosToColorize <= 0) {
            getChildren().addAll(new Label(number));
        } else if (number.contains(FormattingUtils.RANGE_SEPARATOR)) {
            String[] splitNumber = number.split(FormattingUtils.RANGE_SEPARATOR);
            Tuple2<Label, Label> numbers = getSplittedNumberNodes(splitNumber[0], numberOfZerosToColorize);
            getChildren().addAll(numbers.first, numbers.second);

            getChildren().add(new Label(FormattingUtils.RANGE_SEPARATOR));

            numbers = getSplittedNumberNodes(splitNumber[1], numberOfZerosToColorize);
            getChildren().addAll(numbers.first, numbers.second);
        } else {
            Tuple2<Label, Label> numbers = getSplittedNumberNodes(number, numberOfZerosToColorize);
            getChildren().addAll(numbers.first, numbers.second);
        }
        setAlignment(Pos.CENTER_LEFT);
    }

    private Tuple2<Label, Label> getSplittedNumberNodes(String number, int numberOfZeros) {
        String placesBeforeZero = number.split("0{1," + Integer.toString(numberOfZeros) + "}$")[0];
        String zeroDecimalPlaces = number.substring(placesBeforeZero.length());
        Label first = new AutoTooltipLabel(placesBeforeZero);
        Label last = new Label(zeroDecimalPlaces);
        last.getStyleClass().add("zero-decimals");

        return new Tuple2<>(first, last);
    }
}
