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

package bisq.core.payment.payload;

import bisq.core.locale.Res;

import com.google.protobuf.Message;

import org.apache.commons.lang3.ArrayUtils;

import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;

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
public final class InteracETransferAccountPayload extends PaymentAccountPayload implements PayloadWithHolderName {
    private String email = "";
    private String holderName = "";
    private String question = "";
    private String answer = "";

    public InteracETransferAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private InteracETransferAccountPayload(String paymentMethod,
                                           String id,
                                           String email,
                                           String holderName,
                                           String question,
                                           String answer,
                                           long maxTradePeriod,
                                           Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethod,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);
        this.email = email;
        this.holderName = holderName;
        this.question = question;
        this.answer = answer;
    }

    @Override
    public Message toProtoMessage() {
        return getPaymentAccountPayloadBuilder()
                .setInteracETransferAccountPayload(protobuf.InteracETransferAccountPayload.newBuilder()
                        .setEmail(email)
                        .setHolderName(holderName)
                        .setQuestion(question)
                        .setAnswer(answer))
                .build();
    }

    public static InteracETransferAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        return new InteracETransferAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                proto.getInteracETransferAccountPayload().getEmail(),
                proto.getInteracETransferAccountPayload().getHolderName(),
                proto.getInteracETransferAccountPayload().getQuestion(),
                proto.getInteracETransferAccountPayload().getAnswer(),
                proto.getMaxTradePeriod(),
                new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " + Res.getWithCol("payment.account.owner") + " " + holderName + ", " +
                Res.get("payment.email") + " " + email + ", " + Res.getWithCol("payment.secret") + " " +
                question + ", " + Res.getWithCol("payment.answer") + " " + answer;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return Res.getWithCol("payment.account.owner") + " " + holderName + "\n" +
                Res.getWithCol("payment.email") + " " + email + "\n" +
                Res.getWithCol("payment.secret") + " " + question + "\n" +
                Res.getWithCol("payment.answer") + " " + answer;
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        return super.getAgeWitnessInputData(ArrayUtils.addAll(email.getBytes(StandardCharsets.UTF_8),
                ArrayUtils.addAll(question.getBytes(StandardCharsets.UTF_8),
                        answer.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public String getOwnerId() {
        return holderName;
    }
}
