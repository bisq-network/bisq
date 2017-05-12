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

package io.bisq.core.payment.payload;

import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@ToString
@Setter
@Getter
@Slf4j
public final class OKPayAccountPayload extends PaymentAccountPayload {

    private String accountNr;

    public OKPayAccountPayload(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    public OKPayAccountPayload(String paymentMethodName, String id, long maxTradePeriod, String accountNr) {
        super(paymentMethodName, id, maxTradePeriod);
        this.accountNr = accountNr;
    }

    @Override
    public String getPaymentDetails() {
        return "OKPay - Account no.: " + accountNr;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return getPaymentDetails();
    }

    @Override
    public PB.PaymentAccountPayload toProtoMessage() {
        PB.OKPayAccountPayload.Builder builder = PB.OKPayAccountPayload.newBuilder()
                .setAccountNr(accountNr);

        PB.PaymentAccountPayload.Builder paymentAccountPayload =
                PB.PaymentAccountPayload.newBuilder()
                        .setId(id)
                        .setPaymentMethodId(paymentMethodId)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setOKPayAccountPayload(builder);
        return paymentAccountPayload.build();
    }
}
