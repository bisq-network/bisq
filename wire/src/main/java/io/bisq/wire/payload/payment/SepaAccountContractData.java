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
import io.bisq.common.locale.Country;
import io.bisq.common.wire.proto.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

// TODO refactor with BankAccountContractData
public final class SepaAccountContractData extends CountryBasedPaymentAccountContractData {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private static final Logger log = LoggerFactory.getLogger(SepaAccountContractData.class);

    private String holderName;
    private String iban;
    private String bic;
    // Dont use a set here as we need a deterministic ordering, otherwise the contract hash does not match
    private final List<String> acceptedCountryCodes;

    public SepaAccountContractData(String paymentMethod, String id, long maxTradePeriod, List<Country> acceptedCountries) {
        super(paymentMethod, id, maxTradePeriod);
        Set<String> acceptedCountryCodesAsSet = acceptedCountries.stream()
                .map(e -> e.code).collect(Collectors.toSet());
        acceptedCountryCodes = new ArrayList<>(acceptedCountryCodesAsSet);
        acceptedCountryCodes.sort(String::compareTo);
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getIban() {
        return iban;
    }

    public void setBic(String bic) {
        this.bic = bic;
    }

    public String getBic() {
        return bic;
    }

    public void addAcceptedCountry(String countryCode) {
        if (!acceptedCountryCodes.contains(countryCode))
            acceptedCountryCodes.add(countryCode);
    }

    public void removeAcceptedCountry(String countryCode) {
        if (acceptedCountryCodes.contains(countryCode))
            acceptedCountryCodes.remove(countryCode);
    }

    public List<String> getAcceptedCountryCodes() {
        return acceptedCountryCodes;
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
    public Messages.PaymentAccountContractData toProtoBuf() {
        Messages.SepaAccountContractData.Builder sepaAccountContractData =
                Messages.SepaAccountContractData.newBuilder()
                        .setHolderName(holderName)
                        .setIban(iban)
                        .setBic(bic)
                        .addAllAcceptedCountryCodes(acceptedCountryCodes);
        Messages.CountryBasedPaymentAccountContractData.Builder countryBasedPaymentAccountContractData =
                Messages.CountryBasedPaymentAccountContractData.newBuilder()
                        .setCountryCode(countryCode)
                        .setSepaAccountContractData(sepaAccountContractData);
        Messages.PaymentAccountContractData.Builder paymentAccountContractData =
                Messages.PaymentAccountContractData.newBuilder()
                        .setId(id)
                        .setPaymentMethodId(paymentMethodId)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setCountryBasedPaymentAccountContractData(countryBasedPaymentAccountContractData);

        return paymentAccountContractData.build();
    }
}
