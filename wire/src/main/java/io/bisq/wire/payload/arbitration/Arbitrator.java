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

package io.bisq.wire.payload.arbitration;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.wire.payload.StoragePayload;
import io.bisq.wire.payload.crypto.PubKeyRing;
import io.bisq.wire.payload.p2p.NodeAddress;
import io.bisq.wire.proto.Messages;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@EqualsAndHashCode
@Slf4j
@ToString
@Getter
public final class Arbitrator implements StoragePayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public static final long TTL = TimeUnit.DAYS.toMillis(10);

    // Payload
    private final byte[] btcPubKey;
    private final PubKeyRing pubKeyRing;
    private final NodeAddress arbitratorNodeAddress;
    private final List<String> languageCodes;
    private final String btcAddress;
    private final long registrationDate;
    private final String registrationSignature;
    private final byte[] registrationPubKey;
    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility 
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new 
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;

    // Called from domain and PB
    public Arbitrator(NodeAddress arbitratorNodeAddress,
                      byte[] btcPubKey,
                      String btcAddress,
                      PubKeyRing pubKeyRing,
                      List<String> languageCodes,
                      Date registrationDate,
                      byte[] registrationPubKey,
                      String registrationSignature,
                      @Nullable Map<String, String> extraDataMap) {
        this.arbitratorNodeAddress = arbitratorNodeAddress;
        this.btcPubKey = btcPubKey;
        this.btcAddress = btcAddress;
        this.pubKeyRing = pubKeyRing;
        this.languageCodes = languageCodes;
        this.registrationDate = registrationDate.getTime();
        this.registrationPubKey = registrationPubKey;
        this.registrationSignature = registrationSignature;
        this.extraDataMap = Optional.ofNullable(extraDataMap).orElse(Maps.newHashMap());
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return pubKeyRing.getSignaturePubKey();
    }

    @Override
    public Messages.StoragePayload toProtoBuf() {
        final Messages.Arbitrator.Builder builder = Messages.Arbitrator.newBuilder()
                .setTTL(TTL)
                .setBtcPubKey(ByteString.copyFrom(btcPubKey))
                .setPubKeyRing((Messages.PubKeyRing) pubKeyRing.toProtoBuf())
                .setArbitratorNodeAddress(arbitratorNodeAddress.toProtoBuf())
                .addAllLanguageCodes(languageCodes)
                .setBtcAddress(btcAddress)
                .setRegistrationDate(registrationDate)
                .setRegistrationSignature(registrationSignature)
                .setRegistrationPubKey(ByteString.copyFrom(registrationPubKey));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraDataMap);
        return Messages.StoragePayload.newBuilder().setArbitrator(builder).build();
    }

}
