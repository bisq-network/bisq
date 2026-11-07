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

package bisq.core.trade.validation;

import bisq.core.provider.fee.FeeService;
import bisq.core.trade.validation.ValidationTestUtils.InputsForDepositTxRequestValidationFixture;

import bisq.common.crypto.CryptoException;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.validation.ValidationTestUtils.configureTradeFeeService;
import static bisq.core.trade.validation.ValidationTestUtils.inputsForDepositTxRequestValidationFixture;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class InputsForDepositTxRequestValidationTest {

    /* --------------------------------------------------------------------- */
    // InputsForDepositTxRequest
    /* --------------------------------------------------------------------- */

    @Test
    void checkInputsForDepositTxRequestAcceptsValidRequest() throws CryptoException {
        InputsForDepositTxRequestValidationFixture fixture = inputsForDepositTxRequestValidationFixture(null);

        assertSame(fixture.request, InputsForDepositTxRequestValidation.checkInputsForDepositTxRequest(fixture.request,
                fixture.offer,
                fixture.user,
                fixture.btcWalletService,
                fixture.priceFeedService,
                fixture.delayedPayoutTxReceiverService,
                fixture.feeService));
    }

    @Test
    void checkInputsForDepositTxRequestRejectsInvalidAccountAgeWitnessSignature() throws CryptoException {
        InputsForDepositTxRequestValidationFixture fixture = inputsForDepositTxRequestValidationFixture(new byte[]{1});

        assertThrows(IllegalArgumentException.class, () -> InputsForDepositTxRequestValidation.checkInputsForDepositTxRequest(fixture.request,
                fixture.offer,
                fixture.user,
                fixture.btcWalletService,
                fixture.priceFeedService,
                fixture.delayedPayoutTxReceiverService,
                fixture.feeService));
    }

    // The fixture's request states the trade tx fee at 2 sat/vbyte. A maker whose fee rate is
    // MAX_FEE_DEVIATION_FACTOR times higher still accepts it, as fee providers can differ that much.
    @Test
    void checkInputsForDepositTxRequestAcceptsTxFeeAtTheDeviationFactor() throws CryptoException {
        InputsForDepositTxRequestValidationFixture fixture = inputsForDepositTxRequestValidationFixture(null);
        long makersFeeRate = (long) (2 * MinerFeeValidation.MAX_FEE_DEVIATION_FACTOR);
        FeeService feeService = configureTradeFeeService(Coin.valueOf(77), Coin.valueOf(100), makersFeeRate);

        assertSame(fixture.request, InputsForDepositTxRequestValidation.checkInputsForDepositTxRequest(fixture.request,
                fixture.offer,
                fixture.user,
                fixture.btcWalletService,
                fixture.priceFeedService,
                fixture.delayedPayoutTxReceiverService,
                feeService));
    }

    @Test
    void checkInputsForDepositTxRequestRejectsTxFeeOutsideAllowedTolerance() throws CryptoException {
        InputsForDepositTxRequestValidationFixture fixture = inputsForDepositTxRequestValidationFixture(null);
        long makersFeeRate = (long) (2 * MinerFeeValidation.MAX_FEE_DEVIATION_FACTOR) + 1;
        FeeService feeService = configureTradeFeeService(Coin.valueOf(77), Coin.valueOf(100), makersFeeRate);

        assertThrows(IllegalArgumentException.class, () -> InputsForDepositTxRequestValidation.checkInputsForDepositTxRequest(fixture.request,
                fixture.offer,
                fixture.user,
                fixture.btcWalletService,
                fixture.priceFeedService,
                fixture.delayedPayoutTxReceiverService,
                feeService));
    }

    @Test
    void checkInputsForDepositTxRequestRejectsUnexpectedTakerFee() throws CryptoException {
        InputsForDepositTxRequestValidationFixture fixture =
                inputsForDepositTxRequestValidationFixture(null, Coin.valueOf(151));

        assertThrows(IllegalArgumentException.class, () -> InputsForDepositTxRequestValidation.checkInputsForDepositTxRequest(fixture.request,
                fixture.offer,
                fixture.user,
                fixture.btcWalletService,
                fixture.priceFeedService,
                fixture.delayedPayoutTxReceiverService,
                fixture.feeService));
    }

    @Test
    void checkInputsForDepositTxRequestRejectsNullOfferId() throws CryptoException {
        InputsForDepositTxRequestValidationFixture fixture = inputsForDepositTxRequestValidationFixture(null);
        when(fixture.offer.getId()).thenReturn(null);

        assertThrows(NullPointerException.class, () -> InputsForDepositTxRequestValidation.checkInputsForDepositTxRequest(fixture.request,
                fixture.offer,
                fixture.user,
                fixture.btcWalletService,
                fixture.priceFeedService,
                fixture.delayedPayoutTxReceiverService,
                fixture.feeService));
    }

    @Test
    void checkInputsForDepositTxRequestRejectsBlankOfferId() throws CryptoException {
        InputsForDepositTxRequestValidationFixture fixture = inputsForDepositTxRequestValidationFixture(null);
        when(fixture.offer.getId()).thenReturn(" ");

        assertThrows(IllegalArgumentException.class, () -> InputsForDepositTxRequestValidation.checkInputsForDepositTxRequest(fixture.request,
                fixture.offer,
                fixture.user,
                fixture.btcWalletService,
                fixture.priceFeedService,
                fixture.delayedPayoutTxReceiverService,
                fixture.feeService));
    }


}
