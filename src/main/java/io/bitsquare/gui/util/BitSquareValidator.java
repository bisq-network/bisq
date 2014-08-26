/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.util;

import com.google.bitcoin.core.Coin;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.trade.Offer;
import javafx.scene.control.TextField;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.paint.Color;

//TODO to be removed
@Deprecated
public class BitSquareValidator {
    private static final Effect invalidEffect = new DropShadow(BlurType.GAUSSIAN, Color.RED, 4, 0.0, 0, 0);
    private static final String invalidStyle = "-fx-border-color: red";

    public static boolean tradeAmountOutOfRange(Coin tradeAmount, Offer offer) {
        return tradeAmount.compareTo(offer.getAmount()) > 0 || tradeAmount.compareTo(offer.getMinAmount()) < 0;
    }

    public static boolean greaterThanZero(Coin value) {
        return value.compareTo(Coin.ZERO) > 0;
    }

    public static void textFieldsNotEmptyWithReset(TextField... textFields) throws ValidationException {
        resetTextFields(textFields);
        textFieldsNotEmpty(textFields);
    }

    public static void resetTextFields(TextField... textFields) {
        for (TextField textField : textFields) {
            textField.setStyle("-fx-border-color: null");
            textField.setEffect(null);
        }
    }

    public static void textFieldsNotEmpty(TextField... textFields) throws ValidationException {
        for (TextField textField : textFields) {
            textFieldNotEmpty(textField);
        }
    }

    public static void textFieldNotEmpty(TextField textField) throws ValidationException {
        if (!validateStringNotEmpty(textField.getText())) {
            textField.setEffect(invalidEffect);
            textField.setStyle(invalidStyle);
            throw new ValidationException();
        }
    }

    public static void textFieldsHasDoubleValueWithReset(TextField... textFields) throws ValidationException {
        resetTextFields(textFields);
        textFieldsHasDoubleValue(textFields);
    }

    public static void textFieldsHasDoubleValue(TextField... textFields) throws ValidationException {
        for (TextField textField : textFields) {
            textFieldHasDoubleValue(textField);
        }
    }

    public static void textFieldHasDoubleValue(TextField textField) throws ValidationException {
        if (!validateStringAsDouble(textField.getText().replace(",", "."))) {
            textField.setEffect(invalidEffect);
            textField.setStyle(invalidStyle);
            throw new ValidationException();
        }
    }


    public static void textFieldsHasPositiveDoubleValueWithReset(TextField... textFields) throws ValidationException {
        resetTextFields(textFields);
        textFieldsHasPositiveDoubleValue(textFields);
    }

    public static void textFieldsHasPositiveDoubleValue(TextField... textFields) throws ValidationException {
        for (TextField textField : textFields) {
            textFieldHasPositiveDoubleValue(textField);
        }
    }

    public static void textFieldHasPositiveDoubleValue(TextField textField) throws ValidationException {
        String input = textField.getText().replace(",", ".");
        if (!validateStringAsDouble(input)) {
            textField.setEffect(invalidEffect);
            textField.setStyle(invalidStyle);
            throw new ValidationException();
        } else {
            double val = Double.parseDouble(input);
            if (val <= 0) {
                textField.setEffect(invalidEffect);
                textField.setStyle(invalidStyle);
                throw new ValidationException();
            }
        }
    }

    //TODO
    @SuppressWarnings("UnusedParameters")
    public static void textFieldBankAccountPrimaryIDIsValid(TextField textField, BankAccountType bankAccountType)
            throws ValidationException {
        if (!validateStringNotEmpty(textField.getText())) {
            textField.setEffect(invalidEffect);
            textField.setStyle(invalidStyle);
            throw new ValidationException();
        }
    }

    //TODO
    @SuppressWarnings("UnusedParameters")
    public static void textFieldBankAccountSecondaryIDIsValid(TextField textField,
                                                              BankAccountType bankAccountType) throws
            ValidationException {
        if (!validateStringNotEmpty(textField.getText())) {
            textField.setEffect(invalidEffect);
            textField.setStyle(invalidStyle);
            throw new ValidationException();
        }
    }

    public static boolean validateStringsAsDouble(String... inputs) {
        boolean result = true;
        for (String input : inputs) {
            result &= validateStringAsDouble(input);
        }
        return result;
    }

    public static boolean validateStringAsDouble(String input) {
        try {
            input = input.replace(",", ".");
            //noinspection ResultOfMethodCallIgnored
            Double.parseDouble(input);
            return true;
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
    }

    public static boolean validateStringsNotEmpty(String... inputs) {
        boolean result = true;
        for (String input : inputs) {
            result &= validateStringNotEmpty(input);
        }
        return result;
    }

    public static boolean validateStringNotEmpty(String input) {
        return input != null && !input.isEmpty() && !" ".equals(input);
    }

    public static class ValidationException extends Exception {
        private static final long serialVersionUID = -5583980308504568844L;
    }

}
