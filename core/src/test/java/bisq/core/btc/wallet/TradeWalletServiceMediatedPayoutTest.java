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

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.utils.PayoutTransactionUtil;
import bisq.core.user.Preferences;

import bisq.common.config.Config;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;

import java.util.Arrays;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeWalletServiceMediatedPayoutTest {
    private static final DeterministicKey BUYER_KEY = HDKeyDerivation.createMasterPrivateKey(seed(1));
    private static final DeterministicKey SELLER_KEY = HDKeyDerivation.createMasterPrivateKey(seed(2));

    private TradeWalletService tradeWalletService;
    private Transaction depositTx;
    private String buyerAddress;
    private String sellerAddress;

    @BeforeEach
    void setUp() {
        // TradeWalletService parses the payout addresses with the configured network
        NetworkParameters params = Config.baseCurrencyNetworkParameters();
        Context.propagate(new Context(params));
        WalletsSetup walletsSetup = mock(WalletsSetup.class);
        when(walletsSetup.getBtcWallet()).thenReturn(Wallet.createBasic(params));
        tradeWalletService = new TradeWalletService(walletsSetup, mock(Preferences.class));
        ArgumentCaptor<Runnable> setupCompletedHandler = ArgumentCaptor.forClass(Runnable.class);
        verify(walletsSetup).addSetupCompletedHandler(setupCompletedHandler.capture());
        setupCompletedHandler.getValue().run();

        Script redeemScript = PayoutTransactionUtil.get2of2MultiSigRedeemScript(BUYER_KEY.getPubKey(),
                SELLER_KEY.getPubKey());
        depositTx = new Transaction(params);
        depositTx.addInput(new TransactionInput(params,
                depositTx,
                new byte[]{},
                new TransactionOutPoint(params, 0, Sha256Hash.ZERO_HASH),
                Coin.valueOf(120_000)));
        depositTx.addOutput(Coin.valueOf(110_000), ScriptBuilder.createP2WSHOutputScript(redeemScript));
        buyerAddress = SegwitAddress.fromKey(params, new ECKey()).toString();
        sellerAddress = SegwitAddress.fromKey(params, new ECKey()).toString();
    }

    @Test
    void finalizesPayoutThatBothSignaturesCover() throws Exception {
        byte[] buyerSignature = sign(BUYER_KEY, 80_000, 30_000);
        byte[] sellerSignature = sign(SELLER_KEY, 80_000, 30_000);

        Transaction payoutTx = finalizePayout(buyerSignature, sellerSignature, 80_000, 30_000);

        assertEquals(Coin.valueOf(80_000), payoutTx.getOutput(0).getValue());
        assertEquals(Coin.valueOf(30_000), payoutTx.getOutput(1).getValue());
    }

    @Test
    void rejectsSellerSignatureOfAnotherPayout() throws Exception {
        byte[] buyerSignature = sign(BUYER_KEY, 70_000, 40_000);
        byte[] sellerSignature = sign(SELLER_KEY, 80_000, 30_000);

        assertThrows(TransactionVerificationException.class,
                () -> finalizePayout(buyerSignature, sellerSignature, 70_000, 40_000));
    }

    @Test
    void rejectsBuyerSignatureOfAnotherPayout() throws Exception {
        byte[] buyerSignature = sign(BUYER_KEY, 80_000, 30_000);
        byte[] sellerSignature = sign(SELLER_KEY, 70_000, 40_000);

        assertThrows(TransactionVerificationException.class,
                () -> finalizePayout(buyerSignature, sellerSignature, 70_000, 40_000));
    }

    private byte[] sign(DeterministicKey key, long buyerPayoutAmount, long sellerPayoutAmount) throws Exception {
        return tradeWalletService.signMediatedPayoutTx(depositTx,
                Coin.valueOf(buyerPayoutAmount),
                Coin.valueOf(sellerPayoutAmount),
                buyerAddress,
                sellerAddress,
                key,
                BUYER_KEY.getPubKey(),
                SELLER_KEY.getPubKey());
    }

    private Transaction finalizePayout(byte[] buyerSignature,
                                       byte[] sellerSignature,
                                       long buyerPayoutAmount,
                                       long sellerPayoutAmount) throws Exception {
        return tradeWalletService.finalizeMediatedPayoutTx(depositTx,
                buyerSignature,
                sellerSignature,
                Coin.valueOf(buyerPayoutAmount),
                Coin.valueOf(sellerPayoutAmount),
                buyerAddress,
                sellerAddress,
                BUYER_KEY,
                BUYER_KEY.getPubKey(),
                SELLER_KEY.getPubKey());
    }

    private static byte[] seed(int value) {
        byte[] seed = new byte[32];
        Arrays.fill(seed, (byte) value);
        return seed;
    }
}
