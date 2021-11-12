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

import bisq.desktop.components.InputTextField;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.EmailValidator;
import bisq.desktop.util.validation.LengthValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.TransferwiseUsdAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.TransferwiseUsdAccountPayload;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;

import static bisq.common.util.Utilities.cleanString;
import static bisq.desktop.util.FormBuilder.*;

public class TransferwiseUsdForm extends PaymentMethodForm {
    private final TransferwiseUsdAccount account;
    private final LengthValidator addressValidator = new LengthValidator(0, 100);
    private EmailValidator emailValidator = new EmailValidator();

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {

        addTopLabelTextFieldWithCopyIcon(gridPane, gridRow, 1, Res.get("payment.account.owner"),
                ((TransferwiseUsdAccountPayload) paymentAccountPayload).getHolderName(),
                Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE);

        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, 1, Res.get("payment.email"),
                ((TransferwiseUsdAccountPayload) paymentAccountPayload).getEmail());

        String address = ((TransferwiseUsdAccountPayload) paymentAccountPayload).getBeneficiaryAddress();
        if (address.length() > 0) {
            TextArea textAddress = addCompactTopLabelTextArea(gridPane, gridRow, 0, Res.get("payment.account.address"), "").second;
            textAddress.setMinHeight(70);
            textAddress.setEditable(false);
            textAddress.setText(address);
        }

        return gridRow;
    }

    public TransferwiseUsdForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService,
                      InputValidator inputValidator, GridPane gridPane,
                      int gridRow, CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.account = (TransferwiseUsdAccount) paymentAccount;
    }

    @Override
    public void addFormForAddAccount() {
        // this payment method is currently restricted to United States/USD
        account.setSingleTradeCurrency(new FiatCurrency("USD"));
        CountryUtil.findCountryByCode("US").ifPresent(c -> account.setCountry(c));

        gridRowFrom = gridRow + 1;

        InputTextField emailField = addInputTextField(gridPane, ++gridRow, Res.get("payment.email"));
        emailField.setValidator(emailValidator);
        emailField.textProperty().addListener((ov, oldValue, newValue) -> {
            account.setEmail(newValue.trim());
            updateFromInputs();
        });

        InputTextField holderNameField = addInputTextField(gridPane, ++gridRow, Res.get("payment.account.owner"));
        holderNameField.setValidator(inputValidator);
        holderNameField.textProperty().addListener((ov, oldValue, newValue) -> {
            account.setHolderName(newValue.trim());
            updateFromInputs();
        });

        String addressLabel = Res.get("payment.account.owner.address") + Res.get("payment.transferwiseUsd.address");
        TextArea addressTextArea = addTopLabelTextArea(gridPane, ++gridRow, addressLabel, addressLabel).second;
        addressTextArea.setMinHeight(70);
        addressTextArea.textProperty().addListener((ov, oldValue, newValue) -> {
            account.setBeneficiaryAddress(newValue.trim());
            updateFromInputs();
        });

        addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), account.getSingleTradeCurrency().getNameAndCode());
        addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.country"), account.getCountry().name);
        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    @Override
    protected void autoFillNameTextField() {
        setAccountNameWithString(account.getHolderName());
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                account.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(account.getPaymentMethod().getId()));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.email"), account.getEmail());
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.owner"), account.getHolderName());
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.address"), cleanString(account.getBeneficiaryAddress()));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), account.getSingleTradeCurrency().getNameAndCode());
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.country"), account.getCountry().name);
        addLimitations(true);
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && emailValidator.validate(account.getEmail()).isValid
                && inputValidator.validate(account.getHolderName()).isValid
                && addressValidator.validate(account.getBeneficiaryAddress()).isValid
        );
    }
}
