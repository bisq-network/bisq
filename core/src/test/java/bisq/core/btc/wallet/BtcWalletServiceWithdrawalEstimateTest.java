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

import bisq.core.btc.model.WithdrawalEstimate;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.junit.jupiter.api.Test;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Estimates withdrawals with the real BtcWalletService and a real bitcoinj wallet, and checks that the amounts of the
 * estimate are what the send of these amounts pays.
 */
class BtcWalletServiceWithdrawalEstimateTest {
    private static final Coin FEE_RATE = Coin.valueOf(50);

    private final WithdrawalWalletFixture fixture = new WithdrawalWalletFixture(FEE_RATE);

    // The 300 satoshi left of the inputs are below bitcoinj's change limit, so bitcoinj adds them to the fee
    @Test
    void withTheFeeIncludedTheSenderSpendsARemainderThatBitcoinjAddsToTheFee() throws Exception {
        fixture.fund(8000);

        WithdrawalEstimate estimate = estimate(Coin.valueOf(7700), false);

        assertAmounts(estimate, 2200, 5800, 8000);
        assertEquals(Coin.ZERO, estimate.getDust());
        assertEquals(Coin.valueOf(300), estimate.getChangeBelowDust());
        assertSent(send(estimate), 2200, 5800, 1);
    }

    // The 540 satoshi left of the inputs are above bitcoinj's change limit of 537 but below our dust limit. The send
    // has no change output, so the fee is for the size without it, and the 540 satoshi are paid on top.
    @Test
    void withTheFeeIncludedTheSenderSpendsAChangeOutputBelowOurDustLimit() throws Exception {
        fixture.fund(8240);

        WithdrawalEstimate estimate = estimate(Coin.valueOf(7700), false);

        assertAmounts(estimate, 2200, 6040, 8240);
        assertEquals(1, estimate.getTransaction().getOutputs().size());
        assertEquals(Coin.ZERO, estimate.getDust());
        assertEquals(Coin.valueOf(540), estimate.getChangeBelowDust());
        assertSent(send(estimate), 2200, 6040, 1);
    }

    // With a fee for a change output, the receiver would get 50 satoshi, below the dust limit
    @Test
    void aChangeBelowOurDustLimitDoesNotRefuseAnAmountThatTheSentTransactionPays() throws Exception {
        fixture.fund(7640);

        WithdrawalEstimate estimate = estimate(Coin.valueOf(7100), false);

        assertAmounts(estimate, 1600, 6040, 7640);
        assertSent(send(estimate), 1600, 6040, 1);
    }

    // At 2 sat/vB the 300 satoshi left of the inputs pay the fee for the size of the transaction already
    @Test
    void aRemainderThatPaysTheRateLeavesTheReceiverTheWholeAmount() throws Exception {
        fixture.fund(8000);

        WithdrawalEstimate estimate = fixture.getBtcWalletService().getWithdrawalEstimate(fixture.getFromAddresses(),
                fixture.getToAddress(),
                Coin.valueOf(7700),
                false,
                Coin.valueOf(2));

        assertAmounts(estimate, 7700, 300, 8000);
        assertSent(send(estimate), 7700, 300, 1);
    }

    @Test
    void withTheFeeExcludedTheReceiverGetsTheAmountAndTheChangeIsReturned() throws Exception {
        fixture.fund(10_000);

        WithdrawalEstimate estimate = estimate(Coin.valueOf(1000), true);

        assertAmounts(estimate, 1000, 7050, 8050);
        assertEquals(Coin.ZERO, estimate.getChangeBelowDust());
        assertSent(send(estimate), 1000, 7050, 2);
    }

    // With the fee excluded, the 540 satoshi left after the fee for a change output stay as a change output below our
    // dust limit in the estimate. The send has no change output and pays them to the fee.
    @Test
    void withTheFeeExcludedAChangeOutputBelowOurDustLimitIsPartOfTheFee() throws Exception {
        fixture.fund(17_590);

        WithdrawalEstimate estimate = estimate(Coin.valueOf(10_000), true);

        assertAmounts(estimate, 10_000, 7590, 17_590);
        assertEquals(Coin.valueOf(540), estimate.getDust());
        assertEquals(Coin.valueOf(540), estimate.getChangeBelowDust());
        assertSent(send(estimate), 10_000, 7590, 1);
    }

