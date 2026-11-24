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

package bisq.core.btc.wallet;

import bisq.core.btc.exceptions.InsufficientFundsException;
import bisq.core.btc.exceptions.ReceiverAmountBelowDustException;
import bisq.core.btc.exceptions.WithdrawalEstimationException;
import bisq.core.btc.model.WithdrawalEstimate;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import java.util.Arrays;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static bisq.core.btc.wallet.WithdrawalTestWallet.PARAMS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Estimates withdrawals on a real bitcoinj wallet and checks that sending the estimate's sender's amount with its fee
 * creates the estimated transaction. One P2WPKH input and a P2WPKH receiver give 110 vbytes, 141 vbytes with a change
 * output, so at 50 sat/vbyte the fee is 5500 sat without and 7050 sat with a change output.
 */
class BtcWalletServiceWithdrawalEstimateTest {
    private static final Coin RATE = Coin.valueOf(50);

    private final WithdrawalTestWallet testWallet = new WithdrawalTestWallet();

    @Nested
    class FeeIncluded {
        @Test
        void aChangeOutputReturnsTheRest() throws Exception {
            testWallet.fund(100_000);

            WithdrawalEstimate estimate = estimate(50_000, false, RATE);

            assertAmounts(estimate, 42_950, 7050, 50_000, 0);
            assertSent(estimate, 2);
        }

        // bitcoinj adds the remainder of 300 sat to the fee itself
        @Test
        void aRemainderBelowTheDustLimitPaysTheFeeFirst() throws Exception {
            testWallet.fund(8000);

            WithdrawalEstimate estimate = estimate(7700, false, RATE);

            assertAmounts(estimate, 2500, 5500, 8000, 300);
            assertSent(estimate, 1);
        }

        // bitcoinj keeps a change of 540 sat, as its change limit is 537 sat, but the sent transaction has none
        @Test
        void aRemainderThatBitcoinjKeepsAsChangePaysTheFeeFirst() throws Exception {
            testWallet.fund(8240);

            WithdrawalEstimate estimate = estimate(7700, false, RATE);

            assertAmounts(estimate, 2740, 5500, 8240, 540);
            assertEquals(110, estimate.vsize());
            assertSent(estimate, 1);
        }

        // At 3 sat/vbyte the fee for the size is 330 sat, of which the remainder pays 300 sat
        @Test
        void theReceiverPaysThePartOfTheFeeThatTheRemainderDoesNotPay() throws Exception {
            testWallet.fund(8000);

            WithdrawalEstimate estimate = estimate(7700, false, Coin.valueOf(3));

            assertAmounts(estimate, 7670, 330, 8000, 300);
            assertSent(estimate, 1);
        }

        // At 2 sat/vbyte the fee for the size is 220 sat. The receiver gets at most the amount, so the whole remainder
        // is the fee.
        @Test
        void aRemainderAboveTheFeeForTheSizeIsTheFee() throws Exception {
            testWallet.fund(8000);

            WithdrawalEstimate estimate = estimate(7700, false, Coin.valueOf(2));

            assertAmounts(estimate, 7700, 300, 8000, 300);
            assertSent(estimate, 1);
        }

        @Test
        void theRemainderCanKeepTheReceiverAboveTheDustLimit() throws Exception {
            testWallet.fund(6300);

            WithdrawalEstimate estimate = estimate(6000, false, RATE);

            assertAmounts(estimate, 800, 5500, 6300, 300);
            assertSent(estimate, 1);
        }

        @Test
        void anAmountThatTheFeeLeavesAtTheDustLimitIsAccepted() throws Exception {
            testWallet.fund(6046);

            WithdrawalEstimate estimate = estimate(6046, false, RATE);

            assertAmounts(estimate, 546, 5500, 6046, 0);
            assertSent(estimate, 1);
        }

        @Test
        void aShorterSignatureAtTheDustLimitLetsTheReceiverBePaid() throws Exception {
            WithdrawalTestWallet legacyWallet = new WithdrawalTestWallet(1, true);
            legacyWallet.fund(8_000_000);

            WithdrawalEstimate estimate = legacyWallet.getBtcWalletService().getWithdrawalEstimate(
                    legacyWallet.getSourceAddresses(), legacyWallet.getToAddress(), Coin.valueOf(4252), false,
                    Coin.valueOf(17));

            assertAmounts(estimate, 546, 3706, 4252, 0);
            assertEquals(218, assertSent(legacyWallet, estimate, 2).getVsize());
        }

