package bisq.apitest.method.wallet;

import bisq.proto.grpc.BsqBalanceInfo;
import bisq.proto.grpc.BtcBalanceInfo;

import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class WalletTestUtil {

    // All api tests depend on the DAO / regtest environment, and Bob & Alice's wallets
    // are initialized with 10 BTC during the scaffolding setup.
    public static final bisq.core.api.model.BtcBalanceInfo INITIAL_BTC_BALANCES =
            bisq.core.api.model.BtcBalanceInfo.valueOf(1000000000,
                    0,
                    1000000000,
                    0);


    // Alice's regtest BSQ wallet is initialized with 1,000,000 BSQ.
    public static final bisq.core.api.model.BsqBalanceInfo ALICES_INITIAL_BSQ_BALANCES =
            bsqBalanceModel(100000000,
                    0,
                    0,
                    0,
                    0,
                    0);

    // Bob's regtest BSQ wallet is initialized with 1,500,000 BSQ.
    public static final bisq.core.api.model.BsqBalanceInfo BOBS_INITIAL_BSQ_BALANCES =
            bsqBalanceModel(150000000,
                    0,
                    0,
                    0,
                    0,
                    0);

    @SuppressWarnings("SameParameterValue")
    public static bisq.core.api.model.BsqBalanceInfo bsqBalanceModel(long availableBalance,
                                                                     long unverifiedBalance,
                                                                     long unconfirmedChangeBalance,
                                                                     long lockedForVotingBalance,
                                                                     long lockupBondsBalance,
                                                                     long unlockingBondsBalance) {
        return bisq.core.api.model.BsqBalanceInfo.valueOf(availableBalance,
                unverifiedBalance,
                unconfirmedChangeBalance,
                lockedForVotingBalance,
                lockupBondsBalance,
                unlockingBondsBalance);
    }

    public static void verifyBsqBalances(bisq.core.api.model.BsqBalanceInfo expected,
                                         BsqBalanceInfo actual) {
        assertEquals(expected.getAvailableBalance(), actual.getAvailableConfirmedBalance());
        assertEquals(expected.getUnverifiedBalance(), actual.getUnverifiedBalance());
        assertEquals(expected.getUnconfirmedChangeBalance(), actual.getUnconfirmedChangeBalance());
        assertEquals(expected.getLockedForVotingBalance(), actual.getLockedForVotingBalance());
        assertEquals(expected.getLockupBondsBalance(), actual.getLockupBondsBalance());
        assertEquals(expected.getUnlockingBondsBalance(), actual.getUnlockingBondsBalance());
    }

    public static void verifyBtcBalances(bisq.core.api.model.BtcBalanceInfo expected,
                                         BtcBalanceInfo actual) {
        assertEquals(expected.getAvailableBalance(), actual.getAvailableBalance());
        assertEquals(expected.getReservedBalance(), actual.getReservedBalance());
        assertEquals(expected.getTotalAvailableBalance(), actual.getTotalAvailableBalance());
        assertEquals(expected.getLockedBalance(), actual.getLockedBalance());
    }
}
