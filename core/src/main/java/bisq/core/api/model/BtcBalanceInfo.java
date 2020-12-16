package bisq.core.api.model;

import bisq.common.Payload;

import com.google.common.annotations.VisibleForTesting;

import lombok.Getter;

@Getter
public class BtcBalanceInfo implements Payload {

    public static final BtcBalanceInfo EMPTY = new BtcBalanceInfo(-1,
            -1,
            -1,
            -1);

    // All balances are in BTC satoshis.
    private final long availableBalance;
    private final long reservedBalance;
    private final long totalAvailableBalance; // available + reserved
    private final long lockedBalance;

    public BtcBalanceInfo(long availableBalance,
                          long reservedBalance,
                          long totalAvailableBalance,
                          long lockedBalance) {
        this.availableBalance = availableBalance;
        this.reservedBalance = reservedBalance;
        this.totalAvailableBalance = totalAvailableBalance;
        this.lockedBalance = lockedBalance;
    }

    @VisibleForTesting
    public static BtcBalanceInfo valueOf(long availableBalance,
                                         long reservedBalance,
                                         long totalAvailableBalance,
                                         long lockedBalance) {
        // Convenience for creating a model instance instead of a proto.
        return new BtcBalanceInfo(availableBalance,
                reservedBalance,
                totalAvailableBalance,
                lockedBalance);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.BtcBalanceInfo toProtoMessage() {
        return bisq.proto.grpc.BtcBalanceInfo.newBuilder()
                .setAvailableBalance(availableBalance)
                .setReservedBalance(reservedBalance)
                .setTotalAvailableBalance(totalAvailableBalance)
                .setLockedBalance(lockedBalance)
                .build();
    }

    public static BtcBalanceInfo fromProto(bisq.proto.grpc.BtcBalanceInfo proto) {
        return new BtcBalanceInfo(proto.getAvailableBalance(),
                proto.getReservedBalance(),
                proto.getTotalAvailableBalance(),
                proto.getLockedBalance());
    }

    @Override
    public String toString() {
        return "BtcBalanceInfo{" +
                "availableBalance=" + availableBalance +
                ", reservedBalance=" + reservedBalance +
                ", totalAvailableBalance=" + totalAvailableBalance +
                ", lockedBalance=" + lockedBalance +
                '}';
    }
}
