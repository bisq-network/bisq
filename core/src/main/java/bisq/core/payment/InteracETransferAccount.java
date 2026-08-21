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

package bisq.core.payment;

import bisq.core.locale.FiatCurrency;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.payload.InteracETransferAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.List;

import lombok.EqualsAndHashCode;

import org.jetbrains.annotations.NotNull;

@EqualsAndHashCode(callSuper = true)
public final class InteracETransferAccount extends PaymentAccount {

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(new FiatCurrency("CAD"));

    public InteracETransferAccount() {
        super(PaymentMethod.INTERAC_E_TRANSFER);
        setSingleTradeCurrency(SUPPORTED_CURRENCIES.get(0));
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new InteracETransferAccountPayload(paymentMethod.getId(), id);
    }

    @NotNull
    @Override
    public List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
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
