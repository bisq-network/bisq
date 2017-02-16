/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.messages.payment.payload;

import io.bitsquare.messages.app.Version;
import io.bitsquare.common.wire.proto.Messages;

public final class AliPayAccountContractData extends PaymentAccountContractData {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private String accountNr;

    public AliPayAccountContractData(String paymentMethod, String id, long maxTradePeriod, String accountNr) {
        this(paymentMethod, id, maxTradePeriod);
        setAccountNr(accountNr);
    }

    public AliPayAccountContractData(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    public void setAccountNr(String accountNr) {
        this.accountNr = accountNr;
    }

    public String getAccountNr() {
        return accountNr;
    }

    @Override
    public String getPaymentDetails() {
        return "AliPay - Account no.: " + accountNr;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return getPaymentDetails();
    }

    @Override
    public Messages.PaymentAccountContractData toProtoBuf() {
        Messages.AliPayAccountContractData.Builder thisClass =
                Messages.AliPayAccountContractData.newBuilder().setAccountNr(accountNr);
        Messages.PaymentAccountContractData.Builder paymentAccountContractData =
                Messages.PaymentAccountContractData.newBuilder()
                        .setId(id)
                        .setPaymentMethodName(paymentMethodName)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setAliPayAccountContractData(thisClass);
        return paymentAccountContractData.build();
    }

    @Override
    public String toString() {
        return "AliPayAccountContractData{" +
                "accountNr='" + accountNr + '\'' +
                '}';
    }
}
