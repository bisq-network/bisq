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

package bisq.network.p2p.peers.peerexchange;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.SupportedCapabilitiesListener;

import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Getter
@EqualsAndHashCode(exclude = {"date"}) // failedConnectionAttempts is transient and therefore excluded anyway
@Slf4j
public final class Peer implements NetworkPayload, PersistablePayload, SupportedCapabilitiesListener {
    private static final int MAX_FAILED_CONNECTION_ATTEMPTS = 5;

    private final NodeAddress nodeAddress;
    private final long date;
    // Added in v. 0.7.1
    @Setter
    private List<Integer> supportedCapabilities = new ArrayList<>();

    @Setter
    transient private int failedConnectionAttempts = 0;

    public Peer(NodeAddress nodeAddress, @Nullable List<Integer> supportedCapabilities) {
        this(nodeAddress, new Date().getTime(),
                supportedCapabilities == null ? new ArrayList<>() : supportedCapabilities);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Peer(NodeAddress nodeAddress, long date, List<Integer> supportedCapabilities) {
        this.nodeAddress = nodeAddress;
        this.date = date;
        this.supportedCapabilities = supportedCapabilities;

        if (supportedCapabilities.isEmpty())
            log.warn("SupportedCapabilities is empty. nodeAddress={}", nodeAddress);
    }

    @Override
    public PB.Peer toProtoMessage() {
        return PB.Peer.newBuilder()
                .setNodeAddress(nodeAddress.toProtoMessage())
                .setDate(date)
                .addAllSupportedCapabilities(supportedCapabilities)
                .build();
    }

    public static Peer fromProto(PB.Peer proto) {
        return new Peer(NodeAddress.fromProto(proto.getNodeAddress()),
                proto.getDate(),
                proto.getSupportedCapabilitiesList().isEmpty() ?
                        new ArrayList<>() : new ArrayList<>(proto.getSupportedCapabilitiesList()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void increaseFailedConnectionAttempts() {
        this.failedConnectionAttempts++;
    }

    public boolean tooManyFailedConnectionAttempts() {
        return failedConnectionAttempts >= MAX_FAILED_CONNECTION_ATTEMPTS;
    }

    public Date getDate() {
        return new Date(date);
    }

    @Override
    public void onChanged(List<Integer> supportedCapabilities) {
        if (supportedCapabilities != null && !supportedCapabilities.isEmpty())
            this.supportedCapabilities = supportedCapabilities;
    }


    @Override
    public String toString() {
        return "Peer{" +
                "\n     nodeAddress=" + nodeAddress +
                ",\n     supportedCapabilities=" + supportedCapabilities +
                ",\n     failedConnectionAttempts=" + failedConnectionAttempts +
                ",\n     date=" + date +
                "\n}";
    }
}
