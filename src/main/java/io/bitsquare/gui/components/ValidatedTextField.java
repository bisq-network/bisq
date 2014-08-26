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

package io.bitsquare.gui.components;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TextField;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * TextField with regex-based real-time input validation.
 * JavaFX 2 and FXML compatible. </p>
 * <p>
 * FXML code example:<div>
 * {@code <ValidatedTextField fx:id="validatedTextField" minLength="1" maxLength="1" mask="^[0-9]*$" />}
 * </div>
 * </p>
 */
@Deprecated
public class ValidatedTextField extends TextField
{
    private static final Logger log = LoggerFactory.getLogger(ValidatedTextField.class);

    private final BooleanProperty invalid = new SimpleBooleanProperty(false);
    private final StringProperty mask;
    private final IntegerProperty minLength;
    private final IntegerProperty maxLength;

    private Effect invalidEffect = new DropShadow(BlurType.GAUSSIAN, Color.RED, 4, 0.0, 0, 0);

    public ValidatedTextField()
    {
        super();
        this.mask = new SimpleStringProperty("^[0-9.,]*$");
        this.minLength = new SimpleIntegerProperty(1);
        this.maxLength = new SimpleIntegerProperty(12);

        bind();
    }

    public ValidatedTextField(String mask, int minLength, int maxLength, boolean nullable)
    {
        this(mask, minLength, maxLength, nullable, null);
    }

    public ValidatedTextField(String mask, int minLength, int maxLength, boolean nullable, String string)
    {
        super(string);
        this.mask = new SimpleStringProperty(mask);
        this.minLength = new SimpleIntegerProperty(minLength);
        this.maxLength = new SimpleIntegerProperty(maxLength);

        bind();
    }

    public ReadOnlyBooleanProperty invalidProperty()
    {
        return invalid;
    }

    public ReadOnlyStringProperty maskProperty()
    {
        return mask;
    }

    public ReadOnlyIntegerProperty minLengthProperty()
    {
        return minLength;
    }

    public ReadOnlyIntegerProperty maxLengthProperty()
    {
        return maxLength;
    }

    public boolean isInvalid()
    {
        return invalid.get();
    }

    public String getMask()
    {
        return mask.get();
    }

    public void setMask(String mask)
    {
        this.mask.set(mask);
    }

    public int getMinLength()
    {
        return minLength.get();
    }

    public void setMinLength(int minLength)
    {
        this.minLength.set(minLength);
    }

    public int getMaxLength()
    {
        return maxLength.get();
    }

    public void setMaxLength(int maxLength)
    {
        this.maxLength.set(maxLength);
    }

    public Effect getInvalidEffect()
    {
        return this.invalidEffect;
    }

    public void setInvalidEffect(Effect effect)
    {
        this.invalidEffect = effect;
    }

    private void bind()
    {
        this.invalid.bind(maskCheck().or(minLengthCheck()));

        this.textProperty().addListener(new ChangeListener<String>()
        {
            @Override
            public void changed(ObservableValue<? extends String> ov, String t, String t1)
            {
                if (textProperty().get() != null && textProperty().get().length() > maxLength.get())
                {
                    setText(t);
                }
            }
        });

        this.invalid.addListener(new ChangeListener<Boolean>()
        {
            @Override
            public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1)
            {
                if (t ^ t1)
                {
                    if (t1)
                    {
                        //                        setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
                        setEffect(invalidEffect);
                    }
                    else
                    {
                        //                        setStyle("-fx-font-weight: normal; -fx-text-fill: inherit;");
                        setEffect(null);
                    }
                }

            }
        });
    }

    private BooleanBinding maskCheck()
    {
        return new BooleanBinding()
        {
            {
                super.bind(textProperty(), mask);
            }

            @Override
            protected boolean computeValue()
            {
                return (textProperty().get() != null) ? !textProperty().get().matches(mask.get()) : false;
            }
        };
    }

    private BooleanBinding minLengthCheck()
    {
        return new BooleanBinding()
        {
            {
                super.bind(textProperty(), minLength);
            }

            @Override
            protected boolean computeValue()
            {
                return (textProperty().get() != null) ? textProperty().get().length() < minLength.get() : false;
            }
        };
    }

    private BooleanBinding maxLengthCheck()
    {
        return new BooleanBinding()
        {
            {
                super.bind(textProperty(), maxLength);
            }

            @Override
            protected boolean computeValue()
            {
                return textProperty().get().length() > maxLength.get();
            }
        };
    }
}