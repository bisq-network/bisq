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

package bisq.core.notifications.alerts.price;

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import lombok.Value;

@Value
public class PriceAlertFilter implements PersistablePayload {
    String currencyCode;
    long high;
    long low;

    public PriceAlertFilter(String currencyCode, long high, long low) {
        this.currencyCode = currencyCode;
        this.high = high;
        this.low = low;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.PriceAlertFilter toProtoMessage() {
        return PB.PriceAlertFilter.newBuilder()
                .setCurrencyCode(currencyCode)
                .setHigh(high)
                .setLow(low).build();
    }

    public static PriceAlertFilter fromProto(PB.PriceAlertFilter proto) {
        return new PriceAlertFilter(proto.getCurrencyCode(), proto.getHigh(), proto.getLow());
    }
}
