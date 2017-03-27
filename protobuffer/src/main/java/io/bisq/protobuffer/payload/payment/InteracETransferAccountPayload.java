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

package io.bisq.protobuffer.payload.payment;

import io.bisq.common.app.Version;
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
public final class InteracETransferAccountPayload extends PaymentAccountPayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private String email;
    private String holderName;
    private String question;
    private String answer;

    public InteracETransferAccountPayload(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    public InteracETransferAccountPayload(String paymentMethodName, String id, long maxTradePeriod,
                                          String email, String holderName, String question, String answer) {
        super(paymentMethodName, id, maxTradePeriod);
        this.email = email;
        this.holderName = holderName;
        this.question = question;
        this.answer = answer;
    }

    @Override
    public String getPaymentDetails() {
        return "Interac e-Transfer - Holder name: " + holderName + ", email: " + email + ", secret question: " + question + ", answer: " + answer;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return "Holder name: " + holderName + "\n" +
                "Email: " + email + "\n" +
                "Secret question: " + question + "\n" +
                "Answer: " + answer;
    }

    @Override
    public PB.PaymentAccountPayload toProto() {
        PB.InteracETransferAccountPayload.Builder interacETransferAccountPayload =
                PB.InteracETransferAccountPayload.newBuilder()
                        .setEmail(email)
                        .setHolderName(holderName)
                        .setQuestion(question)
                        .setAnswer(answer);
        PB.PaymentAccountPayload.Builder paymentAccountPayload =
                PB.PaymentAccountPayload.newBuilder()
                        .setId(id)
                        .setPaymentMethodId(paymentMethodId)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setInteracETransferAccountPayload(interacETransferAccountPayload);
        return paymentAccountPayload.build();
    }
}
