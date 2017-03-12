/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.payment;

import io.bisq.app.Version;
import io.bisq.messages.locale.FiatCurrency;
import io.bisq.messages.payment.PaymentMethod;
import io.bisq.messages.payment.payload.InteracETransferAccountContractData;
import io.bisq.messages.payment.payload.PaymentAccountContractData;

public final class InteracETransferAccount extends PaymentAccount {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    public InteracETransferAccount() {
        super(PaymentMethod.INTERAC_E_TRANSFER);
        setSingleTradeCurrency(new FiatCurrency("CAD"));
    }

    @Override
    protected PaymentAccountContractData setContractData() {
        return new InteracETransferAccountContractData(paymentMethod.getId(), id, paymentMethod.getMaxTradePeriod());
    }

    public void setEmail(String email) {
        ((InteracETransferAccountContractData) contractData).setEmail(email);
    }

    public String getEmail() {
        return ((InteracETransferAccountContractData) contractData).getEmail();
    }

    public void setAnswer(String answer) {
        ((InteracETransferAccountContractData) contractData).setAnswer(answer);
    }

    public String getAnswer() {
        return ((InteracETransferAccountContractData) contractData).getAnswer();
    }

    public void setQuestion(String question) {
        ((InteracETransferAccountContractData) contractData).setQuestion(question);
    }

    public String getQuestion() {
        return ((InteracETransferAccountContractData) contractData).getQuestion();
    }

    public void setHolderName(String holderName) {
        ((InteracETransferAccountContractData) contractData).setHolderName(holderName);
    }

    public String getHolderName() {
        return ((InteracETransferAccountContractData) contractData).getHolderName();
    }
}
