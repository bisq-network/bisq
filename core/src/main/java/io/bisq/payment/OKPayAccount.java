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

package io.bisq.payment;

import io.bisq.app.Version;
import io.bisq.messages.locale.CurrencyUtil;
import io.bisq.messages.payment.PaymentMethod;
import io.bisq.messages.payment.payload.OKPayAccountContractData;
import io.bisq.messages.payment.payload.PaymentAccountContractData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO missing support for selected trade currency
public final class OKPayAccount extends PaymentAccount {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(OKPayAccount.class);

    public OKPayAccount() {
        super(PaymentMethod.OK_PAY);
        tradeCurrencies.addAll(CurrencyUtil.getAllOKPayCurrencies());
    }

    @Override
    protected PaymentAccountContractData setContractData() {
        return new OKPayAccountContractData(paymentMethod.getId(), id, paymentMethod.getMaxTradePeriod());
    }

    public void setAccountNr(String accountNr) {
        ((OKPayAccountContractData) contractData).setAccountNr(accountNr);
    }

    public String getAccountNr() {
        return ((OKPayAccountContractData) contractData).getAccountNr();
    }
}
