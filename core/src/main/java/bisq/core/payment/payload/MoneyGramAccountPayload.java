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

import bisq.core.locale.BankUtil;
import bisq.core.locale.CountryUtil;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import org.springframework.util.CollectionUtils;

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
@Setter
@Getter
@Slf4j
public class MoneyGramAccountPayload extends PaymentAccountPayload {
    private String holderName;
    private String countryCode = "";
    private String state = ""; // is optional. we don't use @Nullable because it would makes UI code more complex.
    private String email;

    public MoneyGramAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private MoneyGramAccountPayload(String paymentMethodName,
                                    String id,
                                    String countryCode,
                                    String holderName,
                                    String state,
                                    String email,
                                    long maxTradePeriod,
                                    @Nullable Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethodName,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);
        this.holderName = holderName;
        this.countryCode = countryCode;
        this.state = state;
        this.email = email;
    }

    @Override
    public Message toProtoMessage() {
        PB.MoneyGramAccountPayload.Builder builder =
                PB.MoneyGramAccountPayload.newBuilder()
                        .setHolderName(holderName)
                        .setCountryCode(countryCode)
                        .setState(state)
                        .setEmail(email);

        return getPaymentAccountPayloadBuilder()
                .setMoneyGramAccountPayload(builder)
                .build();
    }

    public static PaymentAccountPayload fromProto(PB.PaymentAccountPayload proto) {
        PB.MoneyGramAccountPayload moneyGramAccountPayload = proto.getMoneyGramAccountPayload();
        return new MoneyGramAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                moneyGramAccountPayload.getCountryCode(),
                moneyGramAccountPayload.getHolderName(),
                moneyGramAccountPayload.getState(),
                moneyGramAccountPayload.getEmail(),
                proto.getMaxTradePeriod(),
                CollectionUtils.isEmpty(proto.getExcludeFromJsonDataMap()) ? null : new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return "MoneyGram - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        String state = BankUtil.isStateRequired(countryCode) ? ("State: " + this.state + "\n") : "";
        return "Full name: " + holderName + "\n" +
                state +
                "Country: " + CountryUtil.getNameByCode(countryCode) + "\n" +
                "Email: " + email;
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        String all = this.countryCode +
                this.state +
                this.holderName +
                this.email;
        return super.getAgeWitnessInputData(all.getBytes(Charset.forName("UTF-8")));
    }
}
