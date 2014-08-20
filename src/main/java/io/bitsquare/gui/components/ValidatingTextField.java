package io.bitsquare.gui.components;

import io.bitsquare.gui.util.NumberValidator;
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
    private static PopOver popOver;

    private Effect invalidEffect = new DropShadow(BlurType.GAUSSIAN, Color.RED, 4, 0.0, 0, 0);

    private final BooleanProperty isValid = new SimpleBooleanProperty(true);
    private NumberValidator numberValidator;
    private boolean validateOnFocusOut = true;
    private boolean needsValidationOnFocusOut;
    private Region errorPopupLayoutReference;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void hidePopover()
    {
        if (popOver != null)
            popOver.hide();
    }


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

    /**
     * @param errorPopupLayoutReference The node used as reference for positioning
     */
    public void setErrorPopupLayoutReference(Region errorPopupLayoutReference)
    {
        this.errorPopupLayoutReference = errorPopupLayoutReference;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean getIsValid()
    {
        return isValid.get();
    }

    public BooleanProperty isValidProperty()
    {
        return isValid;
    }
    
    
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupListeners()
    {
        sceneProperty().addListener((ov, oldValue, newValue) -> {
            // we got removed from the scene
            // lets hide an open popup
            if (newValue == null)
                hidePopover();
        });

        textProperty().addListener((ov, oldValue, newValue) -> {
            if (numberValidator != null)
            {
                if (!validateOnFocusOut)
                    validate(newValue);
                else
                    needsValidationOnFocusOut = true;
            }
        });

        focusedProperty().addListener((ov, oldValue, newValue) -> {
            if (validateOnFocusOut && needsValidationOnFocusOut && !newValue && getScene() != null && getScene().getWindow().isFocused())
                validate(getText());
        });

        isValid.addListener((ov, oldValue, newValue) -> applyEffect(newValue));
    }

    private void validate(String input)
    {
        if (input != null)
        {
            NumberValidator.ValidationResult validationResult = numberValidator.validate(input);
            isValid.set(validationResult.isValid);
            applyErrorMessage(validationResult);
        }
    }

    private void applyErrorMessage(NumberValidator.ValidationResult validationResult)
    {
        if (validationResult.isValid)
        {
            if (popOver != null)
            {
                popOver.hide();
            }
        }
        else
        {
            if (popOver == null)
                createErrorPopOver(validationResult.errorMessage);
            else
                setErrorMessage(validationResult.errorMessage);

            popOver.show(getScene().getWindow(), getErrorPopupPosition().getX(), getErrorPopupPosition().getY());
        }
    }

    private void applyEffect(boolean isValid)
    {
        setEffect(isValid ? null : invalidEffect);
    }

    private Point2D getErrorPopupPosition()
    {
        Window window = getScene().getWindow();
        Point2D point;
        double x;
        if (errorPopupLayoutReference == null)
        {
            point = localToScene(0, 0);
            x = point.getX() + window.getX() + getWidth() + 20;
        }
        else
        {
            point = errorPopupLayoutReference.localToScene(0, 0);
            x = point.getX() + window.getX() + errorPopupLayoutReference.getWidth() + 20;
        }
        double y = point.getY() + window.getY() + Math.floor(getHeight() / 2);
        return new Point2D(x, y);
    }

    private static void setErrorMessage(String errorMessage)
    {
        ((Label) popOver.getContentNode()).setText(errorMessage);
    }

    private static void createErrorPopOver(String errorMessage)
    {
        Label errorLabel = new Label(errorMessage);
        errorLabel.setId("validation-error");
        errorLabel.setPadding(new Insets(0, 10, 0, 10));

        popOver = new PopOver(errorLabel);
        popOver.setAutoFix(true);
        popOver.setDetachedTitle("");
        popOver.setArrowIndent(5);
    }
}