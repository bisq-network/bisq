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

import bisq.core.locale.CurrencyUtil;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.RevolutAccountPayload;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class RevolutAccount extends PaymentAccount {
    public RevolutAccount() {
        super(PaymentMethod.REVOLUT);
        tradeCurrencies.addAll(CurrencyUtil.getAllRevolutCurrencies());
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new RevolutAccountPayload(paymentMethod.getId(), id);
    }

    public void setUserName(String userName) {
        revolutAccountPayload().setUserName(userName);
    }

    public String getUserName() {
        return (revolutAccountPayload()).getUserName();
    }

    public String getAccountId() {
        return (revolutAccountPayload()).getAccountId();
    }

    public boolean userNameNotSet() {
        return (revolutAccountPayload()).userNameNotSet();
    }

    public boolean hasOldAccountId() {
        return (revolutAccountPayload()).hasOldAccountId();
    }

    private RevolutAccountPayload revolutAccountPayload() {
        return (RevolutAccountPayload) paymentAccountPayload;
    }

    @Override
    public void onAddToUser() {
        super.onAddToUser();

        // At save we apply the userName to accountId in case it is empty for backward compatibility
        revolutAccountPayload().maybeApplyUserNameToAccountId();
    }
}
