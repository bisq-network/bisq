package bisq.desktop.components.paymentmethods;

import bisq.desktop.components.InputTextField;
import bisq.desktop.util.FormBuilder;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Country;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.CountryBasedPaymentAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import org.apache.commons.lang3.StringUtils;

import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;

import static bisq.desktop.util.FormBuilder.addTopLabelWithVBox;

public abstract class GeneralSepaForm extends PaymentMethodForm {

    static final String BIC = "BIC";
    static final String IBAN = "IBAN";

    final List<CheckBox> euroCountryCheckBoxes = new ArrayList<>();
    final List<CheckBox> nonEuroCountryCheckBoxes = new ArrayList<>();
    private TextField currencyTextField;
    InputTextField ibanInputTextField;

    private FiatCurrency euroCurrency = CurrencyUtil.getFiatCurrency("EUR").get();

    GeneralSepaForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator, GridPane gridPane, int gridRow, CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        paymentAccount.setSingleTradeCurrency(euroCurrency);
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameToggleButton != null && !useCustomAccountNameToggleButton.isSelected()) {
            TradeCurrency singleTradeCurrency = this.paymentAccount.getSingleTradeCurrency();
            String currency = singleTradeCurrency != null ? singleTradeCurrency.getCode() : null;
            if (currency != null) {
                String iban = ibanInputTextField.getText();
                if (iban.length() > 9)
                    iban = StringUtils.abbreviate(iban, 9);
                String method = Res.get(paymentAccount.getPaymentMethod().getId());
                CountryBasedPaymentAccount countryBasedPaymentAccount = (CountryBasedPaymentAccount) this.paymentAccount;
                String country = countryBasedPaymentAccount.getCountry() != null ?
                        countryBasedPaymentAccount.getCountry().code : null;
                if (country != null)
                    accountNameTextField.setText(method.concat(" (").concat(currency).concat("/").concat(country)
                            .concat("): ").concat(iban));
            }
        }
    }

    void setCountryComboBoxAction(ComboBox<Country> countryComboBox, CountryBasedPaymentAccount paymentAccount) {
        countryComboBox.setOnAction(e -> {
            Country selectedItem = countryComboBox.getSelectionModel().getSelectedItem();
            paymentAccount.setCountry(selectedItem);

            updateCountriesSelection(euroCountryCheckBoxes);
            updateCountriesSelection(nonEuroCountryCheckBoxes);
            updateFromInputs();
        });
    }

    void addCountriesGrid(String title, List<CheckBox> checkBoxList,
                          List<Country> dataProvider) {
        FlowPane flowPane = FormBuilder.addTopLabelFlowPane(gridPane, ++gridRow, title, 0).second;

        flowPane.setId("flow-pane-checkboxes-bg");

        dataProvider.forEach(country ->
                fillUpFlowPaneWithCountries(checkBoxList, flowPane, country));
        updateCountriesSelection(checkBoxList);
    }

    ComboBox<Country> addCountrySelection() {
        HBox hBox = new HBox();

        hBox.setSpacing(10);
        ComboBox<Country> countryComboBox = new JFXComboBox<>();
        currencyTextField = new JFXTextField("");
        currencyTextField.setEditable(false);
        currencyTextField.setMouseTransparent(true);
        currencyTextField.setFocusTraversable(false);
        currencyTextField.setMinWidth(300);

        currencyTextField.setVisible(true);
        currencyTextField.setManaged(true);
        currencyTextField.setText(Res.get("payment.currencyWithSymbol", euroCurrency.getNameAndCode()));

        hBox.getChildren().addAll(countryComboBox, currencyTextField);

        addTopLabelWithVBox(gridPane, ++gridRow, Res.get("payment.bank.country"), hBox, 0);

        countryComboBox.setPromptText(Res.get("payment.select.bank.country"));
        countryComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Country country) {
                return country.name + " (" + country.code + ")";
            }

            @Override
            public Country fromString(String s) {
                return null;
            }
        });
        return countryComboBox;
    }

    abstract void updateCountriesSelection(List<CheckBox> checkBoxList);

}