        @Test
        void anAmountThatTheFeeLeavesBelowTheDustLimitIsRefused() {
            testWallet.fund(6045);

            assertThrows(ReceiverAmountBelowDustException.class, () -> estimate(6045, false, RATE));
        }

        // A legacy input has its signature outside the segwit data, so the size varies more between the rounds
        @Test
        void aRemainderOfALegacyCoinPaysTheFeeFirst() throws Exception {
            WithdrawalTestWallet legacyWallet = new WithdrawalTestWallet(1, true);
            legacyWallet.fund(8000);
            Coin rate = Coin.valueOf(2);

            WithdrawalEstimate estimate = legacyWallet.getBtcWalletService().getWithdrawalEstimate(
                    legacyWallet.getSourceAddresses(), legacyWallet.getToAddress(), Coin.valueOf(7700), false, rate);

            assertEquals(Coin.valueOf(8000), estimate.sendersAmount());
            assertEquals(Coin.valueOf(300), estimate.dust());
            Transaction sent = legacyWallet.send(estimate.sendersAmount(), estimate.fee());
            assertEquals(estimate.receiverAmount(), sent.getOutput(0).getValue());
            assertEquals(estimate.fee(), sent.getFee());
            assertEquals(estimate.vsize(), sent.getVsize());
            assertEquals(1, sent.getOutputs().size());
            assertFalse(sent.getFee().isLessThan(rate.multiply(sent.getVsize())));
        }

        @Test
        void aRemainderOfTwoAddressesPaysTheFeeFirst() throws Exception {
            WithdrawalTestWallet twoAddresses = new WithdrawalTestWallet(2);
            twoAddresses.fundSourceAddress(0, 5000);
            twoAddresses.fundSourceAddress(1, 3000);

            WithdrawalEstimate estimate = twoAddresses.getBtcWalletService().getWithdrawalEstimate(
                    twoAddresses.getSourceAddresses(), twoAddresses.getToAddress(), Coin.valueOf(7700), false,
                    Coin.valueOf(2));

            assertEquals(Coin.valueOf(8000), estimate.sendersAmount());
            assertEquals(Coin.valueOf(300), estimate.dust());
            assertEquals(Coin.valueOf(2).multiply(estimate.vsize()), estimate.fee());
            Transaction sent = twoAddresses.send(estimate.sendersAmount(), estimate.fee());
            assertEquals(2, sent.getInputs().size());
            assertEquals(1, sent.getOutputs().size());
            assertEquals(estimate.fee(), sent.getFee());
        }

        // The coin selection takes both coins and leaves 300 sat, the fee is for two inputs
        @Test
        void aRemainderOfTwoCoinsPaysTheFeeFirst() throws Exception {
            testWallet.fund(5000, 3000);

            WithdrawalEstimate estimate = estimate(7700, false, Coin.valueOf(2));

            assertEquals(Coin.valueOf(8000), estimate.sendersAmount());
            assertEquals(Coin.valueOf(300), estimate.dust());
            assertEquals(Coin.valueOf(2).multiply(estimate.vsize()), estimate.fee());
            Transaction sent = assertSent(estimate, 1);
            assertEquals(2, sent.getInputs().size());
        }

        // The first completion leaves a remainder of 300 sat. A coin that arrives after it gives the next rounds other
        // coins and a change output, so the remainder no longer belongs to the transaction.
        @Test
        void aCoinThatArrivesDuringTheEstimationRefusesIt() {
            testWallet.fund(8000);
            testWallet.fundAfterCompletedTxs(1, 100_000);

            WithdrawalEstimationException exception = assertThrows(WithdrawalEstimationException.class,
                    () -> estimate(7700, false, RATE));

            assertEquals("The coins changed during the fee estimation", exception.getMessage());
        }

