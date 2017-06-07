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

package io.bisq.core.offer.messages;

import io.bisq.common.app.Version;
import io.bisq.network.p2p.DirectMessage;

import javax.annotation.concurrent.Immutable;

@Immutable
public abstract class OfferMessage implements DirectMessage {
    private final int messageVersion = Version.getP2PMessageVersion();
    public final String offerId;

    OfferMessage(String offerId) {
        this.offerId = offerId;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String toString() {
        return "OfferMessage{" +
                "messageVersion=" + messageVersion +
                ", offerId='" + offerId + '\'' +
                '}';
    }
}
