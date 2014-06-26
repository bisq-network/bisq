package io.bitsquare.gui.util;

import io.bitsquare.bank.BankAccountTypeInfo;
import javafx.scene.control.TextField;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.paint.Color;

public class BitSquareValidator
{
    private static Effect invalidEffect = new DropShadow(BlurType.GAUSSIAN, Color.RED, 4, 0.0, 0, 0);
    private static String invalidStyle = "-fx-border-color: red";

    public static class ValidationException extends Exception
    {
    }

    public static void textFieldsNotEmptyWithReset(TextField... textFields) throws ValidationException
    {
        resetTextFields(textFields);
        textFieldsNotEmpty(textFields);
    }

    public static void resetTextFields(TextField... textFields)
    {
        for (int i = 0; i < textFields.length; i++)
        {
            TextField textField = textFields[i];
            textField.setStyle("-fx-border-color: null");
            textField.setEffect(null);
        }
    }

    public static void textFieldsNotEmpty(TextField... textFields) throws ValidationException
    {
        for (int i = 0; i < textFields.length; i++)
        {
            TextField textField = textFields[i];
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
        for (int i = 0; i < textFields.length; i++)
        {
            TextField textField = textFields[i];
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
    public static void textFieldBankAccountPrimaryIDIsValid(TextField textField, BankAccountTypeInfo bankAccountTypeInfo) throws ValidationException
    {
        if (!validateStringNotEmpty(textField.getText()))
        {
            textField.setEffect(invalidEffect);
            textField.setStyle(invalidStyle);
            throw new ValidationException();
        }
    }

    //TODO
    public static void textFieldBankAccountSecondaryIDIsValid(TextField textField, BankAccountTypeInfo bankAccountTypeInfo) throws ValidationException
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
        for (int i = 0; i < inputs.length; i++)
        {
            String input = inputs[i];
            result &= validateStringAsDouble(input);
        }
        return result;
    }

    public static boolean validateStringAsDouble(String input)
    {
        try
        {
            input = input.replace(",", ".");
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
        for (int i = 0; i < inputs.length; i++)
        {
            String input = inputs[i];
            result &= validateStringNotEmpty(input);
        }
        return result;
    }

    public static boolean validateStringNotEmpty(String input)
    {
        return input != null && input.length() > 0 && !input.equals(" ");
    }

}