        // The first completion spends the coin of 100,000 sat with a change output. Once that coin is spent elsewhere,
        // the other coin leaves a change of 540 sat, which bitcoinj keeps as an output below the dust limit, or of 300
        // sat, which bitcoinj adds to the fee without a note.
        @ParameterizedTest
        @ValueSource(longs = {50_540, 50_300})
        void aCoinThatIsSpentDuringTheEstimationRefusesIt(long otherCoin) {
            testWallet.fund(100_000, otherCoin);
            testWallet.spendAfterCompletedTxs(1, 100_000);

            WithdrawalEstimationException exception = assertThrows(WithdrawalEstimationException.class,
                    () -> estimate(50_000, false, RATE));

            assertEquals("The coins changed during the fee estimation", exception.getMessage());
        }
    }

    @Nested
    class FeeExcluded {
        @Test
        void aChangeOutputReturnsTheRest() throws Exception {
            testWallet.fund(10_000);

            WithdrawalEstimate estimate = estimate(1000, true, RATE);

            assertAmounts(estimate, 1000, 7050, 8050, 0);
            assertSent(estimate, 2);
        }

        @Test
        void anAmountBelowTheDustLimitIsRefused() {
            testWallet.fund(10_000);

            assertThrows(ReceiverAmountBelowDustException.class, () -> estimate(545, true, RATE));
        }

