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

import org.springframework.util.CollectionUtils;

import org.apache.commons.lang3.ArrayUtils;

import java.nio.charset.Charset;

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@EqualsAndHashCode(callSuper = true)
@ToString
@Getter
@Slf4j
public final class FasterPaymentsAccountPayload extends PaymentAccountPayload {
    @Setter
    private String sortCode = "";
    @Setter
    private String accountNr = "";
    private String email = "";// not used anymore but need to keep it for backward compatibility, must not be null but empty string, otherwise hash check fails for contract

    public FasterPaymentsAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private FasterPaymentsAccountPayload(String paymentMethod,
                                         String id,
                                         String sortCode,
                                         String accountNr,
                                         String email,
                                         long maxTradePeriod,
                                         @Nullable Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethod,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);
        this.sortCode = sortCode;
        this.accountNr = accountNr;
        this.email = email;
    }

    @Override
    public Message toProtoMessage() {
        return getPaymentAccountPayloadBuilder()
                .setFasterPaymentsAccountPayload(protobuf.FasterPaymentsAccountPayload.newBuilder()
                        .setSortCode(sortCode)
                        .setAccountNr(accountNr)
                        .setEmail(email))
                .build();
    }

    public static FasterPaymentsAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        return new FasterPaymentsAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                proto.getFasterPaymentsAccountPayload().getSortCode(),
                proto.getFasterPaymentsAccountPayload().getAccountNr(),
                proto.getFasterPaymentsAccountPayload().getEmail(),
                proto.getMaxTradePeriod(),
                CollectionUtils.isEmpty(proto.getExcludeFromJsonDataMap()) ? null : new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - UK Sort code: " + sortCode + ", " + Res.getWithCol("payment.accountNr") + " " + accountNr;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return "UK Sort code: " + sortCode + "\n" +
                Res.getWithCol("payment.accountNr") + " " + accountNr;
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        return super.getAgeWitnessInputData(ArrayUtils.addAll(sortCode.getBytes(Charset.forName("UTF-8")),
                accountNr.getBytes(Charset.forName("UTF-8"))));
    }
}
