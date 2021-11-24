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

package bisq.common.crypto;

import bisq.common.proto.network.NetworkPayload;

import com.google.protobuf.ByteString;

import java.math.BigInteger;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
public final class ProofOfWork implements NetworkPayload {
    @Getter
    private final byte[] payload;
    @Getter
    private final long counter;
    @Getter
    private final byte[] challenge;
    // We want to support BigInteger value for difficulty as well so we store it as byte array
    private final byte[] difficulty;
    @Getter
    private final long duration;
    @Getter
    private final int version;

    public ProofOfWork(byte[] payload,
                       long counter,
                       byte[] challenge,
                       int difficulty,
                       long duration,
                       int version) {
        this(payload,
                counter,
                challenge,
                BigInteger.valueOf(difficulty).toByteArray(),
                duration,
                version);
    }

    public ProofOfWork(byte[] payload,
                       long counter,
                       byte[] challenge,
                       BigInteger difficulty,
                       long duration,
                       int version) {
        this(payload,
                counter,
                challenge,
                difficulty.toByteArray(),
                duration,
                version);
    }

    private ProofOfWork(byte[] payload,
                        long counter,
                        byte[] challenge,
                        byte[] difficulty,
                        long duration,
                        int version) {
        this.payload = payload;
        this.counter = counter;
        this.challenge = challenge;
        this.difficulty = difficulty;
        this.duration = duration;
        this.version = version;
    }

    public int getNumLeadingZeros() {
        return new BigInteger(difficulty).intValue();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.ProofOfWork toProtoMessage() {
        return protobuf.ProofOfWork.newBuilder()
                .setPayload(ByteString.copyFrom(payload))
                .setCounter(counter)
                .setChallenge(ByteString.copyFrom(challenge))
                .setDifficulty(ByteString.copyFrom(difficulty))
                .setDuration(duration)
                .setVersion(version)
                .build();
    }

    public static ProofOfWork fromProto(protobuf.ProofOfWork proto) {
        return new ProofOfWork(
                proto.getPayload().toByteArray(),
                proto.getCounter(),
                proto.getChallenge().toByteArray(),
                proto.getDifficulty().toByteArray(),
                proto.getDuration(),
                proto.getVersion()
        );
    }


    @Override
    public String toString() {
        return "ProofOfWork{" +
                ",\r\n     counter=" + counter +
                ",\r\n     numLeadingZeros=" + getNumLeadingZeros() +
                ",\r\n     duration=" + duration +
                ",\r\n     version=" + version +
                "\r\n}";
    }
}
