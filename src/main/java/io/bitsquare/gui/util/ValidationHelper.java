package io.bitsquare.gui.util;

import io.bitsquare.gui.components.ValidatingTextField;
import javafx.beans.property.StringProperty;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for setting up the validation and dependencies for minAmount and Amount.
 * TODO Might be improved but does the job for now...
 */
public class ValidationHelper
{
    private static final Logger log = LoggerFactory.getLogger(ValidationHelper.class);

    /**
     * Handles validation between minAmount and amount fields
     */
    public static void setupMinAmountInRangeOfAmountValidation(ValidatingTextField amountTextField,
                                                               ValidatingTextField minAmountTextField,
                                                               StringProperty amount,
                                                               StringProperty minAmount,
                                                               BtcValidator amountValidator,
                                                               BtcValidator minAmountValidator)
    {


        amountTextField.focusedProperty().addListener((ov, oldValue, newValue) -> {
            // only on focus out and ignore focus loss from window
            if (!newValue && amountTextField.getScene().getWindow().isFocused())
                validateMinAmount(amountTextField,
                                  minAmountTextField,
                                  amount,
                                  minAmount,
                                  amountValidator,
                                  minAmountValidator,
                                  amountTextField);
        });

        minAmountTextField.focusedProperty().addListener((ov, oldValue, newValue) -> {
            // only on focus out and ignore focus loss from window
            if (!newValue && minAmountTextField.getScene().getWindow().isFocused())
                validateMinAmount(amountTextField,
                                  minAmountTextField,
                                  amount,
                                  minAmount,
                                  amountValidator,
                                  minAmountValidator,
                                  minAmountTextField);
        });
    }

    private static void validateMinAmount(ValidatingTextField amountTextField,
                                          ValidatingTextField minAmountTextField,
                                          StringProperty amount,
                                          StringProperty minAmount,
                                          BtcValidator amountValidator,
                                          BtcValidator minAmountValidator,
                                          TextField currentTextField)
    {
        amountValidator.overrideResult(null);
        if (!amountValidator.validate(amount.get()).isValid)
            return;

        minAmountValidator.overrideResult(null);
        if (!minAmountValidator.validate(minAmount.get()).isValid)
            return;

        if (currentTextField == amountTextField)
        {
            if (Double.parseDouble(amount.get()) < Double.parseDouble(minAmount.get()))
            {
                amountValidator.overrideResult(new NumberValidator.ValidationResult(false, "Amount cannot be smaller than minimum amount.", NumberValidator.ErrorType.AMOUNT_LESS_THAN_MIN_AMOUNT));
                amountTextField.reValidate();
            }
            else
            {
                amountValidator.overrideResult(null);
                minAmountTextField.reValidate();
            }
        }
        else if (currentTextField == minAmountTextField)
        {
            if (Double.parseDouble(minAmount.get()) > Double.parseDouble(amount.get()))
            {
                minAmountValidator.overrideResult(new NumberValidator.ValidationResult(false, "Minimum amount cannot be larger than amount.", NumberValidator.ErrorType.MIN_AMOUNT_LARGER_THAN_MIN_AMOUNT));
                minAmountTextField.reValidate();
            }
            else
            {
                minAmountValidator.overrideResult(null);
                amountTextField.reValidate();
            }
        }
    }
}
