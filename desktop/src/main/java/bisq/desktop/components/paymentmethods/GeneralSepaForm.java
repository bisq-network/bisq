package bisq.desktop.components.paymentmethods;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipCheckBox;
import bisq.desktop.main.overlays.windows.QRCodeWindow;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.CountryBasedPaymentAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import org.apache.commons.lang3.StringUtils;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;
import de.jensd.fx.glyphs.materialdesignicons.utils.MaterialDesignIconFactory;

import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.geometry.Insets;

import javafx.util.StringConverter;

import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextFieldWithCopyIcon;
import static bisq.desktop.util.FormBuilder.addTopLabelWithVBox;

@Slf4j
public abstract class GeneralSepaForm extends PaymentMethodForm {

    static final String BIC = "BIC";
    static final String IBAN = "IBAN";

    public static int addFormForBuyer(GridPane gridPane, int gridRow, String amount,
                                      String countryCode, String recipient, String bic, String iban) {
        Button showQrCodeButton = new AutoTooltipButton(Res.get("shared.showSepaQRCode"),
                MaterialDesignIconFactory.get().createIcon(MaterialDesignIcon.QRCODE, "2.0em"));
        GridPane.setRowIndex(showQrCodeButton, gridRow);
        GridPane.setColumnIndex(showQrCodeButton, 0);
        gridPane.getChildren().add(showQrCodeButton);
        GridPane.setMargin(showQrCodeButton, new Insets(66 + Layout.FLOATING_LABEL_DISTANCE, 0, 0, 0));
        showQrCodeButton.setStyle("-fx-pref-height: 27; -fx-padding: 4 4 4 4;");
        GridPane.setColumnIndex(showQrCodeButton, 1);
        showQrCodeButton.setOnMouseClicked(e -> GUIUtil.showMaximizedToProtectPrivacyMessage(() ->
                new QRCodeWindow(constructQRCodeString(bic, iban, recipient, amount))
                    .withoutText()
                    .setWindowDimensions(gridPane.getScene().getWindow().getWidth() * 1.05,
                            gridPane.getScene().getWindow().getHeight() * 1.05)
                    .show()));

        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.account.owner"), recipient);
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, gridRow, 1, BIC, bic);
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                Res.get("payment.bank.country"), CountryUtil.getNameAndCode(countryCode));
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, gridRow, 1, IBAN, iban);

        return gridRow;
    }

    GeneralSepaForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator, GridPane gridPane, int gridRow, CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameToggleButton != null && !useCustomAccountNameToggleButton.isSelected()) {
            TradeCurrency singleTradeCurrency = this.paymentAccount.getSingleTradeCurrency();
            String currency = singleTradeCurrency != null ? singleTradeCurrency.getCode() : null;
            if (currency != null) {
                String iban = getIban();
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

            updateFromInputs();
        });
    }

    void addCountriesGrid(String title, List<Country> countries) {
        FlowPane flowPane = FormBuilder.addTopLabelFlowPane(gridPane, ++gridRow, title, 0).second;

        flowPane.setId("flow-pane-checkboxes-bg");

        countries.forEach(country -> {
            CheckBox checkBox = new AutoTooltipCheckBox(country.code);
            checkBox.setUserData(country.code);
            checkBox.setSelected(isCountryAccepted(country.code));
            checkBox.setMouseTransparent(false);
            checkBox.setMinWidth(45);
            checkBox.setMaxWidth(45);
            checkBox.setTooltip(new Tooltip(country.name));
            checkBox.setOnAction(event -> {
                if (checkBox.isSelected()) {
                    addAcceptedCountry(country.code);
                } else {
                    removeAcceptedCountry(country.code);
                }

                updateAllInputsValid();
            });
            flowPane.getChildren().add(checkBox);
        });
    }

    ComboBox<Country> addCountrySelection() {
        HBox hBox = new HBox();

        hBox.setSpacing(10);
        ComboBox<Country> countryComboBox = new JFXComboBox<>();
        TextField currencyTextField = new JFXTextField("");
        currencyTextField.setEditable(false);
        currencyTextField.setMouseTransparent(true);
        currencyTextField.setFocusTraversable(false);
        currencyTextField.setMinWidth(300);

        currencyTextField.setVisible(true);
        currencyTextField.setManaged(true);
        currencyTextField.setText(Res.get("payment.currencyWithSymbol",
                Objects.requireNonNull(paymentAccount.getSingleTradeCurrency()).getNameAndCode()));

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

    abstract boolean isCountryAccepted(String countryCode);

    protected abstract String getIban();

    // see https://en.wikipedia.org/wiki/EPC_QR_code
    private static String constructQRCodeString(String bic, String iban, String recipient, String amountCcy) {
        String paymentBase = "BCD\n001\n1\nSCT\n" + bic + "\n" + recipient + "\n" + iban;
        String[] amountSplit = amountCcy.split(" ");
        if (amountSplit.length == 2) {
            return paymentBase + "\n" + amountSplit[1] + amountSplit[0]; // ccy and amount combined, EPC_QR_code spec
        }
        return paymentBase;
    }
}
