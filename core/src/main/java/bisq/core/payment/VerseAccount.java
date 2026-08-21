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
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.VerseAccountPayload;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

// Verse was shut down
@Deprecated
@EqualsAndHashCode(callSuper = true)
public final class VerseAccount extends PaymentAccount {

    // https://github.com/bisq-network/growth/issues/223
    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(
            new FiatCurrency("DKK"),
            new FiatCurrency("EUR"),
            new FiatCurrency("HUF"),
            new FiatCurrency("PLN"),
            new FiatCurrency("SEK")
    );

    public VerseAccount() {
        super(PaymentMethod.VERSE);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new VerseAccountPayload(paymentMethod.getId(), id);
    }

    public void setHolderName(String accountId) {
        ((VerseAccountPayload) paymentAccountPayload).setHolderName(accountId);
    }

    public String getHolderName() {
        return ((VerseAccountPayload) paymentAccountPayload).getHolderName();
    }

    public String getMessageForBuyer() {
        return "payment.verse.info.buyer";
    }

    public String getMessageForSeller() {
        return "payment.verse.info.seller";
    }

    public String getMessageForAccountCreation() {
        return "payment.verse.info.account";
    }

    @Override
    public @NonNull List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }
}
