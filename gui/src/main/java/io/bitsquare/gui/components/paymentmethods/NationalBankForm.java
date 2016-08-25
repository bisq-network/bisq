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

import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NationalBankForm extends BankForm {
    private static final Logger log = LoggerFactory.getLogger(NationalBankForm.class);

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        return BankForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
    }

    public NationalBankForm(PaymentAccount paymentAccount, InputValidator inputValidator,
                            GridPane gridPane, int gridRow, BSFormatter formatter, Runnable closeHandler) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter, closeHandler);
    }
}
