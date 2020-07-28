/*
 * This file is part of Bisq.
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

package bisq.core.account.sign;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.DateTolerantPayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProcessOncePersistableNetworkPayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.crypto.Hash;
import bisq.common.proto.ProtoUtil;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;

import java.time.Clock;
import java.time.Instant;

import java.util.concurrent.TimeUnit;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

// Supports signatures made from EC key (arbitrators) and signature created with DSA key.
@Slf4j
@Value
public class SignedWitness implements ProcessOncePersistableNetworkPayload, PersistableNetworkPayload,
        DateTolerantPayload, CapabilityRequiringPayload {

    public enum VerificationMethod {
        ARBITRATOR,
        TRADE;

        public static SignedWitness.VerificationMethod fromProto(protobuf.SignedWitness.VerificationMethod method) {
            return ProtoUtil.enumFromProto(SignedWitness.VerificationMethod.class, method.name());
        }

        public static protobuf.SignedWitness.VerificationMethod toProtoMessage(SignedWitness.VerificationMethod method) {
            return protobuf.SignedWitness.VerificationMethod.valueOf(method.name());
        }
    }

    private static final long TOLERANCE = TimeUnit.DAYS.toMillis(1);

    private final VerificationMethod verificationMethod;
    private final byte[] accountAgeWitnessHash;
    private final byte[] signature;
    private final byte[] signerPubKey;
    private final byte[] witnessOwnerPubKey;
    private final long date;
    private final long tradeAmount;

    transient private final byte[] hash;

    public SignedWitness(VerificationMethod verificationMethod,
                         byte[] accountAgeWitnessHash,
                         byte[] signature,
                         byte[] signerPubKey,
                         byte[] witnessOwnerPubKey,
                         long date,
                         long tradeAmount) {
        this.verificationMethod = verificationMethod;
        this.accountAgeWitnessHash = accountAgeWitnessHash.clone();
        this.signature = signature.clone();
        this.signerPubKey = signerPubKey.clone();
        this.witnessOwnerPubKey = witnessOwnerPubKey.clone();
        this.date = date;
        this.tradeAmount = tradeAmount;

        // The hash is only using the data which does not change in repeated trades between identical users (no date or amount).
        // We only want to store the first and oldest one and will ignore others. That will also help to protect privacy
        // so that the total number of trades is not revealed. We use putIfAbsent when we store the data so first
        // object will win. We consider one signed trade with one peer enough and do not consider repeated trades with
        // same peer to add more security as if that one would be colluding it would be not detected anyway. The total
        // number of signed trades with different peers is still available and can be considered more valuable data for
        // security.
        byte[] data = Utilities.concatenateByteArrays(accountAgeWitnessHash, signature);
        data = Utilities.concatenateByteArrays(data, signerPubKey);
        hash = Hash.getSha256Ripemd160hash(data);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTOBUF
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.PersistableNetworkPayload toProtoMessage() {
        final protobuf.SignedWitness.Builder builder = protobuf.SignedWitness.newBuilder()
                .setVerificationMethod(VerificationMethod.toProtoMessage(verificationMethod))
                .setAccountAgeWitnessHash(ByteString.copyFrom(accountAgeWitnessHash))
                .setSignature(ByteString.copyFrom(signature))
                .setSignerPubKey(ByteString.copyFrom(signerPubKey))
                .setWitnessOwnerPubKey(ByteString.copyFrom(witnessOwnerPubKey))
                .setDate(date)
                .setTradeAmount(tradeAmount);
        return protobuf.PersistableNetworkPayload.newBuilder().setSignedWitness(builder).build();
    }

    public protobuf.SignedWitness toProtoSignedWitness() {
        return toProtoMessage().getSignedWitness();
    }

    public static SignedWitness fromProto(protobuf.SignedWitness proto) {
        return new SignedWitness(
                SignedWitness.VerificationMethod.fromProto(proto.getVerificationMethod()),
                proto.getAccountAgeWitnessHash().toByteArray(),
                proto.getSignature().toByteArray(),
                proto.getSignerPubKey().toByteArray(),
                proto.getWitnessOwnerPubKey().toByteArray(),
                proto.getDate(),
                proto.getTradeAmount());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean isDateInTolerance(Clock clock) {
        // We don't allow older or newer than 1 day.
        // Preventing forward dating is also important to protect against a sophisticated attack
        return Math.abs(clock.millis() - date) <= TOLERANCE;
    }

    @Override
    public boolean verifyHashSize() {
        return hash.length == 20;
    }

    // Pre 1.0.1 version don't know the new message type and throw an error which leads to disconnecting the peer.
    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.SIGNED_ACCOUNT_AGE_WITNESS);
    }

    @Override
    public byte[] getHash() {
        return hash;
    }

    public boolean isSignedByArbitrator() {
        return verificationMethod == VerificationMethod.ARBITRATOR;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public P2PDataStorage.ByteArray getHashAsByteArray() {
        return new P2PDataStorage.ByteArray(hash);
    }

    @Override
    public String toString() {
        return "SignedWitness{" +
                "\n     verificationMethod=" + verificationMethod +
                ",\n     witnessHash=" + Utilities.bytesAsHexString(accountAgeWitnessHash) +
                ",\n     signature=" + Utilities.bytesAsHexString(signature) +
                ",\n     signerPubKey=" + Utilities.bytesAsHexString(signerPubKey) +
                ",\n     witnessOwnerPubKey=" + Utilities.bytesAsHexString(witnessOwnerPubKey) +
                ",\n     date=" + Instant.ofEpochMilli(date) +
                ",\n     tradeAmount=" + Coin.valueOf(tradeAmount).toFriendlyString() +
                ",\n     hash=" + Utilities.bytesAsHexString(hash) +
                "\n}";
    }
}
