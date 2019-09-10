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

import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.Res;

import com.google.protobuf.Message;

import org.springframework.util.CollectionUtils;

import java.nio.charset.Charset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
public final class HalCashAccountPayload extends CountryBasedPaymentAccountPayload {
    // Dont use a set here as we need a deterministic ordering, otherwise the contract hash does not match
    private final List<String> acceptedCountryCodes;
    private String mobileNr = "";

    public HalCashAccountPayload(String paymentMethod, String id, List<Country> acceptedCountries) {
        super(paymentMethod, id);
        Set<String> acceptedCountryCodesAsSet = acceptedCountries.stream().map(e -> e.code).collect(Collectors.toSet());
        acceptedCountryCodes = new ArrayList<>(acceptedCountryCodesAsSet);
        acceptedCountryCodes.sort(String::compareTo);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private HalCashAccountPayload(String paymentMethod,
                                  String id,
                                  String countryCode,
                                  String mobileNr,
                                  List<String> acceptedCountryCodes,
                                  long maxTradePeriod,
                                  @Nullable Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethod, id, countryCode, maxTradePeriod, excludeFromJsonDataMap);
        this.mobileNr = mobileNr;
        this.acceptedCountryCodes = acceptedCountryCodes;
    }

    public static HalCashAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        protobuf.CountryBasedPaymentAccountPayload countryBasedPaymentAccountPayload = proto.getCountryBasedPaymentAccountPayload();
        protobuf.HalCashAccountPayload halCashAccountPayloadPB = countryBasedPaymentAccountPayload.getHalCashAccountPayload();
        return new HalCashAccountPayload(proto.getPaymentMethodId(), proto.getId(), countryBasedPaymentAccountPayload.getCountryCode(), proto.getHalCashAccountPayload().getMobileNr(), new ArrayList<>(halCashAccountPayloadPB.getAcceptedCountryCodesList()), proto.getMaxTradePeriod(), CollectionUtils.isEmpty(proto.getExcludeFromJsonDataMap()) ? null : new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }

    @Override
    public Message toProtoMessage() {
        return getPaymentAccountPayloadBuilder().setHalCashAccountPayload(protobuf.HalCashAccountPayload.newBuilder().setMobileNr(mobileNr).addAllAcceptedCountryCodes(acceptedCountryCodes)).build();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addAcceptedCountry(String countryCode) {
        if (!acceptedCountryCodes.contains(countryCode)) acceptedCountryCodes.add(countryCode);
    }

    public void removeAcceptedCountry(String countryCode) {
        if (acceptedCountryCodes.contains(countryCode)) acceptedCountryCodes.remove(countryCode);
    }

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " + Res.getWithCol("payment.mobile") + " " + mobileNr + ", " + Res.getWithCol("payment.bank.country") + " " + getCountryCode();
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return Res.getWithCol("payment.mobile") + " " + mobileNr + "\n" + Res.getWithCol("payment.bank.country") + " " + CountryUtil.getNameByCode(countryCode);
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        return super.getAgeWitnessInputData(mobileNr.getBytes(Charset.forName("UTF-8")));
    }
}
