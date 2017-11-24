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
import io.bisq.core.payment.payload.SepaInstantAccountPayload;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
public final class SepaInstantAccount extends CountryBasedPaymentAccount implements BankAccount {
    public SepaInstantAccount() {
        super(PaymentMethod.SEPA_INSTANT);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new SepaInstantAccountPayload(paymentMethod.getId(), id,
                CountryUtil.getAllSepaInstantCountries());
    }

    @Override
    public String getBankId() {
        return ((SepaInstantAccountPayload) paymentAccountPayload).getBic();
    }

    public void setHolderName(String holderName) {
        ((SepaInstantAccountPayload) paymentAccountPayload).setHolderName(holderName);
    }

    public String getHolderName() {
        return ((SepaInstantAccountPayload) paymentAccountPayload).getHolderName();
    }

    public void setIban(String iban) {
        ((SepaInstantAccountPayload) paymentAccountPayload).setIban(iban);
    }

    public String getIban() {
        return ((SepaInstantAccountPayload) paymentAccountPayload).getIban();
    }

    public void setBic(String bic) {
        ((SepaInstantAccountPayload) paymentAccountPayload).setBic(bic);
    }

    public String getBic() {
        return ((SepaInstantAccountPayload) paymentAccountPayload).getBic();
    }

    public List<String> getAcceptedCountryCodes() {
        return ((SepaInstantAccountPayload) paymentAccountPayload).getAcceptedCountryCodes();
    }

    public void addAcceptedCountry(String countryCode) {
        ((SepaInstantAccountPayload) paymentAccountPayload).addAcceptedCountry(countryCode);
    }

    public void removeAcceptedCountry(String countryCode) {
        ((SepaInstantAccountPayload) paymentAccountPayload).removeAcceptedCountry(countryCode);
    }
}
