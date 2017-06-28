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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@ToString
@Getter
@Slf4j
public final class SepaAccountPayload extends CountryBasedPaymentAccountPayload {
    @Setter
    private String holderName;
    @Setter
    private String iban;
    @Setter
    private String bic;
    @Setter
    private String email;
    // Dont use a set here as we need a deterministic ordering, otherwise the contract hash does not match
    private final List<String> acceptedCountryCodes;

    public SepaAccountPayload(String paymentMethod, String id, long maxTradePeriod, List<Country> acceptedCountries) {
        super(paymentMethod, id, maxTradePeriod);
        Set<String> acceptedCountryCodesAsSet = acceptedCountries.stream()
                .map(e -> e.code).collect(Collectors.toSet());
        acceptedCountryCodes = new ArrayList<>(acceptedCountryCodesAsSet);
        acceptedCountryCodes.sort(String::compareTo);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private SepaAccountPayload(String paymentMethodName,
                               String id,
                               long maxTradePeriod,
                               String countryCode,
                               String holderName,
                               String iban,
                               String bic,
                               String email,
                               List<String> acceptedCountryCodes) {
        super(paymentMethodName, id, maxTradePeriod, countryCode);
        this.holderName = holderName;
        this.iban = iban;
        this.bic = bic;
        this.email = email;
        this.acceptedCountryCodes = acceptedCountryCodes;
    }

    @Override
    public Message toProtoMessage() {
        PB.SepaAccountPayload.Builder builder =
                PB.SepaAccountPayload.newBuilder()
                        .setHolderName(holderName)
                        .setIban(iban)
                        .setBic(bic)
                        .setEmail(email)
                        .addAllAcceptedCountryCodes(acceptedCountryCodes);
        final PB.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayload = getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .setSepaAccountPayload(builder);
        return getPaymentAccountPayloadBuilder()
                .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayload)
                .build();
    }

    public static PaymentAccountPayload fromProto(PB.PaymentAccountPayload proto) {
        PB.CountryBasedPaymentAccountPayload countryBasedPaymentAccountPayload = proto.getCountryBasedPaymentAccountPayload();
        PB.SepaAccountPayload sepaAccountPayload = countryBasedPaymentAccountPayload.getSepaAccountPayload();
        return new SepaAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                proto.getMaxTradePeriod(),
                countryBasedPaymentAccountPayload.getCountryCode(),
                sepaAccountPayload.getHolderName(),
                sepaAccountPayload.getIban(),
                sepaAccountPayload.getBic(),
                sepaAccountPayload.getEmail(),
                new ArrayList<>(sepaAccountPayload.getAcceptedCountryCodesList()));
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
        return "SEPA - Holder name: " + holderName + ", IBAN: " + iban + ", BIC: " + bic + ", Email: " + email + ", country code: " + getCountryCode();
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return "Holder name: " + holderName + "\n" +
                "IBAN: " + iban + "\n" +
                "BIC: " + bic + "\n" +
                "Email: " + email + "\n" +
                "Country of bank: " + CountryUtil.getNameByCode(countryCode);
    }
}
