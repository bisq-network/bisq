/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.components.paymentmethods;

import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.util.Tuple3;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.user.Preferences;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.validation.InputValidator;
import io.bisq.protobuffer.payload.payment.PaymentAccountPayload;
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

import javax.annotation.Nullable;

import static io.bisq.gui.util.FormBuilder.*;

public abstract class PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(PaymentMethodForm.class);

    protected final PaymentAccount paymentAccount;
    protected final InputValidator inputValidator;
    protected final GridPane gridPane;
    protected int gridRow;
    private final BSFormatter formatter;
    protected final BooleanProperty allInputsValid = new SimpleBooleanProperty();

    protected int gridRowFrom;
    protected InputTextField accountNameTextField;
    protected CheckBox useCustomAccountNameCheckBox;
    protected ComboBox<TradeCurrency> currencyComboBox;

    public PaymentMethodForm(PaymentAccount paymentAccount, InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        this.paymentAccount = paymentAccount;
        this.inputValidator = inputValidator;
        this.gridPane = gridPane;
        this.gridRow = gridRow;
        this.formatter = formatter;
    }

    protected void addTradeCurrencyComboBox() {
        currencyComboBox = addLabelComboBox(gridPane, ++gridRow, Res.getWithCol("shared.currency")).second;
        currencyComboBox.setPromptText(Res.get("list.currency.select"));
        currencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getAllMainFiatCurrencies(Preferences.getDefaultLocale(), Preferences.getDefaultTradeCurrency())));
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
        Tuple3<Label, InputTextField, CheckBox> tuple = addLabelInputTextFieldCheckBox(gridPane, ++gridRow,
                Res.get("payment.account.name"), Res.get("payment.useCustomAccountName"));
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
                                        @Nullable PaymentAccountPayload paymentAccountPayload,
                                        String dateFromBlocks) {
        if (paymentAccountPayload != null) {
            long hours = paymentAccountPayload.getMaxTradePeriod() / 3600_000;
            addLabelTextField(gridPane, gridRow, Res.get("payment.maxPeriod"),
                    getTimeText(hours) + " / " + dateFromBlocks);
        }
    }

    protected static String getTimeText(long hours) {
        String time = hours + " " + Res.get("time.hours");
        if (hours == 1)
            time = Res.get("time.1hour");
        else if (hours == 24)
            time = Res.get("time.1day");
        else if (hours > 24)
            time = hours / 24 + " " + Res.get("time.days");

        return time;
    }

    protected void addAllowedPeriod() {
        long hours = paymentAccount.getPaymentMethod().getMaxTradePeriod() / 3600_000;

        addLabelTextField(gridPane, ++gridRow, Res.get("payment.limitations"),
                Res.get("payment.maxPeriodAndLimit",
                        getTimeText(hours),
                        formatter.formatCoinWithCode(paymentAccount.getPaymentMethod().getMaxTradeLimit())));
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
