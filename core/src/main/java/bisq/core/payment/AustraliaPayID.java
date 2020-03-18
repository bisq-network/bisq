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

import bisq.core.payment.payload.AustraliaPayIDPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.AustraliaPayIDPayload;

import org.jetbrains.annotations.NotNull;

import lombok.Getter;
import lombok.Setter;

import bisq.core.locale.Country;
import bisq.core.locale.FiatCurrency;
import bisq.core.payment.payload.AustraliaPayIDPayload;

public final class AustraliaPayID extends PaymentAccount
{
    public AustraliaPayID()
    {
        super(PaymentMethod.AUSTRALIA_PAYID);
        setSingleTradeCurrency(new FiatCurrency("AUD"));
    }

    @Override
    protected PaymentAccountPayload createPayload()
    {
        return new AustraliaPayIDPayload(paymentMethod.getId(), id);
    }

    // payid
    public String getPayID()
    {
        return ((AustraliaPayIDPayload) paymentAccountPayload).getPayid();
    }
    public void setPayID(String payid)
    {
        if (payid == null) payid = "";
        ((AustraliaPayIDPayload) paymentAccountPayload).setPayid(payid);
    }

    // bankAccount name
    public String getBankAccountName()
    {
        return ((AustraliaPayIDPayload) paymentAccountPayload).getBankAccountName();
    }
    public void setBankAccountName(String bankAccountName)
    {
        if (bankAccountName == null) bankAccountName = "";
        ((AustraliaPayIDPayload) paymentAccountPayload).setBankAccountName(bankAccountName);
    }
}
