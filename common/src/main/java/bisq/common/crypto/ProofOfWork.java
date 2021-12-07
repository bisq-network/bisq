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
    @Getter
    private final double difficulty;
    @Getter
    private final long duration;
    @Getter
    private final byte[] solution;
    @Getter
    private final int version;

    public ProofOfWork(byte[] payload,
                       long counter,
                       byte[] challenge,
                       double difficulty,
                       long duration,
                       byte[] solution,
                       int version) {
        this.payload = payload;
        this.counter = counter;
        this.challenge = challenge;
        this.difficulty = difficulty;
        this.duration = duration;
        this.solution = solution;
        this.version = version;
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
                .setDifficulty(difficulty)
                .setDuration(duration)
                .setSolution(ByteString.copyFrom(solution))
                .setVersion(version)
                .build();
    }

    public static ProofOfWork fromProto(protobuf.ProofOfWork proto) {
        return new ProofOfWork(
                proto.getPayload().toByteArray(),
                proto.getCounter(),
                proto.getChallenge().toByteArray(),
                proto.getDifficulty(),
                proto.getDuration(),
                proto.getSolution().toByteArray(),
                proto.getVersion()
        );
    }


    @Override
    public String toString() {
        return "ProofOfWork{" +
                ",\r\n     counter=" + counter +
                ",\r\n     difficulty=" + difficulty +
                ",\r\n     duration=" + duration +
                ",\r\n     version=" + version +
                "\r\n}";
    }
}
