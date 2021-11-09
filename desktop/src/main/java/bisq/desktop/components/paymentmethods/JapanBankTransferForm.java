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

package bisq.desktop.components.paymentmethods;

import bisq.desktop.components.AutocompleteComboBox;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.paymentmethods.data.JapanBankData;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.JapanBankAccountNameValidator;
import bisq.desktop.util.validation.JapanBankAccountNumberValidator;
import bisq.desktop.util.validation.JapanBankBranchCodeValidator;
import bisq.desktop.util.validation.JapanBankBranchNameValidator;
import bisq.desktop.util.validation.LengthValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.JapanBankAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.JapanBankAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;
import bisq.core.util.validation.RegexValidator;

import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;
import bisq.common.util.Tuple4;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;

import javafx.util.StringConverter;

import static bisq.desktop.util.FormBuilder.*;
import static bisq.desktop.util.GUIUtil.getComboBoxButtonCell;

public class JapanBankTransferForm extends PaymentMethodForm {
    private final JapanBankAccount japanBankAccount;
    protected ComboBox<String> bankComboBox;

    private final JapanBankBranchNameValidator japanBankBranchNameValidator;
    private final JapanBankBranchCodeValidator japanBankBranchCodeValidator;
    private final JapanBankAccountNameValidator japanBankAccountNameValidator;
    private final JapanBankAccountNumberValidator japanBankAccountNumberValidator;

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        JapanBankAccountPayload japanBankAccount = ((JapanBankAccountPayload) paymentAccountPayload);

        String bankText = japanBankAccount.getBankCode() + " " + japanBankAccount.getBankName();
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.japan.bank"), bankText);

