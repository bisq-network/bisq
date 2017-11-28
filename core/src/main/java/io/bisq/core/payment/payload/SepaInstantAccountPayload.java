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

import com.google.protobuf.Message;
import io.bisq.common.locale.Country;
import io.bisq.common.locale.CountryUtil;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@ToString
@Getter
@Slf4j
public final class SepaInstantAccountPayload extends CountryBasedPaymentAccountPayload {
    @Setter
    private String holderName = "";
    @Setter
    private String iban = "";
    @Setter
    private String bic = "";

    // Dont use a set here as we need a deterministic ordering, otherwise the contract hash does not match
    private final List<String> acceptedCountryCodes;

    public SepaInstantAccountPayload(String paymentMethod, String id, List<Country> acceptedCountries) {
        super(paymentMethod, id);
        Set<String> acceptedCountryCodesAsSet = acceptedCountries.stream()
                .map(e -> e.code).collect(Collectors.toSet());
        acceptedCountryCodes = new ArrayList<>(acceptedCountryCodesAsSet);
        acceptedCountryCodes.sort(String::compareTo);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private SepaInstantAccountPayload(String paymentMethodName,
                                      String id,
                                      String countryCode,
                                      String holderName,
                                      String iban,
                                      String bic,
                                      List<String> acceptedCountryCodes,
                                      long maxTradePeriod,
                                      @Nullable Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethodName,
                id,
                countryCode,
                maxTradePeriod,
                excludeFromJsonDataMap);

        this.holderName = holderName;
        this.iban = iban;
        this.bic = bic;
        this.acceptedCountryCodes = acceptedCountryCodes;
    }

    @Override
    public Message toProtoMessage() {
        PB.SepaInstantAccountPayload.Builder builder =
                PB.SepaInstantAccountPayload.newBuilder()
                        .setHolderName(holderName)
                        .setIban(iban)
                        .setBic(bic)
                        .addAllAcceptedCountryCodes(acceptedCountryCodes);
        final PB.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayload = getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .setSepaInstantAccountPayload(builder);
        return getPaymentAccountPayloadBuilder()
                .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayload)
                .build();
    }

    public static PaymentAccountPayload fromProto(PB.PaymentAccountPayload proto) {
        PB.CountryBasedPaymentAccountPayload countryBasedPaymentAccountPayload = proto.getCountryBasedPaymentAccountPayload();
        PB.SepaInstantAccountPayload sepaInstantAccountPayloadPB = countryBasedPaymentAccountPayload.getSepaInstantAccountPayload();
        return new SepaInstantAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                countryBasedPaymentAccountPayload.getCountryCode(),
                sepaInstantAccountPayloadPB.getHolderName(),
                sepaInstantAccountPayloadPB.getIban(),
                sepaInstantAccountPayloadPB.getBic(),
                new ArrayList<>(sepaInstantAccountPayloadPB.getAcceptedCountryCodesList()),
                proto.getMaxTradePeriod(),
                CollectionUtils.isEmpty(proto.getExcludeFromJsonDataMap()) ? null : new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addAcceptedCountry(String countryCode) {
        if (!acceptedCountryCodes.contains(countryCode))
            acceptedCountryCodes.add(countryCode);
    }

    public void removeAcceptedCountry(String countryCode) {
        if (acceptedCountryCodes.contains(countryCode))
            acceptedCountryCodes.remove(countryCode);
    }

    @Override
    public String getPaymentDetails() {
        return "SEPA Instant - Holder name: " + holderName + ", IBAN: " + iban + ", BIC: " + bic + ", Country code: " + getCountryCode();
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return "Holder name: " + holderName + "\n" +
                "IBAN: " + iban + "\n" +
                "BIC: " + bic + "\n" +
                "Country of bank: " + CountryUtil.getNameByCode(countryCode);
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        // We don't add holderName because we don't want to break age validation if the user recreates an account with
        // slight changes in holder name (e.g. add or remove middle name)
        return super.getAgeWitnessInputData(ArrayUtils.addAll(iban.getBytes(Charset.forName("UTF-8")), bic.getBytes(Charset.forName("UTF-8"))));
    }
}
