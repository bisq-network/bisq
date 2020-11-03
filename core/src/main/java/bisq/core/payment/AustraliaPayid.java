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
import bisq.core.payment.payload.AustraliaPayidPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

public final class AustraliaPayid extends PaymentAccount {
    public AustraliaPayid() {
        super(PaymentMethod.AUSTRALIA_PAYID);
        setSingleTradeCurrency(new FiatCurrency("AUD"));
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new AustraliaPayidPayload(paymentMethod.getId(), id);
    }

    public String getPayid() {
        return ((AustraliaPayidPayload) paymentAccountPayload).getPayid();
    }

    public void setPayid(String payid) {
        if (payid == null) payid = "";
        ((AustraliaPayidPayload) paymentAccountPayload).setPayid(payid);
    }

    public String getBankAccountName() {
        return ((AustraliaPayidPayload) paymentAccountPayload).getBankAccountName();
    }

    public void setBankAccountName(String bankAccountName) {
        if (bankAccountName == null) bankAccountName = "";
        ((AustraliaPayidPayload) paymentAccountPayload).setBankAccountName(bankAccountName);
    }
}
