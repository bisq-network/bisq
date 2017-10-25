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

package io.bisq.core.arbitration;

import com.google.protobuf.ByteString;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.util.Utilities;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@EqualsAndHashCode
@Slf4j
@Getter
public final class Arbitrator implements ProtectedStoragePayload {
    public static final long TTL = TimeUnit.DAYS.toMillis(10);

    private final NodeAddress nodeAddress;
    private final byte[] btcPubKey;
    private final String btcAddress;
    private final PubKeyRing pubKeyRing;
    private final List<String> languageCodes;
    private final long registrationDate;
    private final byte[] registrationPubKey;
    private final String registrationSignature;
    @Nullable
    private final String emailAddress;
    @Nullable
    private final String info;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;

    public Arbitrator(NodeAddress nodeAddress,
                      byte[] btcPubKey,
                      String btcAddress,
                      PubKeyRing pubKeyRing,
                      List<String> languageCodes,
                      long registrationDate,
                      byte[] registrationPubKey,
                      String registrationSignature,
                      @Nullable String emailAddress,
                      @Nullable String info,
                      @Nullable Map<String, String> extraDataMap) {
        this.nodeAddress = nodeAddress;
        this.btcPubKey = btcPubKey;
        this.btcAddress = btcAddress;
        this.pubKeyRing = pubKeyRing;
        this.languageCodes = languageCodes;
        this.registrationDate = registrationDate;
        this.registrationPubKey = registrationPubKey;
        this.registrationSignature = registrationSignature;
        this.emailAddress = emailAddress;
        this.info = info;
        this.extraDataMap = extraDataMap;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.StoragePayload toProtoMessage() {
        final PB.Arbitrator.Builder builder = PB.Arbitrator.newBuilder()
                .setNodeAddress(nodeAddress.toProtoMessage())
                .setBtcPubKey(ByteString.copyFrom(btcPubKey))
                .setBtcAddress(btcAddress)
                .setPubKeyRing(pubKeyRing.toProtoMessage())
                .addAllLanguageCodes(languageCodes)
                .setRegistrationDate(registrationDate)
                .setRegistrationPubKey(ByteString.copyFrom(registrationPubKey))
                .setRegistrationSignature(registrationSignature);
        Optional.ofNullable(emailAddress).ifPresent(builder::setEmailAddress);
        Optional.ofNullable(info).ifPresent(builder::setInfo);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return PB.StoragePayload.newBuilder().setArbitrator(builder).build();
    }

    public static Arbitrator fromProto(PB.Arbitrator proto) {
        return new Arbitrator(NodeAddress.fromProto(proto.getNodeAddress()),
                proto.getBtcPubKey().toByteArray(),
                proto.getBtcAddress(),
                PubKeyRing.fromProto(proto.getPubKeyRing()),
                proto.getLanguageCodesList().stream().collect(Collectors.toList()),
                proto.getRegistrationDate(),
                proto.getRegistrationPubKey().toByteArray(),
                proto.getRegistrationSignature(),
                ProtoUtil.stringOrNullFromProto(proto.getEmailAddress()),
                ProtoUtil.stringOrNullFromProto(proto.getInfo()),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return pubKeyRing.getSignaturePubKey();
    }


    @Override
    public String toString() {
        return "Arbitrator{" +
                "\n     nodeAddress=" + nodeAddress +
                ",\n     btcPubKey=" + Utilities.bytesAsHexString(btcPubKey) +
                ",\n     btcAddress='" + btcAddress + '\'' +
                ",\n     pubKeyRing=" + pubKeyRing +
                ",\n     languageCodes=" + languageCodes +
                ",\n     registrationDate=" + registrationDate +
                ",\n     registrationPubKey=" + Utilities.bytesAsHexString(registrationPubKey) +
                ",\n     registrationSignature='" + registrationSignature + '\'' +
                ",\n     emailAddress='" + emailAddress + '\'' +
                ",\n     info='" + info + '\'' +
                ",\n     extraDataMap=" + extraDataMap +
                "\n}";
    }
}
