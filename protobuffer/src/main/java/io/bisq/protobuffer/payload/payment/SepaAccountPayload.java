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
import io.bisq.common.locale.Country;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@ToString
@Getter
@Slf4j
public final class SepaAccountPayload extends CountryBasedPaymentAccountPayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    @Setter
    private String holderName;
    @Setter
    private String iban;
    @Setter
    private String bic;
    // Dont use a set here as we need a deterministic ordering, otherwise the contract hash does not match
    private final List<String> acceptedCountryCodes;

    public SepaAccountPayload(String paymentMethod, String id, long maxTradePeriod, List<Country> acceptedCountries) {
        super(paymentMethod, id, maxTradePeriod);
        Set<String> acceptedCountryCodesAsSet = acceptedCountries.stream()
                .map(e -> e.code).collect(Collectors.toSet());
        acceptedCountryCodes = new ArrayList<>(acceptedCountryCodesAsSet);
        acceptedCountryCodes.sort(String::compareTo);
    }

    public void addAcceptedCountry(String countryCode) {
        if (!acceptedCountryCodes.contains(countryCode))
            acceptedCountryCodes.add(countryCode);
    }

    public void removeAcceptedCountry(String countryCode) {
        if (acceptedCountryCodes.contains(countryCode))
            acceptedCountryCodes.remove(countryCode);
    }

    @Override
    public String getPaymentDetails(Locale locale) {
        return "SEPA - Holder name: " + holderName + ", IBAN: " + iban + ", BIC: " + bic + ", country code: " + getCountryCode();
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return null;
    }

    @Override
    public String getPaymentDetailsForTradePopup(Locale locale) {
        return "Holder name: " + holderName + "\n" +
                "IBAN: " + iban + "\n" +
                "BIC: " + bic + "\n" +
                "Country of bank: " + new Locale(locale.getLanguage(), countryCode).getDisplayCountry();
    }

    @Override
    public PB.PaymentAccountPayload toProto() {
        PB.SepaAccountPayload.Builder sepaAccountPayload =
                PB.SepaAccountPayload.newBuilder()
                        .setHolderName(holderName)
                        .setIban(iban)
                        .setBic(bic)
                        .addAllAcceptedCountryCodes(acceptedCountryCodes);
        PB.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayload =
                PB.CountryBasedPaymentAccountPayload.newBuilder()
                        .setCountryCode(countryCode)
                        .setSepaAccountPayload(sepaAccountPayload);
        PB.PaymentAccountPayload.Builder paymentAccountPayload =
                PB.PaymentAccountPayload.newBuilder()
                        .setId(id)
                        .setPaymentMethodId(paymentMethodId)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayload);

        return paymentAccountPayload.build();
    }
}
