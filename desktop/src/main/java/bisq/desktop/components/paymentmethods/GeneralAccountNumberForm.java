package bisq.desktop.components.paymentmethods;

import bisq.desktop.components.InputTextField;
import bisq.desktop.util.FormBuilder;

import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.PaymentAccount;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.layout.GridPane;

abstract public class GeneralAccountNumberForm extends PaymentMethodForm {

    private InputTextField accountNrInputTextField;

    public GeneralAccountNumberForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        accountNrInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, Res.get("payment.account.no"));
        accountNrInputTextField.setValidator(inputValidator);
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            setAccountNumber(newValue);
            updateFromInputs();
        });

        addTradeCurrency();

        addLimitations();
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    public void addTradeCurrency() {
        final TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        FormBuilder.addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), nameAndCode);
    }

    @Override
    protected void autoFillNameTextField() {
        setAccountNameWithString(accountNrInputTextField.getText());
    }

    @Override
    public void addFormForDisplayAccount() {
        addFormForAccountNumberDisplayAccount(paymentAccount.getAccountName(), paymentAccount.getPaymentMethod(), getAccountNr(),
                paymentAccount.getSingleTradeCurrency());
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && inputValidator.validate(getAccountNr()).isValid
                && paymentAccount.getTradeCurrencies().size() > 0);
    }

    abstract void setAccountNumber(String newValue);

    abstract String getAccountNr();
}
