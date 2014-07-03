package io.bitsquare.gui.util;

import io.bitsquare.bank.BankAccountType;
import io.bitsquare.trade.Offer;
import java.math.BigInteger;
import javafx.scene.control.TextField;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.paint.Color;

@SuppressWarnings("WeakerAccess")
public class BitSquareValidator
{
    private static final Effect invalidEffect = new DropShadow(BlurType.GAUSSIAN, Color.RED, 4, 0.0, 0, 0);
    private static final String invalidStyle = "-fx-border-color: red";

    public static boolean tradeAmountOutOfRange(BigInteger tradeAmount, Offer offer)
    {
        return tradeAmount.compareTo(offer.getAmount()) > 0 || tradeAmount.compareTo(offer.getMinAmount()) < 0;
    }

    public static boolean greaterThanZero(BigInteger value)
    {
        return value.compareTo(BigInteger.ZERO) > 0;
    }

    public static void textFieldsNotEmptyWithReset(TextField... textFields) throws ValidationException
    {
        resetTextFields(textFields);
        textFieldsNotEmpty(textFields);
    }

    public static void resetTextFields(TextField... textFields)
    {
        for (TextField textField : textFields)
        {
            textField.setStyle("-fx-border-color: null");
            textField.setEffect(null);
        }
    }

    public static void textFieldsNotEmpty(TextField... textFields) throws ValidationException
    {
        for (TextField textField : textFields)
        {
            textFieldNotEmpty(textField);
        }
    }

    public static void textFieldNotEmpty(TextField textField) throws ValidationException
    {
        if (!validateStringNotEmpty(textField.getText()))
        {
            textField.setEffect(invalidEffect);
            textField.setStyle(invalidStyle);
            throw new ValidationException();
        }
    }

    public static void textFieldsHasDoubleValueWithReset(TextField... textFields) throws ValidationException
    {
        resetTextFields(textFields);
        textFieldsHasDoubleValue(textFields);
    }

    public static void textFieldsHasDoubleValue(TextField... textFields) throws ValidationException
    {
        for (TextField textField : textFields)
        {
            textFieldHasDoubleValue(textField);
        }
    }

    public static void textFieldHasDoubleValue(TextField textField) throws ValidationException
    {
        if (!validateStringAsDouble(textField.getText()))
        {
            textField.setEffect(invalidEffect);
            textField.setStyle(invalidStyle);
            throw new ValidationException();
        }
    }

    //TODO
    @SuppressWarnings("UnusedParameters")
    public static void textFieldBankAccountPrimaryIDIsValid(TextField textField, BankAccountType bankAccountType) throws ValidationException
    {
        if (!validateStringNotEmpty(textField.getText()))
        {
            textField.setEffect(invalidEffect);
            textField.setStyle(invalidStyle);
            throw new ValidationException();
        }
    }

    //TODO
    @SuppressWarnings("UnusedParameters")
    public static void textFieldBankAccountSecondaryIDIsValid(TextField textField, BankAccountType bankAccountType) throws ValidationException
    {
        if (!validateStringNotEmpty(textField.getText()))
        {
            textField.setEffect(invalidEffect);
            textField.setStyle(invalidStyle);
            throw new ValidationException();
        }
    }

    public static boolean validateStringsAsDouble(String... inputs)
    {
        boolean result = true;
        for (String input : inputs)
        {
            result &= validateStringAsDouble(input);
        }
        return result;
    }

    public static boolean validateStringAsDouble(String input)
    {
        try
        {
            input = input.replace(",", ".");
            //noinspection ResultOfMethodCallIgnored
            Double.parseDouble(input);
            return true;
        } catch (NumberFormatException | NullPointerException e)
        {
            return false;
        }
    }

    public static boolean validateStringsNotEmpty(String... inputs)
    {
        boolean result = true;
        for (String input : inputs)
        {
            result &= validateStringNotEmpty(input);
        }
        return result;
    }

    public static boolean validateStringNotEmpty(String input)
    {
        return input != null && !input.isEmpty() && !" ".equals(input);
    }

    public static class ValidationException extends Exception
    {
        private static final long serialVersionUID = -5583980308504568844L;
    }

}