        // The coin pays the amount, the fee for a change output, 7050 sat, and a change of 546 sat
        @Test
        void aChangeAtTheDustLimitIsReturned() throws Exception {
            testWallet.fund(17_596);

            WithdrawalEstimate estimate = estimate(10_000, true, RATE);

            assertAmounts(estimate, 10_000, 7050, 17_050, 0);
            assertEquals(Coin.valueOf(546), assertSent(estimate, 2).getOutput(1).getValue());
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void aShorterSignatureAtTheDustLimitLetsChangeBeReturned(boolean anotherCoinAvailable) throws Exception {
            WithdrawalTestWallet legacyWallet = new WithdrawalTestWallet(1, true);
            legacyWallet.fund(8_000_000);
            if (anotherCoinAvailable)
                legacyWallet.fund(1_000_000);

            WithdrawalEstimate estimate = legacyWallet.getBtcWalletService().getWithdrawalEstimate(
                    legacyWallet.getSourceAddresses(), legacyWallet.getToAddress(), Coin.valueOf(7_994_004), true,
                    Coin.valueOf(25));

            assertAmounts(estimate, 7_994_004, 5450, 7_999_454, 0);
            Transaction sent = assertSent(legacyWallet, estimate, 2);
            assertEquals(1, sent.getInputs().size());
            assertEquals(218, sent.getVsize());
            assertEquals(Coin.valueOf(546), sent.getOutput(1).getValue());
        }

        // A change output would get 200 sat after its own fee, which bitcoinj adds to the fee
        @Test
        void aChangeBelowTheDustLimitMakesTheRestTheFee() throws Exception {
            testWallet.fund(17_250);

            WithdrawalEstimate estimate = estimate(10_000, true, RATE);

            assertAmounts(estimate, 10_000, 7250, 17_250, 200);
            assertEquals(110, estimate.vsize());
            assertSent(estimate, 1);
        }

        // A change output would get 540 sat after its own fee, which bitcoinj keeps as change
        @Test
        void aChangeThatBitcoinjKeepsBelowTheDustLimitMakesTheRestTheFee() throws Exception {
            testWallet.fund(17_590);

            WithdrawalEstimate estimate = estimate(10_000, true, RATE);

            assertAmounts(estimate, 10_000, 7590, 17_590, 540);
            assertEquals(110, estimate.vsize());
            assertSent(estimate, 1);
        }

        // Without a fee the first round leaves a change of 545 sat. The fee for the size, 110 sat at 1 sat/vbyte, is paid
        // from it, so 435 sat of it are above that fee.
        @Test
        void aChangeOfTheFirstRoundIsNotedWithoutTheFeeForTheSize() throws Exception {
            testWallet.fund(20_000);

            WithdrawalEstimate estimate = estimate(19_455, true, Coin.valueOf(1));

            assertAmounts(estimate, 19_455, 545, 20_000, 435);
            assertSent(estimate, 1);
        }

        // The coin matches the amount and the fee for a change output, 7050 sat, so there is no change output. The fee
        // without it would be 5500 sat.
        @Test
        void aCoinThatMatchesTheAmountAndTheFeeLeavesNoChangeOutput() throws Exception {
            testWallet.fund(17_050);

            WithdrawalEstimate estimate = estimate(10_000, true, RATE);

            assertAmounts(estimate, 10_000, 7050, 17_050, 0);
            assertEquals(110, estimate.vsize());
            assertSent(estimate, 1);
        }

        // The coins cannot pay the fee for a change output, 7050 sat, but the fee without it, 5500 sat
        @Test
        void coinsThatPayTheFeeOnlyWithoutAChangeOutputAreSpent() throws Exception {
            testWallet.fund(15_600);

            WithdrawalEstimate estimate = estimate(10_000, true, RATE);

            assertAmounts(estimate, 10_000, 5600, 15_600, 0);
            assertSent(estimate, 1);
        }

        @Test
        void coinsThatCannotPayTheFeeWithoutAChangeOutputAreRefused() {
            testWallet.fund(15_400);

            assertThrows(InsufficientFundsException.class, () -> estimate(10_000, true, RATE));
        }

        // Each small coin adds 700 sat and about 683 sat of fee for its input, so every round adds coins, more than
        // ten rounds are needed, and the coins never pay a change output of at least the dust limit
        @Test
        void smallCoinsAreAddedUntilTheyPayTheFee() throws Exception {
            testWallet.fund(100_600);
            long[] smallCoins = new long[60];
            Arrays.fill(smallCoins, 700);
            testWallet.fund(smallCoins);
            Coin rate = Coin.valueOf(10);

            WithdrawalEstimate estimate = estimate(100_000, true, rate);

            assertEquals(Coin.valueOf(100_000), estimate.receiverAmount());
            Transaction sent = assertSent(estimate, 1);
            assertFalse(sent.getFee().isLessThan(rate.multiply(sent.getVsize())));
        }

        // The second round leaves a change of 200 sat, so the estimation spends all coins. A coin that arrives before
        // would make that transaction one with a change output.
        @Test
        void aCoinThatArrivesDuringTheEstimationRefusesIt() {
            testWallet.fund(17_250);
            testWallet.fundAfterCompletedTxs(2, 100_000);

            WithdrawalEstimationException exception = assertThrows(WithdrawalEstimationException.class,
                    () -> estimate(10_000, true, RATE));

            assertEquals("The coins changed during the fee estimation", exception.getMessage());
        }

        @Test
        void anAmountAboveTheCoinsIsRefused() {
            testWallet.fund(10_000);

            assertThrows(InsufficientFundsException.class, () -> estimate(10_001, true, RATE));
        }

        // The fee for a change output and one input takes more than the first coin leaves, so the second coin of the
        // other address is added
        @Test
        void aCoinIsAddedFromAnotherAddressForTheFee() throws Exception {
            WithdrawalTestWallet twoAddresses = new WithdrawalTestWallet(2);
            twoAddresses.fundSourceAddress(0, 12_000);
            twoAddresses.fundSourceAddress(1, 9_000);

            WithdrawalEstimate estimate = twoAddresses.getBtcWalletService().getWithdrawalEstimate(
                    twoAddresses.getSourceAddresses(), twoAddresses.getToAddress(), Coin.valueOf(5000), true, RATE);

            assertEquals(Coin.valueOf(5000), estimate.receiverAmount());
            assertEquals(RATE.multiply(estimate.vsize()), estimate.fee());
            Transaction sent = twoAddresses.send(estimate.sendersAmount(), estimate.fee());
            assertEquals(2, sent.getInputs().size());
            assertEquals(2, sent.getOutputs().size());
            assertEquals(estimate.receiverAmount(), sent.getOutput(0).getValue());
            assertEquals(estimate.fee(), sent.getFee());
            assertEquals(estimate.vsize(), sent.getVsize());
        }

        // With both coins a change output costs more than the 9000 sat that the coins leave.
        // Without it the fee for the size is 8850 sat, so all coins of both addresses are spent. No change output could
        // be paid, so there is no remainder to note.
        @Test
        void coinsOfTwoAddressesThatPayTheFeeOnlyWithoutAChangeOutputAreSpent() throws Exception {
            WithdrawalTestWallet twoAddresses = new WithdrawalTestWallet(2);
            twoAddresses.fundSourceAddress(0, 10_000);
            twoAddresses.fundSourceAddress(1, 9000);

            WithdrawalEstimate estimate = twoAddresses.getBtcWalletService().getWithdrawalEstimate(
                    twoAddresses.getSourceAddresses(), twoAddresses.getToAddress(), Coin.valueOf(10_000), true, RATE);

            assertAmounts(estimate, 10_000, 9000, 19_000, 0);
            assertEquals(177, estimate.vsize());
            Transaction sent = twoAddresses.send(estimate.sendersAmount(), estimate.fee());
            assertEquals(2, sent.getInputs().size());
            assertEquals(1, sent.getOutputs().size());
            assertEquals(estimate.receiverAmount(), sent.getOutput(0).getValue());
            assertEquals(estimate.fee(), sent.getFee());
            assertEquals(estimate.vsize(), sent.getVsize());
        }
    }

