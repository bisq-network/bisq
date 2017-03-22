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
import io.bisq.common.locale.CountryUtil;
import io.bisq.core.user.Preferences;
import io.bisq.protobuffer.payload.payment.PaymentAccountPayload;
import io.bisq.protobuffer.payload.payment.PaymentMethod;
import io.bisq.protobuffer.payload.payment.SepaAccountPayload;

import java.util.List;

public final class SepaAccount extends CountryBasedPaymentAccount implements BankAccount {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    public SepaAccount() {
        super(PaymentMethod.SEPA);
    }

    @Override
    protected PaymentAccountPayload setPayload() {
        return new SepaAccountPayload(paymentMethod.getId(), id, paymentMethod.getMaxTradePeriod(),
                CountryUtil.getAllSepaCountries(Preferences.getDefaultLocale()));
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
