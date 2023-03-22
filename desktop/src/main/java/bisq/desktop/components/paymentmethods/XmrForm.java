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

import bisq.desktop.components.BisqTextArea;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.dao.governance.asset.AssetService;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.AssetAccount;
import bisq.core.payment.InstantCryptoCurrencyAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.XmrAccountDelegate;
import bisq.core.payment.payload.AssetsAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.validation.AltCoinAddressValidator;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.User;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;
import bisq.core.util.validation.IntegerValidator;
import bisq.core.util.validation.RegexValidator;

import bisq.asset.AddressValidationResult;
import bisq.asset.CryptoNoteAddressValidator;

import bisq.common.util.Tuple3;
import bisq.common.util.Utilities;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.xmr.knaccc.monero.address.WalletAddress.PUBLIC_ADDRESS_PREFIX;
import static bisq.desktop.util.DisplayUtils.createAssetsAccountName;
import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextFieldWithCopyIcon;
import static bisq.desktop.util.FormBuilder.addLabelCheckBox;

@Slf4j
public class XmrForm extends AssetsForm {
    private InputTextField privateViewKeyInputTextField, accountIndex, subAddressIndex, mainAddressTextField, subAddressTextField;
    private CheckBox useSubAddressesCheckBox;
    private boolean disableUpdates = false;
    private final XmrWalletAddressValidator mainAddressValidator = new XmrWalletAddressValidator();
    private final IntegerValidator accountIndexValidator = new IntegerValidator(0, 99);
    private final IntegerValidator subAddressIndexValidator = new IntegerValidator(0, 9999);
    private final RegexValidator regexValidator = new RegexValidator();
    private final XmrAccountDelegate xmrAccountDelegate;
    private final User user;