        String branchText = japanBankAccount.getBankBranchCode() + " " + japanBankAccount.getBankBranchName();
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, gridRow, 1, Res.get("payment.japan.branch"), branchText);

        String accountText = japanBankAccount.getBankAccountType() + " " + japanBankAccount.getBankAccountNumber();
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.japan.account"), accountText);

        String accountNameText = japanBankAccount.getBankAccountName();
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, gridRow, 1, Res.get("payment.japan.recipient"), accountNameText);

        return gridRow;
    }

    public JapanBankTransferForm(PaymentAccount paymentAccount,
                                 AccountAgeWitnessService accountAgeWitnessService,
                                 InputValidator inputValidator, GridPane gridPane,
                                 int gridRow, CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.japanBankAccount = (JapanBankAccount) paymentAccount;

        this.japanBankBranchCodeValidator = new JapanBankBranchCodeValidator();
        this.japanBankAccountNumberValidator = new JapanBankAccountNumberValidator();

        LengthValidator lengthValidator = new LengthValidator();
        RegexValidator regexValidator = new RegexValidator();
        this.japanBankBranchNameValidator = new JapanBankBranchNameValidator(lengthValidator, regexValidator);
        this.japanBankAccountNameValidator = new JapanBankAccountNameValidator(lengthValidator, regexValidator);
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;

        addTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.name"),
                japanBankAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);

        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(japanBankAccount.getPaymentMethod().getId()));

        addBankDisplay();
        addBankBranchDisplay();
        addBankAccountDisplay();
        addBankAccountTypeDisplay();

        addLimitations(true);
    }

    private void addBankDisplay() {
        String bankText = japanBankAccount.getBankCode() + " " + japanBankAccount.getBankName();
        TextField bankTextField = addCompactTopLabelTextField(gridPane, ++gridRow, JapanBankData.getString("bank"), bankText).second;
        bankTextField.setEditable(false);
    }

    private void addBankBranchDisplay() {
        String branchText = japanBankAccount.getBankBranchCode() + " " + japanBankAccount.getBankBranchName();
        TextField branchTextField = addCompactTopLabelTextField(gridPane, ++gridRow, JapanBankData.getString("branch"), branchText).second;
        branchTextField.setEditable(false);
    }

    private void addBankAccountDisplay() {
        String accountText = japanBankAccount.getBankAccountNumber() + " " + japanBankAccount.getBankAccountName();
        TextField accountTextField = addCompactTopLabelTextField(gridPane, ++gridRow, JapanBankData.getString("account"), accountText).second;
        accountTextField.setEditable(false);
    }

    private void addBankAccountTypeDisplay() {
        TradeCurrency singleTradeCurrency = japanBankAccount.getSingleTradeCurrency();
        String currency = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        String accountTypeText = currency + " " + japanBankAccount.getBankAccountType();
        TextField accountTypeTextField = addCompactTopLabelTextField(gridPane, ++gridRow, JapanBankData.getString("account.type"), accountTypeText).second;
        accountTypeTextField.setEditable(false);
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow;

        addBankInput();
        addBankBranchInput();
        addBankAccountInput();
        addBankAccountTypeInput();

        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    private void addBankInput() {
        gridRow++;

        Tuple4<Label, TextField, Label, ComboBox<String>> tuple4 = addTopLabelTextFieldAutocompleteComboBox(gridPane, gridRow, JapanBankData.getString("bank.code"), JapanBankData.getString("bank.name"), 10);

        // Bank Code (readonly)
        TextField bankCodeField = tuple4.second;
        bankCodeField.setPrefWidth(200);
        bankCodeField.setMaxWidth(200);
        bankCodeField.setEditable(false);

        // Bank Selector
        bankComboBox = tuple4.fourth;
        bankComboBox.setPromptText(JapanBankData.getString("bank.select"));
        bankComboBox.setButtonCell(getComboBoxButtonCell(JapanBankData.getString("bank.name"), bankComboBox));
        bankComboBox.getEditor().focusedProperty().addListener(observable -> bankComboBox.setPromptText(""));
        bankComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String bank) {
                return bank != null ? bank : "";
            }

            public String fromString(String s) {
                return s != null ? s : "";
            }
        });
        ((AutocompleteComboBox<String>) bankComboBox).setAutocompleteItems(JapanBankData.prettyPrintBankList());

        bankComboBox.setPrefWidth(430);
        bankComboBox.setVisibleRowCount(430);

        ((AutocompleteComboBox<?>) bankComboBox).setOnChangeConfirmed(e -> {
            // get selected value
            String bank = bankComboBox.getSelectionModel().getSelectedItem();

            // parse first 4 characters as bank code
            String bankCode = StringUtils.substring(bank, 0, 4);
            if (bankCode != null) {
                // set bank code field to this value
                bankCodeField.setText(bankCode);
                // save to payload
                japanBankAccount.setBankCode(bankCode);

                // parse remainder as bank name
                String bankNameFull = StringUtils.substringAfter(bank, JapanBankData.SPACE);
                // parse beginning as Japanese bank name
                String bankNameJa = StringUtils.substringBefore(bankNameFull, JapanBankData.SPACE);
                // set bank name field to this value
                bankComboBox.getEditor().setText(bankNameJa);
                // save to payload
                japanBankAccount.setBankName(bankNameJa);
            }


            updateFromInputs();
        });
    }

    private void addBankBranchInput() {
        gridRow++;
        Tuple2<InputTextField, InputTextField> tuple2 = addInputTextFieldInputTextField(gridPane, gridRow, JapanBankData.getString("branch.code"), JapanBankData.getString("branch.name"));

        // branch code
        InputTextField bankBranchCodeInputTextField = tuple2.first;
        bankBranchCodeInputTextField.setValidator(japanBankBranchCodeValidator);
        bankBranchCodeInputTextField.setPrefWidth(200);
        bankBranchCodeInputTextField.setMaxWidth(200);
        bankBranchCodeInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            japanBankAccount.setBankBranchCode(newValue);
            updateFromInputs();
        });

        // branch name
        InputTextField bankBranchNameInputTextField = tuple2.second;
        bankBranchNameInputTextField.setValidator(japanBankBranchNameValidator);
        bankBranchNameInputTextField.setPrefWidth(430);
        bankBranchNameInputTextField.setMaxWidth(430);
        bankBranchNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            japanBankAccount.setBankBranchName(newValue);
            updateFromInputs();
        });
    }

    private void addBankAccountInput() {
        gridRow++;
        Tuple2<InputTextField, InputTextField> tuple2 = addInputTextFieldInputTextField(gridPane, gridRow, JapanBankData.getString("account.number"), JapanBankData.getString("account.name"));

        // account number
        InputTextField bankAccountNumberInputTextField = tuple2.first;
        bankAccountNumberInputTextField.setValidator(japanBankAccountNumberValidator);
        bankAccountNumberInputTextField.setPrefWidth(200);
        bankAccountNumberInputTextField.setMaxWidth(200);
        bankAccountNumberInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            japanBankAccount.setBankAccountNumber(newValue);
            updateFromInputs();
        });

        // account name
        InputTextField bankAccountNameInputTextField = tuple2.second;
        bankAccountNameInputTextField.setValidator(japanBankAccountNameValidator);
        bankAccountNameInputTextField.setPrefWidth(430);
        bankAccountNameInputTextField.setMaxWidth(430);
        bankAccountNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            japanBankAccount.setBankAccountName(newValue);
            updateFromInputs();
        });
    }

    private void addBankAccountTypeInput() {
        // account currency
        gridRow++;

        TradeCurrency singleTradeCurrency = japanBankAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addCompactTopLabelTextField(gridPane, gridRow, Res.get("shared.currency"), nameAndCode, 20);

        // account type
        gridRow++;

        ToggleGroup toggleGroup = new ToggleGroup();
        Tuple3<Label, RadioButton, RadioButton> tuple3 =
                addTopLabelRadioButtonRadioButton(
                        gridPane, gridRow, toggleGroup,
                        JapanBankData.getString("account.type.select"),
                        JapanBankData.getString("account.type.futsu"),
                        JapanBankData.getString("account.type.touza"),
                        0
                );

        toggleGroup.getToggles().get(0).setSelected(true);
        japanBankAccount.setBankAccountType(JapanBankData.getString("account.type.futsu.ja"));

        RadioButton futsu = tuple3.second;
        RadioButton touza = tuple3.third;

        toggleGroup.selectedToggleProperty().addListener
                (
                        (ov, oldValue, newValue) ->
                        {
                            if (futsu.isSelected())
                                japanBankAccount.setBankAccountType(JapanBankData.getString("account.type.futsu.ja"));
                            if (touza.isSelected())
                                japanBankAccount.setBankAccountType(JapanBankData.getString("account.type.touza.ja"));
                        }
                );
    }

    @Override
    public void updateFromInputs() {
        System.out.println("JapanBankTransferForm: updateFromInputs()");
        System.out.println("bankName: " + japanBankAccount.getBankName());
        System.out.println("bankCode: " + japanBankAccount.getBankCode());
        System.out.println("bankBranchName: " + japanBankAccount.getBankBranchName());
        System.out.println("bankBranchCode: " + japanBankAccount.getBankBranchCode());
        System.out.println("bankAccountType: " + japanBankAccount.getBankAccountType());
        System.out.println("bankAccountName: " + japanBankAccount.getBankAccountName());
        System.out.println("bankAccountNumber: " + japanBankAccount.getBankAccountNumber());
        super.updateFromInputs();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameToggleButton != null && !useCustomAccountNameToggleButton.isSelected()) {
            accountNameTextField.setText(
                    Res.get(paymentAccount.getPaymentMethod().getId())
                            .concat(": ")
                            .concat(japanBankAccount.getBankName())
                            .concat(" ")
                            .concat(japanBankAccount.getBankBranchName())
                            .concat(" ")
                            .concat(japanBankAccount.getBankAccountNumber())
                            .concat(" ")
                            .concat(japanBankAccount.getBankAccountName())
            );
        }
    }

    @Override
    public void updateAllInputsValid() {
        boolean result =
                (
                        isAccountNameValid() &&
                                inputValidator.validate(japanBankAccount.getBankCode()).isValid &&
                                inputValidator.validate(japanBankAccount.getBankName()).isValid &&
                                japanBankBranchCodeValidator.validate(japanBankAccount.getBankBranchCode()).isValid &&
                                japanBankBranchNameValidator.validate(japanBankAccount.getBankBranchName()).isValid &&
                                japanBankAccountNumberValidator.validate(japanBankAccount.getBankAccountNumber()).isValid &&
                                japanBankAccountNameValidator.validate(japanBankAccount.getBankAccountName()).isValid &&
                                inputValidator.validate(japanBankAccount.getBankAccountType()).isValid
                );
        allInputsValid.set(result);
    }
}