    @Test
    void anAmountWellBelowTheInputsLeavesAChangeOutput() throws Exception {
        fixture.fund(100_000);

        WithdrawalEstimate estimate = estimate(Coin.valueOf(50_000), false);

        assertAmounts(estimate, 42_950, 7050, 50_000);
        assertEquals(Coin.ZERO, estimate.getChangeBelowDust());
        assertSent(send(estimate), 42_950, 7050, 2);
    }

    // With the fee for a change output, the 7250 satoshi left of the inputs leave 200 satoshi of change, below
    // bitcoinj's change limit. The fee for a transaction without a change output would leave a change output that
    // this fee does not pay for, so the transaction without a change output and with the whole remainder in the fee
    // is the result.
    @Test
    void withTheFeeExcludedARemainderThatCannotPayForAChangeOutputEndsTheEstimation() throws Exception {
        fixture.fund(17_250);

        WithdrawalEstimate estimate = estimate(Coin.valueOf(10_000), true);

        assertAmounts(estimate, 10_000, 7250, 17_250);
        assertEquals(Coin.valueOf(200), estimate.getChangeBelowDust());
        assertEquals(1, estimate.getTransaction().getOutputs().size());
        assertEquals(2, fixture.getCompletedTxs());
        assertSent(send(estimate), 10_000, 7250, 1);
    }

    // The desktop and the API pass a rate that can differ from the rate of the preferences
    @Test
    void theEstimateIsCheckedWithTheRateItWasBuiltWith() throws Exception {
        fixture.fund(100_000);
        when(fixture.getFeeService().getTxFeePerVbyte()).thenReturn(Coin.valueOf(10));

        WithdrawalEstimate estimate = estimate(Coin.valueOf(50_000), false);

        assertAmounts(estimate, 42_950, 7050, 50_000);
        // The first round has no fee, the second one has the fee for the size of the first one
        assertEquals(2, fixture.getCompletedTxs());
    }

    private WithdrawalEstimate estimate(Coin amount, boolean feeExcluded) throws Exception {
        return fixture.getBtcWalletService().getWithdrawalEstimate(fixture.getFromAddresses(),
                fixture.getToAddress(),
                amount,
                feeExcluded,
                FEE_RATE);
    }

    // As the desktop sends: the sender's amount and the fee of the estimate
    private Transaction send(WithdrawalEstimate estimate) {
        assertThrows(WithdrawalWalletFixture.SendStoppedException.class,
                () -> fixture.getBtcWalletService().sendFundsForMultipleAddresses(fixture.getFromAddresses(),
                        fixture.getToAddress(),
                        estimate.getSendersAmount(),
                        estimate.getFee(),
                        null,
                        null,
                        null,
                        noCallback()));
        assertNotNull(fixture.getSentTx());
        return fixture.getSentTx();
    }

    private static void assertAmounts(WithdrawalEstimate estimate, long receiverAmount, long fee, long sendersAmount) {
        assertEquals(Coin.valueOf(receiverAmount), estimate.getReceiverAmount());
        assertEquals(Coin.valueOf(fee), estimate.getFee());
        assertEquals(Coin.valueOf(sendersAmount), estimate.getSendersAmount());
    }

    private void assertSent(Transaction sentTx, long receiverAmount, long fee, int numOutputs) {
        assertEquals(Coin.valueOf(receiverAmount), sentTx.getOutput(0).getValue());
        assertEquals(fixture.getToAddress(),
                sentTx.getOutput(0).getScriptPubKey().getToAddress(WithdrawalWalletFixture.PARAMS).toString());
        assertEquals(Coin.valueOf(fee), sentTx.getFee());
        assertEquals(numOutputs, sentTx.getOutputs().size());
    }

    private static FutureCallback<Transaction> noCallback() {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Transaction result) {
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
            }
        };
    }
}
