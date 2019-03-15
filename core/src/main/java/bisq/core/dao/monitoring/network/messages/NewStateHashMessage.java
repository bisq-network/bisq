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

package bisq.core.dao.monitoring.network.messages;

import bisq.core.dao.monitoring.model.StateHash;

import bisq.network.p2p.storage.messages.BroadcastMessage;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public abstract class NewStateHashMessage<T extends StateHash> extends BroadcastMessage implements CapabilityRequiringPayload {
    protected final T stateHash;

    protected NewStateHashMessage(T stateHash, int messageVersion) {
        super(messageVersion);
        this.stateHash = stateHash;
    }

    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.DAO_STATE);
    }

    @Override
    public String toString() {
        return "NewStateHashMessage{" +
                "\n     stateHash=" + stateHash +
                "\n} " + super.toString();
    }
}
