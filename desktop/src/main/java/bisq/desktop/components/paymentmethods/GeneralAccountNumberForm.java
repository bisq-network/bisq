package bisq.desktop.components.paymentmethods;

import bisq.desktop.components.InputTextField;
import bisq.desktop.util.Layout;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;

public abstract class GeneralAccountNumberForm extends PaymentMethodForm {

    private InputTextField accountNrInputTextField;

    GeneralAccountNumberForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator, GridPane gridPane, int gridRow, CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        accountNrInputTextField = addInputTextField(gridPane, ++gridRow, Res.get("payment.account.no"));
        accountNrInputTextField.setValidator(inputValidator);
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            setAccountNumber(newValue);
            updateFromInputs();
        });

        addTradeCurrency();

        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    public void addTradeCurrency() {
        final TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), nameAndCode);
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

    private void addFormForAccountNumberDisplayAccount(String accountName, PaymentMethod paymentMethod, String accountNr,
                                                       TradeCurrency singleTradeCurrency) {
        gridRowFrom = gridRow;
        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), accountName, Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"), Res.get(paymentMethod.getId()));
        TextField field = addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.no"), accountNr).second;
        field.setMouseTransparent(false);

        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), nameAndCode);

        addLimitations(true);
    }
}
