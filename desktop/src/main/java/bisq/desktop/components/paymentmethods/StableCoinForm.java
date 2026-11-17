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
import bisq.desktop.util.FormBuilder;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.StableCoinAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.StableCoinAccountPayload;
import bisq.core.payment.stablecoin.StableCoin;
import bisq.core.payment.validation.StableCoinAddressValidator;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.layout.GridPane;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextFieldWithCopyIcon;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addTopLabelTextFieldWithCopyIcon;

/**
 * The form of all stablecoin payment methods. The coin, its network and its token contract are
 * given by the payment method, the user enters the address.
 */
public class StableCoinForm extends PaymentMethodForm {
    private final StableCoinAccount stableCoinAccount;
    private final StableCoin stableCoin;
    private final StableCoinAddressValidator addressValidator;

    /**
     * @param stableCoin the coin of the payment method of the offer. The payment account data comes from the peer, so
     *                   the coin, its network and its token contract must not be taken from it.
     */
    public static int addFormForBuyer(GridPane gridPane,
                                      int gridRow,
                                      PaymentAccountPayload paymentAccountPayload,
                                      StableCoin stableCoin) {
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.stablecoin.receiver.address"),
                ((StableCoinAccountPayload) paymentAccountPayload).getAddress());
        addCompactTopLabelTextField(gridPane, gridRow, 1, Res.get("payment.stablecoin.network"),
                getNetworkDisplayString(stableCoin));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.stablecoin.coin"),
                getCoinDisplayString(stableCoin));
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, gridRow, 1, Res.get("payment.stablecoin.tokenContract"),
                stableCoin.getTokenContract());
        return gridRow;
    }

    // E.g. Tether USD (USDT)
    public static String getCoinDisplayString(StableCoin stableCoin) {
        return stableCoin.getName() + " (" + stableCoin.getCode() + ")";
    }

    // E.g. Tron (TRC-20)
    public static String getNetworkDisplayString(StableCoin stableCoin) {
        return stableCoin.getNetwork().getDisplayName() + " (" + stableCoin.getTokenStandard().getDisplayName() + ")";
    }

    public StableCoinForm(PaymentAccount paymentAccount,
                          AccountAgeWitnessService accountAgeWitnessService,
                          InputValidator inputValidator,
                          GridPane gridPane,
                          int gridRow,
                          CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.stableCoinAccount = (StableCoinAccount) paymentAccount;
        this.stableCoin = stableCoinAccount.getStableCoin();
        this.addressValidator = new StableCoinAddressValidator(stableCoin);
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        addTopLabelTextField(gridPane, ++gridRow, Res.get("payment.stablecoin.coin"),
                getCoinDisplayString(stableCoin));
        addTopLabelTextField(gridPane, ++gridRow, Res.get("payment.stablecoin.network"),
                getNetworkDisplayString(stableCoin));
        addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.stablecoin.tokenContract"),
                stableCoin.getTokenContract());

        InputTextField addressInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow,
                Res.get("payment.stablecoin.address"));
        addressInputTextField.setValidator(addressValidator);
        addressInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            stableCoinAccount.setAddress(addressValidator.toStoredForm(newValue));
            updateFromInputs();
        });

        addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), getCurrencyNameAndCode());
        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    @Override
    protected void autoFillNameTextField() {
        setAccountNameWithString(stableCoinAccount.getAddress());
    }

    @Override
    public void addFormForEditAccount() {
        gridRowFrom = gridRow;
        addAccountNameTextFieldWithAutoFillToggleButton();
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(stableCoinAccount.getPaymentMethod().getId()));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.stablecoin.network"),
                getNetworkDisplayString(stableCoin));
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.stablecoin.tokenContract"),
                stableCoin.getTokenContract());
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.stablecoin.address"),
                stableCoinAccount.getAddress());
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), getCurrencyNameAndCode());
        addLimitations(true);
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && addressValidator.validate(stableCoinAccount.getAddress()).isValid
                && stableCoinAccount.getTradeCurrencies().size() > 0);
    }

    private String getCurrencyNameAndCode() {
        TradeCurrency singleTradeCurrency = stableCoinAccount.getSingleTradeCurrency();
        return singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
    }
}
