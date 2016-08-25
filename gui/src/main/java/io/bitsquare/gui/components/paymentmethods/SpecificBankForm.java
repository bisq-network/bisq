/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.components.paymentmethods;

import com.google.common.base.Joiner;
import io.bitsquare.common.util.Tuple3;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.payment.SpecificBanksAccountContractData;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.*;

public class SpecificBankForm extends BankForm {
    private static final Logger log = LoggerFactory.getLogger(SpecificBankForm.class);

    private final SpecificBanksAccountContractData specificBanksAccountContractData;
    private TextField acceptedBanksTextField;
    private Tooltip acceptedBanksTooltip;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        return BankForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
    }

    public SpecificBankForm(PaymentAccount paymentAccount, InputValidator inputValidator,
                            GridPane gridPane, int gridRow, BSFormatter formatter, Runnable closeHandler) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter, closeHandler);
        this.specificBanksAccountContractData = (SpecificBanksAccountContractData) paymentAccount.contractData;
    }

    @Override
    protected void addAcceptedBanksForAddAccount() {
        Tuple3<Label, InputTextField, Button> addBankTuple = addLabelInputTextFieldButton(gridPane, ++gridRow, "Add name of accepted bank:", "Add accepted bank");
        InputTextField addBankInputTextField = addBankTuple.second;
        Button addButton = addBankTuple.third;
        addButton.setMinWidth(200);
        addButton.disableProperty().bind(Bindings.createBooleanBinding(() -> addBankInputTextField.getText().isEmpty(), addBankInputTextField.textProperty()));

        Tuple3<Label, TextField, Button> acceptedBanksTuple = addLabelTextFieldButton(gridPane, ++gridRow, "Accepted banks:", "Clear accepted banks");
        acceptedBanksTextField = acceptedBanksTuple.second;
        acceptedBanksTextField.setMouseTransparent(false);
        acceptedBanksTooltip = new Tooltip();
        acceptedBanksTextField.setTooltip(acceptedBanksTooltip);
        Button clearButton = acceptedBanksTuple.third;
        clearButton.setMinWidth(200);
        clearButton.setDefaultButton(false);
        clearButton.disableProperty().bind(Bindings.createBooleanBinding(() -> acceptedBanksTextField.getText().isEmpty(), acceptedBanksTextField.textProperty()));
        addButton.setOnAction(e -> {
            specificBanksAccountContractData.addAcceptedBank(addBankInputTextField.getText());
            addBankInputTextField.setText("");
            String value = Joiner.on(", ").join(specificBanksAccountContractData.getAcceptedBanks());
            acceptedBanksTextField.setText(value);
            acceptedBanksTooltip.setText(value);
            updateAllInputsValid();
        });

        clearButton.setOnAction(e -> resetAcceptedBanks());
    }

    private void resetAcceptedBanks() {
        specificBanksAccountContractData.clearAcceptedBanks();
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
        addLabelTextField(gridPane, ++gridRow, "Accepted banks:",
                Joiner.on(", ").join(specificBanksAccountContractData.getAcceptedBanks())).second.setMouseTransparent(false);
    }

    @Override
    public void updateAllInputsValid() {
        super.updateAllInputsValid();
        allInputsValid.set(allInputsValid.get() && inputValidator.validate(acceptedBanksTextField.getText()).isValid);
    }

}
