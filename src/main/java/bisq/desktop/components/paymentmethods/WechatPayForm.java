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

import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.WechatPayAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.WechatPayAccountPayload;
import bisq.core.util.validation.InputValidator;
import bisq.desktop.components.InputTextField;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.WechatPayValidator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.FormBuilder.*;

public class WechatPayForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(WechatPayForm.class);

    private final WechatPayAccount wechatPayAccount;
    private final WechatPayValidator wechatPayValidator;
    private InputTextField accountNrInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.account.no"), ((WechatPayAccountPayload) paymentAccountPayload).getAccountNr());
        return gridRow;
    }

    public WechatPayForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, WechatPayValidator wechatPayValidator, InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.wechatPayAccount = (WechatPayAccount) paymentAccount;
        this.wechatPayValidator = wechatPayValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        accountNrInputTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.account.no")).second;
        accountNrInputTextField.setValidator(wechatPayValidator);
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            wechatPayAccount.setAccountNr(newValue);
            updateFromInputs();
        });

        final TradeCurrency singleTradeCurrency = wechatPayAccount.getSingleTradeCurrency();
        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addLimitations();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String accountNr = accountNrInputTextField.getText();
            accountNr = StringUtils.abbreviate(accountNr, 9);
            String method = Res.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(": ").concat(accountNr));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), wechatPayAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"), Res.get(wechatPayAccount.getPaymentMethod().getId()));
        TextField field = addLabelTextField(gridPane, ++gridRow, Res.get("payment.account.no"), wechatPayAccount.getAccountNr()).second;
        field.setMouseTransparent(false);
        final TradeCurrency singleTradeCurrency = wechatPayAccount.getSingleTradeCurrency();
        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addLimitations();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && wechatPayValidator.validate(wechatPayAccount.getAccountNr()).isValid
                && wechatPayAccount.getTradeCurrencies().size() > 0);
    }

}
