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
import io.bisq.common.locale.Country;
import io.bisq.protobuffer.payload.payment.CountryBasedPaymentAccountPayload;
import io.bisq.protobuffer.payload.payment.PaymentMethod;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public abstract class CountryBasedPaymentAccount extends PaymentAccount {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(CountryBasedPaymentAccount.class);

    @Nullable
    protected Country country;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////


    protected CountryBasedPaymentAccount(PaymentMethod paymentMethod) {
        super(paymentMethod);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter, Setter
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Nullable
    public Country getCountry() {
        return country;
    }

    public void setCountry(@NotNull Country country) {
        this.country = country;
        ((CountryBasedPaymentAccountPayload) paymentAccountPayload).setCountryCode(country.code);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CountryBasedPaymentAccount)) return false;
        if (!super.equals(o)) return false;

        CountryBasedPaymentAccount that = (CountryBasedPaymentAccount) o;

        return !(country != null ? !country.equals(that.country) : that.country != null);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (country != null ? country.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CountryBasedPaymentAccount{" +
                "country=" + country +
                "} " + super.toString();
    }
}
