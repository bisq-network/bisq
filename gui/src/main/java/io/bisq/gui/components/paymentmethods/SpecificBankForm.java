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

import com.google.common.base.Joiner;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple3;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.SpecificBanksAccountPayload;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.validation.InputValidator;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;

import static io.bisq.gui.util.FormBuilder.*;

public class SpecificBankForm extends BankForm {
    private final SpecificBanksAccountPayload specificBanksAccountPayload;
    private TextField acceptedBanksTextField;
    private Tooltip acceptedBanksTooltip;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        return BankForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
    }

    public SpecificBankForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator,
                            GridPane gridPane, int gridRow, BSFormatter formatter, Runnable closeHandler) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter, closeHandler);
        this.specificBanksAccountPayload = (SpecificBanksAccountPayload) paymentAccount.paymentAccountPayload;
    }

    @Override
    protected void addAcceptedBanksForAddAccount() {
        Tuple3<Label, InputTextField, Button> addBankTuple = addLabelInputTextFieldButton(gridPane, ++gridRow,
                Res.get("payment.nameOfAcceptedBank"), Res.get("payment.addAcceptedBank"));
        InputTextField addBankInputTextField = addBankTuple.second;
        Button addButton = addBankTuple.third;
        addButton.setMinWidth(200);
        addButton.disableProperty().bind(Bindings.createBooleanBinding(() -> addBankInputTextField.getText().isEmpty(),
                addBankInputTextField.textProperty()));

        Tuple3<Label, TextField, Button> acceptedBanksTuple = addLabelTextFieldButton(gridPane, ++gridRow,
                Res.get("payment.accepted.banks"), Res.get("payment.clearAcceptedBanks"));
        acceptedBanksTextField = acceptedBanksTuple.second;
        acceptedBanksTextField.setMouseTransparent(false);
        acceptedBanksTooltip = new Tooltip();
        acceptedBanksTextField.setTooltip(acceptedBanksTooltip);
        Button clearButton = acceptedBanksTuple.third;
        clearButton.setMinWidth(200);
        clearButton.setDefaultButton(false);
        clearButton.disableProperty().bind(Bindings.createBooleanBinding(() -> acceptedBanksTextField.getText().isEmpty(), acceptedBanksTextField.textProperty()));
        addButton.setOnAction(e -> {
            specificBanksAccountPayload.addAcceptedBank(addBankInputTextField.getText());
            addBankInputTextField.setText("");
            String value = Joiner.on(", ").join(specificBanksAccountPayload.getAcceptedBanks());
            acceptedBanksTextField.setText(value);
            acceptedBanksTooltip.setText(value);
            updateAllInputsValid();
        });

        clearButton.setOnAction(e -> resetAcceptedBanks());
    }

    private void resetAcceptedBanks() {
        specificBanksAccountPayload.clearAcceptedBanks();
        acceptedBanksTextField.setText("");
        acceptedBanksTooltip.setText("");
        updateAllInputsValid();
    }

    @Override
    protected void onCountryChanged() {
        resetAcceptedBanks();
    }

    @Override
    public void addAcceptedBanksForDisplayAccount() {
        addLabelTextField(gridPane, ++gridRow, Res.get("payment.accepted.banks"),
                Joiner.on(", ").join(specificBanksAccountPayload.getAcceptedBanks())).second.setMouseTransparent(false);
    }

    @Override
    public void updateAllInputsValid() {
        super.updateAllInputsValid();
        allInputsValid.set(allInputsValid.get() && inputValidator.validate(acceptedBanksTextField.getText()).isValid);
    }

}
