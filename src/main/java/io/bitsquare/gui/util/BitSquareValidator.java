package io.bitsquare.gui.util;

import io.bitsquare.bank.BankAccountType;
import javafx.scene.control.TextField;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("WeakerAccess")
public class BitSquareValidator
{
    private static final Effect invalidEffect = new DropShadow(BlurType.GAUSSIAN, Color.RED, 4, 0.0, 0, 0);
    private static final String invalidStyle = "-fx-border-color: red";

    public static void textFieldsNotEmptyWithReset(TextField... textFields) throws ValidationException
    {
        resetTextFields(textFields);
        textFieldsNotEmpty(textFields);
    }

    public static void resetTextFields(@NotNull TextField... textFields)
    {
        for (@NotNull TextField textField : textFields)
        {
            textField.setStyle("-fx-border-color: null");
            textField.setEffect(null);
        }
    }

    public static void textFieldsNotEmpty(@NotNull TextField... textFields) throws ValidationException
    {
        for (@NotNull TextField textField : textFields)
        {
            textFieldNotEmpty(textField);
        }
    }

    public static void textFieldNotEmpty(@NotNull TextField textField) throws ValidationException
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

    public static void textFieldsHasDoubleValue(@NotNull TextField... textFields) throws ValidationException
    {
        for (@NotNull TextField textField : textFields)
        {
            textFieldHasDoubleValue(textField);
        }
    }

    public static void textFieldHasDoubleValue(@NotNull TextField textField) throws ValidationException
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
    public static void textFieldBankAccountPrimaryIDIsValid(@NotNull TextField textField, BankAccountType bankAccountType) throws ValidationException
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
    public static void textFieldBankAccountSecondaryIDIsValid(@NotNull TextField textField, BankAccountType bankAccountType) throws ValidationException
    {
        if (!validateStringNotEmpty(textField.getText()))
        {
            textField.setEffect(invalidEffect);
            textField.setStyle(invalidStyle);
            throw new ValidationException();
        }
    }

    public static boolean validateStringsAsDouble(@NotNull String... inputs)
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
        } catch (@NotNull NumberFormatException | NullPointerException e)
        {
            return false;
        }
    }

    public static boolean validateStringsNotEmpty(@NotNull String... inputs)
    {
        boolean result = true;
        for (String input : inputs)
        {
            result &= validateStringNotEmpty(input);
        }
        return result;
    }

    public static boolean validateStringNotEmpty(@Nullable String input)
    {
        return input != null && !input.isEmpty() && !" ".equals(input);
    }

    public static class ValidationException extends Exception
    {
        private static final long serialVersionUID = -5583980308504568844L;
    }

}
