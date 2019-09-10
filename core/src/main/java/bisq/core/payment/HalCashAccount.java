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

import bisq.core.locale.CountryUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.payment.payload.HalCashAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.List;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class HalCashAccount extends CountryBasedPaymentAccount {
    public HalCashAccount() {
        super(PaymentMethod.HAL_CASH);
        setSingleTradeCurrency(new FiatCurrency("EUR"));
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new HalCashAccountPayload(paymentMethod.getId(), id, CountryUtil.getAllHalCashCountries());
    }

    public List<String> getAcceptedCountryCodes() {
        return ((HalCashAccountPayload) paymentAccountPayload).getAcceptedCountryCodes();
    }

    public void addAcceptedCountry(String countryCode) {
        ((HalCashAccountPayload) paymentAccountPayload).addAcceptedCountry(countryCode);
    }

    public void removeAcceptedCountry(String countryCode) {
        ((HalCashAccountPayload) paymentAccountPayload).removeAcceptedCountry(countryCode);
    }

    public String getMobileNr() {
        return ((HalCashAccountPayload) paymentAccountPayload).getMobileNr();
    }

    public void setMobileNr(String mobileNr) {
        ((HalCashAccountPayload) paymentAccountPayload).setMobileNr(mobileNr);
    }
}
