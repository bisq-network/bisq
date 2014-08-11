package io.bitsquare.gui.components;

import io.bitsquare.gui.util.NumberValidator;
import java.util.LinkedList;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import org.controlsfx.control.PopOver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TextField with validation support. Validation is executed on the Validator object.
 * In case of a invalid result we display a error message with a PopOver.
 * The position is derived from the textField or if set from the errorPopupLayoutReference object.
 * <p>
 * That class implements just what we need for the moment. It is not intended as a general purpose library class.
 */
public class ValidatingTextField extends TextField
{
    private static final Logger log = LoggerFactory.getLogger(ValidatingTextField.class);
    private static final Effect DEFAULT_EFFECT = new DropShadow(BlurType.GAUSSIAN, Color.RED, 4, 0.0, 0, 0);

    // we hold all error popups any only display the latest one
    private static final LinkedList<PopOver> allErrorPopups = new LinkedList<>();

    private Effect invalidEffect = DEFAULT_EFFECT;
    private final BooleanProperty valid = new SimpleBooleanProperty(true);
    private NumberValidator numberValidator;
    private boolean validateOnFocusOut = true;
    private boolean needsValidationOnFocusOut;
    private PopOver popOver;

    private Region errorPopupLayoutReference;
    private double errorPopOverX;
    private double errorPopOverY;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ValidatingTextField()
    {
        super();
        setupListeners();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void reValidate()
    {
        validate(getText());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setNumberValidator(NumberValidator numberValidator)
    {
        this.numberValidator = numberValidator;
    }

    public void setErrorPopupLayoutReference(Region errorPopupLayoutReference)
    {
        this.errorPopupLayoutReference = errorPopupLayoutReference;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupListeners()
    {
        this.textProperty().addListener((ov, oldValue, newValue) -> {
            if (numberValidator != null)
            {
                if (!validateOnFocusOut)
                    validate(newValue);
                else
                    needsValidationOnFocusOut = true;
            }
        });

        this.focusedProperty().addListener((ov, oldValue, newValue) -> {
            if (validateOnFocusOut && needsValidationOnFocusOut && !newValue && getScene()!= null && getScene().getWindow().isFocused())
                validate(getText());
        });

        this.valid.addListener((ov, oldValue, newValue) -> applyEffect(newValue));
    }

    private void validate(String input)
    {
        if (input != null)
        {
            NumberValidator.ValidationResult validationResult = numberValidator.validate(input);
            valid.set(validationResult.isValid);
            applyErrorMessage(validationResult);
        }
    }

    private void applyErrorMessage(NumberValidator.ValidationResult validationResult)
    {
        if (validationResult.isValid)
        {
            if (allErrorPopups.contains(popOver))
            {
                allErrorPopups.remove(popOver);
                popOver.hide();
            }
            if (allErrorPopups.size() > 0)
            {
                PopOver lastPopOver = allErrorPopups.getLast();
                lastPopOver.show(getScene().getWindow());
            }
            popOver = null;
        }
        else
        {
            if (allErrorPopups.size() > 0)
            {
                PopOver lastPopOver = allErrorPopups.getLast();
                lastPopOver.hide();
            }

            if (allErrorPopups.contains(popOver))
            {
                allErrorPopups.remove(popOver);
                popOver.hide();
            }

            popOver = createErrorPopOver(validationResult.errorMessage);
            popOver.show(getScene().getWindow(), errorPopOverX, errorPopOverY);
            allErrorPopups.add(popOver);
        }
    }

    private void applyEffect(boolean isValid)
    {
        setEffect(isValid ? null : invalidEffect);
    }

    private PopOver createErrorPopOver(String errorMessage)
    {
        Label errorLabel = new Label(errorMessage);
        errorLabel.setId("validation-error");
        errorLabel.setPadding(new Insets(0, 10, 0, 10));

        PopOver popOver = new PopOver(errorLabel);
        popOver.setAutoFix(true);
        popOver.setDetachedTitle("");
        popOver.setArrowIndent(5);
        Window window = getScene().getWindow();

        Point2D point;
        if (errorPopupLayoutReference == null)
        {
            point = localToScene(0, 0);
            errorPopOverX = point.getX() + window.getX() + getWidth() + 20;
        }
        else
        {
            point = errorPopupLayoutReference.localToScene(0, 0);
            errorPopOverX = point.getX() + window.getX() + errorPopupLayoutReference.getWidth() + 20;
        }
        errorPopOverY = point.getY() + window.getY() + Math.floor(getHeight() / 2);
        return popOver;
    }
}