    public static int addFormForBuyer(GridPane gridPane,
                                      int gridRow,
                                      PaymentAccountPayload paymentAccountPayload,
                                      String labelTitle) {
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, labelTitle,
                ((AssetsAccountPayload) paymentAccountPayload).getAddress());
        return gridRow;
    }

    public XmrForm(PaymentAccount paymentAccount,
                   AccountAgeWitnessService accountAgeWitnessService,
                   AltCoinAddressValidator altCoinAddressValidator,
                   InputValidator inputValidator,
                   GridPane gridPane,
                   int gridRow,
                   CoinFormatter formatter,
                   AssetService assetService,
                   User user) {
        super(paymentAccount, accountAgeWitnessService, altCoinAddressValidator, inputValidator, gridPane, gridRow, formatter, assetService);

        this.user = user;
        xmrAccountDelegate = new XmrAccountDelegate(assetAccount);
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        tradeInstantCheckBox = addLabelCheckBox(gridPane, ++gridRow, Res.get("payment.altcoin.tradeInstantCheckbox"), 10);
        tradeInstantCheckBox.setSelected(tradeInstant);
        tradeInstantCheckBox.setOnAction(e -> {
            tradeInstant = tradeInstantCheckBox.isSelected();
            if (tradeInstant)
                new Popup().information(Res.get("payment.altcoin.tradeInstant.popup")).show();
            paymentLimitationsTextField.setText(getLimitationsText());
        });
        tradeInstantCheckBox.setPadding(new Insets(0, 40, 0, 0));

        useSubAddressesCheckBox = addLabelCheckBox(gridPane, ++gridRow, Res.get("payment.altcoin.useSubAddresses"), 10);
        useSubAddressesCheckBox.setPadding(new Insets(0, 40, 0, 0));
        useSubAddressesCheckBox.setSelected(xmrAccountDelegate.isUsingSubAddresses());
        useSubAddressesCheckBox.setOnAction(e -> {
            disableUpdates = true;
            xmrAccountDelegate.reset();
            xmrAccountDelegate.setIsUsingSubAddresses(useSubAddressesCheckBox.isSelected());
            if (useSubAddressesCheckBox.isSelected()) {
                xmrAccountDelegate.setAccountIndex("0");
                xmrAccountDelegate.setSubAddressIndex("1");
                maybeShowXmrSubAddressInfo();
            }
            setFieldManagement(xmrAccountDelegate.isUsingSubAddresses());
            mainAddressTextField.setText(xmrAccountDelegate.getMainAddress());
            privateViewKeyInputTextField.setText(xmrAccountDelegate.getPrivateViewKey());
            accountIndex.setText(xmrAccountDelegate.getAccountIndex());
            subAddressIndex.setText(xmrAccountDelegate.getSubAddressIndex());
            subAddressTextField.setText(xmrAccountDelegate.getSubAddress());
            addressInputTextField.setText(xmrAccountDelegate.getSubAddress());
            disableUpdates = false;
            updateFromInputs();
        });

        mainAddressTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, Res.get("payment.altcoin.xmrMainAddress"));
        mainAddressTextField.setValidator(mainAddressValidator);
        mainAddressTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            updateFromInputs();
        });

        privateViewKeyInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, Res.get("payment.altcoin.privateViewKey"));
        regexValidator.setPattern("[a-fA-F0-9]{64}|^$");
        regexValidator.setErrorMessage(Res.get("portfolio.pending.step2_buyer.confirmStart.proof.invalidInput"));
        privateViewKeyInputTextField.setValidator(regexValidator);
        privateViewKeyInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            updateFromInputs();
        });

        HBox hBox = new HBox();
        hBox.setSpacing(10);

        accountIndex = new InputTextField();
        accountIndex.setLabelFloat(true);
        accountIndex.setPromptText(Res.get("payment.altcoin.xmrAccountIndex"));
        accountIndex.setPrefWidth(100);
        accountIndex.setValidator(accountIndexValidator);
        accountIndex.textProperty().addListener((ov, oldValue, newValue) -> {
            updateFromInputs();
        });

        subAddressIndex = new InputTextField();
        subAddressIndex.setLabelFloat(true);
        subAddressIndex.setPromptText(Res.get("payment.altcoin.initialXmrSubAddressIndex"));
        subAddressIndex.setPrefWidth(130);
        subAddressIndex.setValidator(subAddressIndexValidator);
        subAddressIndex.textProperty().addListener((ov, oldValue, newValue) -> {
            updateFromInputs();
        });

        subAddressTextField = new InputTextField();
        subAddressTextField.setLabelFloat(true);
        subAddressTextField.setPromptText(Res.get("payment.altcoin.xmrSubAddress"));
        subAddressTextField.setDisable(true);   // this field gets calculated, so read-only
        subAddressTextField.setPrefWidth(750);
        hBox.getChildren().addAll(accountIndex, subAddressIndex, subAddressTextField);
        GridPane.setRowIndex(hBox, ++gridRow);
        GridPane.setColumnIndex(hBox, 0);
        GridPane.setMargin(hBox, new Insets(0 + Layout.FLOATING_LABEL_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(hBox);

        // subAddressTextField and addressInputTextField share the same row, they are used interchangably
        // depending on if subaddresses are in use
        addressInputTextField = FormBuilder.addInputTextField(gridPane, gridRow, Res.get("payment.altcoin.address"));
        addressInputTextField.setValidator(altCoinAddressValidator);
        addressInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            updateFromInputs();
        });

        setFieldManagement(xmrAccountDelegate.isUsingSubAddresses());
        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    void setFieldManagement(boolean useSubAddresses) {
        useSubAddressesCheckBox.setManaged(true);
        useSubAddressesCheckBox.setVisible(true);
        mainAddressTextField.setManaged(useSubAddresses);
        mainAddressTextField.setVisible(useSubAddresses);
        privateViewKeyInputTextField.setManaged(useSubAddresses);
        privateViewKeyInputTextField.setVisible(useSubAddresses);
        accountIndex.setManaged(useSubAddresses);
        accountIndex.setVisible(useSubAddresses);
        subAddressIndex.setManaged(useSubAddresses);
        subAddressIndex.setVisible(useSubAddresses);
        subAddressTextField.setManaged(useSubAddresses);
        subAddressTextField.setVisible(useSubAddresses);
        addressInputTextField.setManaged(!useSubAddresses);
        addressInputTextField.setVisible(!useSubAddresses);
    }

    @Override
    public PaymentAccount getPaymentAccount() {
        if (tradeInstant) {
            InstantCryptoCurrencyAccount instantCryptoCurrencyAccount = new InstantCryptoCurrencyAccount();
            instantCryptoCurrencyAccount.init();
            instantCryptoCurrencyAccount.setAccountName(paymentAccount.getAccountName());
            instantCryptoCurrencyAccount.setSaltAsHex(paymentAccount.getSaltAsHex());
            instantCryptoCurrencyAccount.setSalt(paymentAccount.getSalt());
            instantCryptoCurrencyAccount.setSingleTradeCurrency(paymentAccount.getSingleTradeCurrency());
            instantCryptoCurrencyAccount.setSelectedTradeCurrency(paymentAccount.getSelectedTradeCurrency());
            instantCryptoCurrencyAccount.setAddress(xmrAccountDelegate.getAddress());
            instantCryptoCurrencyAccount.setExtraData(paymentAccount.getExtraData());
            return instantCryptoCurrencyAccount;
        } else {
            return paymentAccount;
        }
    }

    @Override
    public void updateFromInputs() {
        if (disableUpdates) {
            return;
        }
        disableUpdates = true;
        if (xmrAccountDelegate.isUsingSubAddresses()) {
            xmrAccountDelegate.setMainAddress(mainAddressTextField.getText());
            xmrAccountDelegate.setPrivateViewKey(privateViewKeyInputTextField.getText());
            xmrAccountDelegate.setAccountIndex(accountIndex.getText());
            xmrAccountDelegate.setSubAddressIndex(subAddressIndex.getText());
            subAddressTextField.getStyleClass().remove("error-text");
            if (accountIndex.validate() && subAddressIndex.validate()
                    && mainAddressTextField.validate()
                    && privateViewKeyInputTextField.validate()
                    && mainAddressTextField.getText().length() > 0
                    && privateViewKeyInputTextField.getText().length() > 0) {
                try {
                    xmrAccountDelegate.createAndSetNewSubAddress();
                    subAddressTextField.setText(xmrAccountDelegate.getSubAddress());
                } catch (Exception ex) {
                    log.warn(ex.getMessage());
                    String[] parts = ex.getMessage().split(":");
                    subAddressTextField.setText(parts.length > 0 ? parts[parts.length-1] : ex.getMessage());
                    subAddressTextField.getStyleClass().add("error-text");
                }
            } else {
                xmrAccountDelegate.setSubAddress("");
                subAddressTextField.setText("");
            }
        } else {
            // legacy XMR (no subAddress)
            xmrAccountDelegate.setAddress(addressInputTextField.getText());
        }
        super.updateFromInputs();
        disableUpdates = false;
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameToggleButton != null && !useCustomAccountNameToggleButton.isSelected()) {
            accountNameTextField.setText(createAssetsAccountName(paymentAccount, xmrAccountDelegate.isUsingSubAddresses() ?
                    xmrAccountDelegate.getMainAddress() : xmrAccountDelegate.getAddress()));
        }
    }

    @Override
    public void addFormForEditAccount() {
        gridRowFrom = gridRow;
        addAccountNameTextFieldWithAutoFillToggleButton();
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(xmrAccountDelegate.getAccount().getPaymentMethod().getId()));
        final TradeCurrency singleTradeCurrency = xmrAccountDelegate.getAccount().getSingleTradeCurrency();
        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.altcoin"), nameAndCode);

        if (xmrAccountDelegate.isUsingSubAddresses()) {
            Tuple3<Label, TextField, VBox> xmrMainAddress = addCompactTopLabelTextField(gridPane, ++gridRow,
                    Res.get("payment.altcoin.xmrMainAddress"), xmrAccountDelegate.getMainAddress());
            xmrMainAddress.second.setMouseTransparent(false);
            addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.altcoin.xmrAccountIndex"), xmrAccountDelegate.getAccountIndex())
                    .second.setMouseTransparent(false);
            addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.altcoin.initialXmrSubAddressIndex"), xmrAccountDelegate.getSubAddressIndex())
                    .second.setMouseTransparent(false);

            Map<String, Set<PaymentAccount>> subAccountsByMainAddress = user.getSubAccountsById();
            List<PaymentAccount> subAccounts = new ArrayList<>(subAccountsByMainAddress.getOrDefault(xmrAccountDelegate.getSubAccountId(), new HashSet<>()));
            if (subAccounts.size() == 0) {
                addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.altcoin.xmrSubAddress"), xmrAccountDelegate.getSubAddress())
                        .second.setMouseTransparent(false);
            } else {
                StringBuilder subAddressReport = new StringBuilder();
                subAccounts.sort(Comparator.comparing(PaymentAccount::getCreationDate));
                for (PaymentAccount account : subAccounts) {
                    XmrAccountDelegate delegate = new XmrAccountDelegate((AssetAccount) account);
                    subAddressReport.append(Res.get("payment.altcoin.usedSubaddressList",
                                    delegate.getSubAddressIndex(),
                                    delegate.getSubAddress(),
                                    Utilities.getShortId(delegate.getTradeId())))
                            .append(System.lineSeparator());
                }
                GridPane gridPane2 = new GridPane();
                gridPane2.getColumnConstraints().add(gridPane.getColumnConstraints().get(0));
                TitledPane titledPane = new TitledPane(Res.get("payment.altcoin.xmrSubAddressesUsed"), gridPane2);
                titledPane.setExpanded(false);
                gridPane.add(titledPane, 0, ++gridRow);
                TextArea subAddressTextArea = new BisqTextArea();
                gridPane2.add(subAddressTextArea, 0, 1);
                subAddressTextArea.setMinHeight(70);
                subAddressTextArea.setText(subAddressReport.toString());
                subAddressTextArea.setEditable(false);
            }
        } else {
            addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.altcoin.address"), xmrAccountDelegate.getAddress())
                    .second.setMouseTransparent(false);
        }
        addLimitations(true);
    }

    @Override
    public void updateAllInputsValid() {
        TradeCurrency selectedTradeCurrency = xmrAccountDelegate.getAccount().getSelectedTradeCurrency();
        if (selectedTradeCurrency != null) {
            altCoinAddressValidator.setCurrencyCode(selectedTradeCurrency.getCode());
            if (xmrAccountDelegate.isUsingSubAddresses()) {
                // monero using subaddresses
                allInputsValid.set(isAccountNameValid()
                        && altCoinAddressValidator.validate(xmrAccountDelegate.getSubAddress()).isValid
                        && mainAddressValidator.validate(xmrAccountDelegate.getMainAddress()).isValid
                        && regexValidator.validate(xmrAccountDelegate.getPrivateViewKey()).isValid
                        && accountIndexValidator.validate(xmrAccountDelegate.getAccountIndex()).isValid
                        && subAddressIndexValidator.validate(xmrAccountDelegate.getSubAddressIndex()).isValid
                        && xmrAccountDelegate.getAccount().getSingleTradeCurrency() != null);
            } else {
                // fixed monero address
                allInputsValid.set(isAccountNameValid()
                        && altCoinAddressValidator.validate(xmrAccountDelegate.getAddress()).isValid
                        && xmrAccountDelegate.getAccount().getSingleTradeCurrency() != null);
            }
        }
    }

    private void maybeShowXmrSubAddressInfo() {
        String key = "xmrSubAddressInfo";
        if (DontShowAgainLookup.showAgain(key)) {
            new Popup()
                    .headLine(Res.get("account.altcoin.popup.xmr.subAddressHeadline"))
                    .attention(Res.get("account.altcoin.popup.xmr.subAddressInfo"))
                    .dontShowAgainId(key)
                    .show();
        }
    }

    private static class XmrWalletAddressValidator extends InputValidator {
        // enforce that the main wallet address uses PUBLIC_ADDRESS_PREFIX (not a subaddress)
        private final CryptoNoteAddressValidator validator = new CryptoNoteAddressValidator(PUBLIC_ADDRESS_PREFIX);

        @Override
        public ValidationResult validate(String input) {
            AddressValidationResult adr = validator.validate(input);
            return new ValidationResult(adr.isValid(), adr.getMessage());
        }
    }
}
