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

package io.bisq.core.payment.payload;

import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@ToString
@Setter
@Getter
@Slf4j
public abstract class CountryBasedPaymentAccountPayload extends PaymentAccountPayload {
    protected String countryCode = "";

    CountryBasedPaymentAccountPayload(String paymentMethodName, String id) {
        super(paymentMethodName, id);
    }

    protected CountryBasedPaymentAccountPayload(String paymentMethodName,
                                                String id,
                                                String countryCode,
                                                long maxTradePeriod,
                                                @Nullable Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethodName,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);

        this.countryCode = countryCode;
    }

    @Override
    protected PB.PaymentAccountPayload.Builder getPaymentAccountPayloadBuilder() {
        PB.CountryBasedPaymentAccountPayload.Builder builder = PB.CountryBasedPaymentAccountPayload.newBuilder()
                .setCountryCode(countryCode);
        return super.getPaymentAccountPayloadBuilder()
                .setCountryBasedPaymentAccountPayload(builder);
    }

    abstract public String getPaymentDetails();

    abstract public String getPaymentDetailsForTradePopup();

    @Override
    protected byte[] getAgeWitnessInputData(byte[] data) {
        return super.getAgeWitnessInputData(ArrayUtils.addAll(countryCode.getBytes(Charset.forName("UTF-8")), data));
    }
}
