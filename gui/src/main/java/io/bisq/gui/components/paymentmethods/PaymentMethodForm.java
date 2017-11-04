/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.components.paymentmethods;

import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.util.Tuple3;
import io.bisq.common.util.Utilities;
import io.bisq.core.offer.Offer;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.CryptoCurrencyAccount;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.validation.InputValidator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;

import static io.bisq.gui.util.FormBuilder.*;

@Slf4j
public abstract class PaymentMethodForm {
    protected final PaymentAccount paymentAccount;
    private final AccountAgeWitnessService accountAgeWitnessService;
    protected final InputValidator inputValidator;
    protected final GridPane gridPane;
    protected int gridRow;
    private final BSFormatter formatter;
    protected final BooleanProperty allInputsValid = new SimpleBooleanProperty();

    protected int gridRowFrom;
    protected InputTextField accountNameTextField;
    protected CheckBox useCustomAccountNameCheckBox;
    protected ComboBox<TradeCurrency> currencyComboBox;

    public PaymentMethodForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        this.paymentAccount = paymentAccount;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.inputValidator = inputValidator;
        this.gridPane = gridPane;
        this.gridRow = gridRow;
        this.formatter = formatter;
    }

    protected void addTradeCurrencyComboBox() {
        //noinspection unchecked
        currencyComboBox = addLabelComboBox(gridPane, ++gridRow, Res.getWithCol("shared.currency")).second;
        currencyComboBox.setPromptText(Res.get("list.currency.select"));
        currencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getMainFiatCurrencies()));
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

    public static void addOpenTradeDuration(GridPane gridPane,
                                            int gridRow,
                                            Offer offer,
                                            String dateFromBlocks) {
        long hours = offer.getMaxTradePeriod() / 3600_000;
        addLabelTextField(gridPane, gridRow, Res.get("payment.maxPeriod"),
                getTimeText(hours) + " / " + dateFromBlocks);
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

    protected void addLimitations() {
        long hours = paymentAccount.getPaymentMethod().getMaxTradePeriod() / 3600_000;

        final TradeCurrency tradeCurrency;
        if (paymentAccount.getSingleTradeCurrency() != null)
            tradeCurrency = paymentAccount.getSingleTradeCurrency();
        else if (paymentAccount.getSelectedTradeCurrency() != null)
            tradeCurrency = paymentAccount.getSelectedTradeCurrency();
        else if (!paymentAccount.getTradeCurrencies().isEmpty())
            tradeCurrency = paymentAccount.getTradeCurrencies().get(0);
        else
            tradeCurrency = paymentAccount instanceof CryptoCurrencyAccount ?
                    CurrencyUtil.getAllSortedCryptoCurrencies().get(0) :
                    CurrencyUtil.getDefaultTradeCurrency();

        final boolean isAddAccountScreen = paymentAccount.getAccountName() == null;
        final long accountAge = !isAddAccountScreen ? accountAgeWitnessService.getMyAccountAge(paymentAccount.getPaymentAccountPayload()) : 0L;
        addLabelTextField(gridPane, ++gridRow, Res.get("payment.limitations"), Res.get("payment.maxPeriodAndLimit",
                getTimeText(hours),
                formatter.formatCoinWithCode(Coin.valueOf(accountAgeWitnessService.getMyTradeLimit(paymentAccount, tradeCurrency.getCode()))),
                formatter.formatAccountAge(accountAge)));

        if (isAddAccountScreen) {
            InputTextField inputTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.salt"), 0).second;
            inputTextField.setText(Utilities.bytesAsHexString(paymentAccount.getPaymentAccountPayload().getSalt()));
            inputTextField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue.isEmpty()) {
                    try {
                        // test if input is hex
                        Utilities.decodeFromHex(newValue);

                        paymentAccount.setSaltAsHex(newValue);
                    } catch (Throwable t) {
                        new Popup().warning(Res.get("payment.error.noHexSalt")).show();
                        inputTextField.setText(Utilities.bytesAsHexString(paymentAccount.getPaymentAccountPayload().getSalt()));
                        log.warn(t.toString());
                    }
                }
            });
        } else {
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.salt",
                    Utilities.bytesAsHexString(paymentAccount.getPaymentAccountPayload().getSalt())),
                    Utilities.bytesAsHexString(paymentAccount.getPaymentAccountPayload().getSalt()));
        }
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
