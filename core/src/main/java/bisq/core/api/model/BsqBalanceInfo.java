package bisq.core.api.model;

import bisq.common.Payload;

import com.google.common.annotations.VisibleForTesting;

import lombok.Getter;

@Getter
public class BsqBalanceInfo implements Payload {

    public static final BsqBalanceInfo EMPTY = new BsqBalanceInfo(-1,
            -1,
            -1,
            -1,
            -1,
            -1);

    // All balances are in BSQ satoshis.
    private final long availableConfirmedBalance;
    private final long unverifiedBalance;
    private final long unconfirmedChangeBalance;
    private final long lockedForVotingBalance;
    private final long lockupBondsBalance;
    private final long unlockingBondsBalance;

    public BsqBalanceInfo(long availableConfirmedBalance,
                          long unverifiedBalance,
                          long unconfirmedChangeBalance,
                          long lockedForVotingBalance,
                          long lockupBondsBalance,
                          long unlockingBondsBalance) {
        this.availableConfirmedBalance = availableConfirmedBalance;
        this.unverifiedBalance = unverifiedBalance;
        this.unconfirmedChangeBalance = unconfirmedChangeBalance;
        this.lockedForVotingBalance = lockedForVotingBalance;
        this.lockupBondsBalance = lockupBondsBalance;
        this.unlockingBondsBalance = unlockingBondsBalance;
    }

    @VisibleForTesting
    public static BsqBalanceInfo valueOf(long availableConfirmedBalance,
                                         long unverifiedBalance,
                                         long unconfirmedChangeBalance,
                                         long lockedForVotingBalance,
                                         long lockupBondsBalance,
                                         long unlockingBondsBalance) {
        // Convenience for creating a model instance instead of a proto.
        return new BsqBalanceInfo(availableConfirmedBalance,
                unverifiedBalance,
                unconfirmedChangeBalance,
                lockedForVotingBalance,
                lockupBondsBalance,
                unlockingBondsBalance);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.BsqBalanceInfo toProtoMessage() {
        return bisq.proto.grpc.BsqBalanceInfo.newBuilder()
                .setAvailableConfirmedBalance(availableConfirmedBalance)
                .setUnverifiedBalance(unverifiedBalance)
                .setUnconfirmedChangeBalance(unconfirmedChangeBalance)
                .setLockedForVotingBalance(lockedForVotingBalance)
                .setLockupBondsBalance(lockupBondsBalance)
                .setUnlockingBondsBalance(unlockingBondsBalance)
                .build();

    }

    public static BsqBalanceInfo fromProto(bisq.proto.grpc.BsqBalanceInfo proto) {
        return new BsqBalanceInfo(proto.getAvailableConfirmedBalance(),
                proto.getUnverifiedBalance(),
                proto.getUnconfirmedChangeBalance(),
                proto.getLockedForVotingBalance(),
                proto.getLockupBondsBalance(),
                proto.getUnlockingBondsBalance());
    }

    @Override
    public String toString() {
        return "BsqBalanceInfo{" +
                "availableConfirmedBalance=" + availableConfirmedBalance +
                ", unverifiedBalance=" + unverifiedBalance +
                ", unconfirmedChangeBalance=" + unconfirmedChangeBalance +
                ", lockedForVotingBalance=" + lockedForVotingBalance +
                ", lockupBondsBalance=" + lockupBondsBalance +
                ", unlockingBondsBalance=" + unlockingBondsBalance +
                '}';
    }
}
