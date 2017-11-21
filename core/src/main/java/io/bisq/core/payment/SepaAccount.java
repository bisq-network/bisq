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

package io.bisq.core.payment;

import io.bisq.common.locale.CountryUtil;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.PaymentMethod;
import io.bisq.core.payment.payload.SepaAccountPayload;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
public final class SepaAccount extends CountryBasedPaymentAccount implements BankAccount {
    public SepaAccount() {
        super(PaymentMethod.SEPA);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new SepaAccountPayload(paymentMethod.getId(), id,
                CountryUtil.getAllSepaCountries());
    }

    @Override
    public String getBankId() {
        return ((SepaAccountPayload) paymentAccountPayload).getBic();
    }

    public void setHolderName(String holderName) {
        ((SepaAccountPayload) paymentAccountPayload).setHolderName(holderName);
    }

    public String getHolderName() {
        return ((SepaAccountPayload) paymentAccountPayload).getHolderName();
    }

    public void setIban(String iban) {
        ((SepaAccountPayload) paymentAccountPayload).setIban(iban);
    }

    public String getIban() {
        return ((SepaAccountPayload) paymentAccountPayload).getIban();
    }

    public void setBic(String bic) {
        ((SepaAccountPayload) paymentAccountPayload).setBic(bic);
    }

    public String getBic() {
        return ((SepaAccountPayload) paymentAccountPayload).getBic();
    }

    public List<String> getAcceptedCountryCodes() {
        return ((SepaAccountPayload) paymentAccountPayload).getAcceptedCountryCodes();
    }

    public void addAcceptedCountry(String countryCode) {
        ((SepaAccountPayload) paymentAccountPayload).addAcceptedCountry(countryCode);
    }

    public void removeAcceptedCountry(String countryCode) {
        ((SepaAccountPayload) paymentAccountPayload).removeAcceptedCountry(countryCode);
    }
}
