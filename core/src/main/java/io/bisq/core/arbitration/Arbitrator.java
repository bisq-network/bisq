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

package io.bisq.core.arbitration;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.core.proto.ProtoUtil;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.storage.payload.StoragePayload;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.security.PublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private final NodeAddress nodeAddress;
    private final List<String> languageCodes;
    private final String btcAddress;
    private final long registrationDate;
    private final String registrationSignature;
    private final byte[] registrationPubKey;
    @Nullable
    private final String emailAddress;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;

    // Called from domain and PB
    public Arbitrator(NodeAddress nodeAddress,
                      byte[] btcPubKey,
                      String btcAddress,
                      PubKeyRing pubKeyRing,
                      List<String> languageCodes,
                      Date registrationDate,
                      byte[] registrationPubKey,
                      String registrationSignature,
                      @Nullable String emailAddress,
                      @Nullable Map<String, String> extraDataMap) {
        this.nodeAddress = nodeAddress;
        this.btcPubKey = btcPubKey;
        this.btcAddress = btcAddress;
        this.pubKeyRing = pubKeyRing;
        this.languageCodes = languageCodes;
        this.emailAddress = emailAddress;
        this.registrationDate = registrationDate.getTime();
        this.registrationPubKey = registrationPubKey;
        this.registrationSignature = registrationSignature;
        this.extraDataMap = extraDataMap;
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
    public PB.StoragePayload toProto() {
        final PB.Arbitrator.Builder builder = PB.Arbitrator.newBuilder()
                .setBtcPubKey(ByteString.copyFrom(btcPubKey))
                .setPubKeyRing(pubKeyRing.toProto())
                .setNodeAddress(nodeAddress.toProto())
                .addAllLanguageCodes(languageCodes)
                .setBtcAddress(btcAddress)
                .setRegistrationDate(registrationDate)
                .setRegistrationSignature(ByteString.copyFrom(registrationSignature.getBytes())) // string does not conform to UTF-8 !
                .setRegistrationPubKey(ByteString.copyFrom(registrationPubKey));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraDataMap);
        Optional.ofNullable(emailAddress).ifPresent(builder::setEmailAddress);
        return PB.StoragePayload.newBuilder().setArbitrator(builder).build();
    }

    public static Arbitrator fromProto(PB.Arbitrator arbitrator) {
        List<String> strings = arbitrator.getLanguageCodesList().stream().collect(Collectors.toList());
        Date date = new Date(arbitrator.getRegistrationDate());
        String emailAddress = arbitrator.getEmailAddress().isEmpty() ? null : arbitrator.getEmailAddress();
        return new Arbitrator(NodeAddress.fromProto(arbitrator.getNodeAddress()),
                arbitrator.getBtcPubKey().toByteArray(),
                arbitrator.getBtcAddress(),
                ProtoUtil.getPubKeyRing(arbitrator.getPubKeyRing()),
                strings,
                date,
                arbitrator.getRegistrationPubKey().toByteArray(),
                arbitrator.getRegistrationSignature().toString(Charset.forName("UTF-8")), // convert back to String
                emailAddress,
                CollectionUtils.isEmpty(arbitrator.getExtraDataMapMap()) ?
                        null : arbitrator.getExtraDataMapMap());
    }

}
