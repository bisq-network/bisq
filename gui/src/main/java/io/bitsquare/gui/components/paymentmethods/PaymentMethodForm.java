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

package io.bitsquare.gui.components.paymentmethods;

import io.bitsquare.common.util.Tuple3;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.*;

public abstract class PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(PaymentMethodForm.class);

    protected final PaymentAccount paymentAccount;
    protected final InputValidator inputValidator;
    protected final GridPane gridPane;
    protected int gridRow;
    protected final BooleanProperty allInputsValid = new SimpleBooleanProperty();

    protected int gridRowFrom;
    protected InputTextField accountNameTextField;
    protected CheckBox useCustomAccountNameCheckBox;
    private ComboBox<TradeCurrency> currencyComboBox;

    public PaymentMethodForm(PaymentAccount paymentAccount, InputValidator inputValidator, GridPane gridPane, int gridRow) {
        this.paymentAccount = paymentAccount;
        this.inputValidator = inputValidator;
        this.gridPane = gridPane;
        this.gridRow = gridRow;
    }

    protected void addTradeCurrencyComboBox() {
        currencyComboBox = addLabelComboBox(gridPane, ++gridRow, "Currency:").second;
        currencyComboBox.setPromptText("Select currency");
        currencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getAllMainFiatCurrencies()));
        currencyComboBox.setConverter(new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                return tradeCurrency.getNameAndCode();
            }

            @Override
            public TradeCurrency fromString(String s) {
                return null;
            }
        });
        currencyComboBox.setOnAction(e -> {
            paymentAccount.setSingleTradeCurrency(currencyComboBox.getSelectionModel().getSelectedItem());
            updateFromInputs();
        });
    }

    protected void addAccountNameTextFieldWithAutoFillCheckBox() {
        Tuple3<Label, InputTextField, CheckBox> tuple = addLabelInputTextFieldCheckBox(gridPane, ++gridRow, "Account name:", "Use custom account name");
        accountNameTextField = tuple.second;
        accountNameTextField.setPrefWidth(300);
        accountNameTextField.setEditable(false);
        accountNameTextField.setValidator(inputValidator);
        accountNameTextField.setFocusTraversable(false);
        accountNameTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            paymentAccount.setAccountName(newValue);
            updateAllInputsValid();
        });
        useCustomAccountNameCheckBox = tuple.third;
        useCustomAccountNameCheckBox.setSelected(false);
        useCustomAccountNameCheckBox.setOnAction(e -> {
            boolean selected = useCustomAccountNameCheckBox.isSelected();
            accountNameTextField.setEditable(selected);
            accountNameTextField.setFocusTraversable(selected);
            autoFillNameTextField();
        });
    }

    public static void addAllowedPeriod(GridPane gridPane, int gridRow,
                                        PaymentAccountContractData paymentAccountContractData, String dateFromBlocks) {
        long hours = paymentAccountContractData.getMaxTradePeriod() / 6;
        String displayText;
        if (hours == 1)
            displayText = hours + " hour";
        else
            displayText = hours + " hours";
        if (hours == 24)
            displayText = "1 day";
        if (hours > 24)
            displayText = hours / 24 + " days";


        addLabelTextField(gridPane, gridRow, "Max. allowed trade period / date:", displayText + " / " + dateFromBlocks);
    }

    protected void addAllowedPeriod() {
        long hours = paymentAccount.getPaymentMethod().getMaxTradePeriod() / 6;
        String displayText = hours + " hours";
        if (hours == 1)
            displayText = "1 hour";
        else if (hours == 24)
            displayText = "1 day";
        else if (hours > 24)
            displayText = hours / 24 + " days";

        displayText += " (Max. permitted period until the trade has to be completed)";

        addLabelTextField(gridPane, ++gridRow, "Max. allowed trade period:", displayText);
    }

    abstract protected void autoFillNameTextField();

    abstract public void addFormForAddAccount();

    abstract public void addFormForDisplayAccount();

    protected abstract void updateAllInputsValid();

    public void updateFromInputs() {
        autoFillNameTextField();
        updateAllInputsValid();
    }

    public boolean isAccountNameValid() {
        return inputValidator.validate(paymentAccount.getAccountName()).isValid;
    }

    public int getGridRow() {
        return gridRow;
    }

    public int getRowSpan() {
        return gridRow - gridRowFrom + 1;
    }

    public PaymentAccount getPaymentAccount() {
        return paymentAccount;
    }

    public BooleanProperty allInputsValidProperty() {
        return allInputsValid;
    }
}