    @Test
    void aRateThatIsNotPositiveIsRefused() {
        testWallet.fund(10_000);

        assertThrows(IllegalArgumentException.class, () -> estimate(5000, false, Coin.ZERO));
        assertThrows(IllegalArgumentException.class, () -> estimate(5000, true, Coin.valueOf(-1)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aReplacementInputCannotBeChargedForThreeInputs(boolean feeExcluded) {
        testWallet.fund(50_000, 50_100, 50_200);
        testWallet.fundAfterCompletedTxs(feeExcluded ? 1 : 2, 1_000_000);

        WithdrawalEstimationException exception = assertThrows(WithdrawalEstimationException.class,
                () -> estimate(110_000, feeExcluded, RATE));
        assertEquals("The coins changed during the fee estimation", exception.getMessage());
    }

    // The coin selection takes the coins by value times confirmations: 9000 sat with 6 confirmations before 50,000 sat
    // with 1, so the first rounds spend both. After a block the larger coin comes first, and a later round would spend
    // it alone with the fee for two inputs.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aBlockThatReordersTheCoinsDuringTheEstimationRefusesIt(boolean feeExcluded) {
        testWallet.fundWithConfirmations(6, 9000);
        testWallet.fundWithConfirmations(1, 50_000);
        testWallet.confirmAfterCompletedTxs(feeExcluded ? 1 : 2);

        WithdrawalEstimationException exception = assertThrows(WithdrawalEstimationException.class,
                () -> estimate(25_000, feeExcluded, RATE));
        assertEquals("The coins changed during the fee estimation", exception.getMessage());
    }

    private WithdrawalEstimate estimate(long amount, boolean feeExcluded, Coin rate) throws Exception {
        return testWallet.getBtcWalletService().getWithdrawalEstimate(testWallet.getSourceAddresses(),
                testWallet.getToAddress(),
                Coin.valueOf(amount),
                feeExcluded,
                rate);
    }

    private static void assertAmounts(WithdrawalEstimate estimate,
                                      long receiverAmount,
                                      long fee,
                                      long sendersAmount,
                                      long dust) {
        assertEquals(Coin.valueOf(receiverAmount), estimate.receiverAmount());
        assertEquals(Coin.valueOf(fee), estimate.fee());
        assertEquals(Coin.valueOf(sendersAmount), estimate.sendersAmount());
        assertEquals(Coin.valueOf(dust), estimate.dust());
    }

    // Sends the estimate's sender's amount with its fee and checks that the sent transaction is the estimated one
    private Transaction assertSent(WithdrawalEstimate estimate, int numOutputs) throws Exception {
        return assertSent(testWallet, estimate, numOutputs);
    }

    private static Transaction assertSent(WithdrawalTestWallet wallet, WithdrawalEstimate estimate, int numOutputs)
            throws Exception {
        Transaction sent = wallet.send(estimate.sendersAmount(), estimate.fee());
        assertEquals(estimate.receiverAmount(), sent.getOutput(0).getValue());
        assertEquals(wallet.getToAddress(), sent.getOutput(0).getScriptPubKey().getToAddress(PARAMS).toString());
        assertEquals(estimate.fee(), sent.getFee());
        assertEquals(estimate.vsize(), sent.getVsize());
        assertEquals(numOutputs, sent.getOutputs().size());
        for (int i = 1; i < sent.getOutputs().size(); i++) {
            assertFalse(Restrictions.isDust(sent.getOutput(i).getValue()));
        }
        return sent;
    }
}
