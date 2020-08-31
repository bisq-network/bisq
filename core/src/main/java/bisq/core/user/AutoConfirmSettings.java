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

package bisq.core.user;

import bisq.common.proto.persistable.PersistablePayload;

import com.google.protobuf.Message;

import java.util.ArrayList;
import java.util.List;

public final class AutoConfirmSettings implements PersistablePayload {
    public final boolean enabled;
    public final int requiredConfirmations;
    public final long tradeLimit;
    public final List<String> serviceAddresses;
    public final String currencyCode;

    public AutoConfirmSettings(boolean enabled,
                               int requiredConfirmations,
                               long tradeLimit,
                               List<String> serviceAddresses,
                               String currencyCode) {
        this.enabled = enabled;
        this.requiredConfirmations = requiredConfirmations;
        this.tradeLimit = tradeLimit;
        this.serviceAddresses = serviceAddresses;
        this.currencyCode = currencyCode;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Message toProtoMessage() {
        return protobuf.AutoConfirmSettings.newBuilder()
                .setEnabled(enabled)
                .setRequiredConfirmations(requiredConfirmations)
                .setTradeLimit(tradeLimit)
                .addAllServiceAddresses(serviceAddresses)
                .setCurrencyCode(currencyCode)
                .build();
    }

    public static AutoConfirmSettings fromProto(protobuf.AutoConfirmSettings proto) {
        List<String> serviceAddresses = proto.getServiceAddressesList().isEmpty() ?
                new ArrayList<>() : new ArrayList<>(proto.getServiceAddressesList());
        return new AutoConfirmSettings(
                proto.getEnabled(),
                proto.getRequiredConfirmations(),
                proto.getTradeLimit(),
                serviceAddresses,
                proto.getCurrencyCode());
    }
}
