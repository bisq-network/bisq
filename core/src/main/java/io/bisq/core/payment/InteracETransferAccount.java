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

package io.bisq.core.payment;

import io.bisq.common.app.Version;
import io.bisq.common.locale.FiatCurrency;
import io.bisq.core.user.Preferences;
import io.bisq.protobuffer.payload.payment.InteracETransferAccountPayload;
import io.bisq.protobuffer.payload.payment.PaymentAccountPayload;
import io.bisq.protobuffer.payload.payment.PaymentMethod;

public final class InteracETransferAccount extends PaymentAccount {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    public InteracETransferAccount() {
        super(PaymentMethod.INTERAC_E_TRANSFER);
        setSingleTradeCurrency(new FiatCurrency("CAD", Preferences.getDefaultLocale()));
    }

    @Override
    protected PaymentAccountPayload setPayload() {
        return new InteracETransferAccountPayload(paymentMethod.getId(), id, paymentMethod.getMaxTradePeriod());
    }

    public void setEmail(String email) {
        ((InteracETransferAccountPayload) paymentAccountPayload).setEmail(email);
    }

    public String getEmail() {
        return ((InteracETransferAccountPayload) paymentAccountPayload).getEmail();
    }

    public void setAnswer(String answer) {
        ((InteracETransferAccountPayload) paymentAccountPayload).setAnswer(answer);
    }

    public String getAnswer() {
        return ((InteracETransferAccountPayload) paymentAccountPayload).getAnswer();
    }

    public void setQuestion(String question) {
        ((InteracETransferAccountPayload) paymentAccountPayload).setQuestion(question);
    }

    public String getQuestion() {
        return ((InteracETransferAccountPayload) paymentAccountPayload).getQuestion();
    }

    public void setHolderName(String holderName) {
        ((InteracETransferAccountPayload) paymentAccountPayload).setHolderName(holderName);
    }

    public String getHolderName() {
        return ((InteracETransferAccountPayload) paymentAccountPayload).getHolderName();
    }
}
