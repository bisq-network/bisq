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

package io.bisq.wire.payload.payment;

import io.bisq.common.app.Version;
import io.bisq.wire.proto.Messages;

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

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
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
    public Messages.PaymentAccountPayload toProtoBuf() {
        Messages.InteracETransferAccountPayload.Builder interacETransferAccountPayload =
                Messages.InteracETransferAccountPayload.newBuilder()
                        .setEmail(email)
                        .setHolderName(holderName)
                        .setQuestion(question)
                        .setAnswer(answer);
        Messages.PaymentAccountPayload.Builder paymentAccountPayload =
                Messages.PaymentAccountPayload.newBuilder()
                        .setId(id)
                        .setPaymentMethodId(paymentMethodId)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setInteracETransferAccountPayload(interacETransferAccountPayload);
        return paymentAccountPayload.build();
    }
}
