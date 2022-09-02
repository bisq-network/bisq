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

package bisq.core.trade.statistics;

import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProcessOncePersistableNetworkPayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.util.Utilities;

import protobuf.TradeStatistics3;

import com.google.protobuf.ByteString;

import com.google.common.annotations.VisibleForTesting;

import java.nio.ByteBuffer;

import java.util.Arrays;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * Links to a TradeStatistics3 instance by joining on its hash field,
 * to show if API was used by one or both sides of a trade.
 * <p>
 * The serialized protobuf is a 21 byte array.
 * Bytes 0-19 are the TradeStatistics3 hash byte[20] (~ db join key).
 * Byte 20 determines whether the API was used to make the trade's offer, take the
 * offer, or both.
 *
 * Serialized protobuf size is 25 bytes.
 */
@Getter
@Slf4j
public final class ApiTradeStatistics implements ProcessOncePersistableNetworkPayload,
        PersistableNetworkPayload, CapabilityRequiringPayload {

    @VisibleForTesting
    static final int RIPEMD160_HASH_LEN = 20;

    // The proto is a one field byte array that might grow in size
    // to hold more encoded info, and remain backward compatible.
    @VisibleForTesting
    static final int SERIALIZED_BYTE_ARRAY_LEN = 21;


    // 0x00 means API was used to make the trade's offer.
    private static final byte API_IS_MAKER = (byte) 0x00;
    // 0x01 means API was used to take the trade's offer.
    private static final byte API_IS_TAKER = (byte) 0x01;
    // 0x02 means API was used to create and take the offer.
    private static final byte API_IS_MAKER_AND_TAKER = (byte) 0x02;

    private final byte[] tradeStatistics3Hash;  // Joins on TradeStatistics3.hash
    private final boolean isMakerApiUser;
    private final boolean isTakerApiUser;

    // Is set while joining stores on tradeStatistics3.hash <--> this.tradeStatistics3Hash.
    @Setter
    @Nullable
    private transient bisq.core.trade.statistics.TradeStatistics3 tradeStatistics3;

    public ApiTradeStatistics(byte[] tradeStatistics3Hash,
                              boolean isMakerApiUser,
                              boolean isTakerApiUser) {
        this.tradeStatistics3Hash = tradeStatistics3Hash;
        this.isMakerApiUser = isMakerApiUser;
        this.isTakerApiUser = isTakerApiUser;
    }

    public ApiTradeStatistics(byte[] bytes) {
        var instance = decode(bytes);
        this.tradeStatistics3Hash = instance.tradeStatistics3Hash;
        this.isMakerApiUser = instance.isMakerApiUser;
        this.isTakerApiUser = instance.isTakerApiUser;
    }

    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.API_TRADE_STATISTICS);
    }

    @Override
    public byte[] getHash() {
        return this.tradeStatistics3Hash;
    }

    @Override
    public boolean verifyHashSize() {
        checkNotNull(tradeStatistics3Hash, "tradeStatistics3Hash must not be null");
        return tradeStatistics3Hash.length == RIPEMD160_HASH_LEN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiTradeStatistics)) return false;

        ApiTradeStatistics that = (ApiTradeStatistics) o;

        if (isMakerApiUser != that.isMakerApiUser) return false;
        if (isTakerApiUser != that.isTakerApiUser) return false;
        return Arrays.equals(tradeStatistics3Hash, that.tradeStatistics3Hash);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(tradeStatistics3Hash);
        result = 31 * result + (isMakerApiUser ? 1 : 0);
        result = 31 * result + (isTakerApiUser ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ApiTradeStatistics{" +
                "tradeStatistics3Hash=" + Utilities.bytesAsHexString(tradeStatistics3Hash) +
                ", isApiMaker=" + isMakerApiUser +
                ", isApiTaker=" + isTakerApiUser +
                ", tradeStatistics3=" + tradeStatistics3 +
                '}';
    }

    public static ApiTradeStatistics from(TradeStatistics3 tradeStatisticsProto,
                                          boolean isApiMaker,
                                          boolean isApiTaker) {
        byte[] tradeStatistics3Hash = tradeStatisticsProto.getHash().toByteArray();
        if (tradeStatistics3Hash.length == 0) {
            log.warn("Fake tradeStatistics3Hash! Fake tradeStatistics3Hash! Fake tradeStatistics3Hash!");
            tradeStatistics3Hash = new byte[RIPEMD160_HASH_LEN];
        }
        return new ApiTradeStatistics(tradeStatistics3Hash, isApiMaker, isApiTaker);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public protobuf.ApiTradeStatistics toProtoApiTradeStatistics() {
        return getBuilder().build();
    }

    @Override
    public protobuf.PersistableNetworkPayload toProtoMessage() {
        return protobuf.PersistableNetworkPayload.newBuilder().setApiTradeStatistics(getBuilder()).build();
    }

    @VisibleForTesting
    public protobuf.ApiTradeStatistics.Builder getBuilder() {
        var byteString = ByteString.copyFrom(encode(tradeStatistics3Hash, isMakerApiUser, isTakerApiUser));
        protobuf.ApiTradeStatistics.Builder builder = protobuf.ApiTradeStatistics.newBuilder()
                .setBytes(byteString);
        return builder;
    }

    public static ApiTradeStatistics fromProto(protobuf.ApiTradeStatistics proto) {
        return new ApiTradeStatistics(proto.getBytes().toByteArray());
    }

    private static byte[] encode(byte[] tradeStatistics3Hash,
                                 boolean isApiMaker,
                                 boolean isApiTaker) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(SERIALIZED_BYTE_ARRAY_LEN);
        byteBuffer.put(tradeStatistics3Hash);                          // Bytes: 0->19
        byteBuffer.put(encodeApiRoleByte(isApiMaker, isApiTaker));     // Byte: 20
        return byteBuffer.array();
    }

    private static ApiTradeStatistics decode(byte[] bytes) {
        if (bytes.length != SERIALIZED_BYTE_ARRAY_LEN) {
            throw new IllegalStateException(
                    format("Invalid byte[] length (%d), is not a serialized ApiTradeStatistics byte[%d] array.",
                            bytes.length,
                            SERIALIZED_BYTE_ARRAY_LEN));
        }
        // Bytes: 0->19
        byte[] tradeStatistics3Hash = Arrays.copyOf(bytes, SERIALIZED_BYTE_ARRAY_LEN - 1);
        // Byte: 20
        byte apiRoleByte = bytes[SERIALIZED_BYTE_ARRAY_LEN - 1];
        validateApiRoleByte(apiRoleByte);
        if (isMakerApiUser(apiRoleByte)) {
            return new ApiTradeStatistics(tradeStatistics3Hash, true, false);
        } else if (isTakerApiUser(apiRoleByte)) {
            return new ApiTradeStatistics(tradeStatistics3Hash, false, true);
        } else {
            return new ApiTradeStatistics(tradeStatistics3Hash, true, true);
        }
    }

    private static byte encodeApiRoleByte(boolean isApiMaker, boolean isApiTaker) {
        if (!isApiMaker && !isApiTaker) {
            throw new IllegalStateException("API was not used for the trade.");
        }
        if (isApiMaker && !isApiTaker) {
            return API_IS_MAKER;
        } else if (!isApiMaker) {
            return API_IS_TAKER;
        } else {
            return API_IS_MAKER_AND_TAKER;
        }
    }

    private static void validateApiRoleByte(byte b) {
        if (b != API_IS_MAKER && b != API_IS_TAKER && b != API_IS_MAKER_AND_TAKER) {
            throw new IllegalStateException("API was not used for the trade.");
        }
    }

    private static boolean isMakerApiUser(byte b) {
        return b == API_IS_MAKER;
    }

    private static boolean isTakerApiUser(byte b) {
        return b == API_IS_TAKER;
    }
}
