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
import io.bisq.protobuffer.payload.payment.PaymentAccountPayload;
import io.bisq.protobuffer.payload.payment.PaymentMethod;
import io.bisq.protobuffer.payload.payment.USPostalMoneyOrderAccountPayload;

public final class USPostalMoneyOrderAccount extends PaymentAccount {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    public USPostalMoneyOrderAccount() {
        super(PaymentMethod.US_POSTAL_MONEY_ORDER);
        setSingleTradeCurrency(new FiatCurrency("USD", Preferences.getDefaultLocale()));
    }

    @Override
    protected PaymentAccountPayload setPayload() {
        return new USPostalMoneyOrderAccountPayload(paymentMethod.getId(), id, paymentMethod.getMaxTradePeriod());
    }

    public void setPostalAddress(String postalAddress) {
        ((USPostalMoneyOrderAccountPayload) paymentAccountPayload).setPostalAddress(postalAddress);
    }

    public String getPostalAddress() {
        return ((USPostalMoneyOrderAccountPayload) paymentAccountPayload).getPostalAddress();
    }

    public void setHolderName(String holderName) {
        ((USPostalMoneyOrderAccountPayload) paymentAccountPayload).setHolderName(holderName);
    }

    public String getHolderName() {
        return ((USPostalMoneyOrderAccountPayload) paymentAccountPayload).getHolderName();
    }
}
