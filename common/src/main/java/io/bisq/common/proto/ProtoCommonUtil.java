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

package io.bisq.common.proto;

import com.google.protobuf.ByteString;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.generated.protobuffer.PB;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ProtoCommonUtil {

    public static Set<byte[]> getByteSet(List<ByteString> byteStringList) {
        return byteStringList.stream().map(ByteString::toByteArray).collect(Collectors.toSet());
    }

    public static String getCurrencyCode(PB.OfferPayload pbOffer) {
        String currencyCode;
        if (CurrencyUtil.isCryptoCurrency(pbOffer.getBaseCurrencyCode()))
            currencyCode = pbOffer.getBaseCurrencyCode();
        else
            currencyCode = pbOffer.getCounterCurrencyCode();
        return currencyCode;
    }

}
