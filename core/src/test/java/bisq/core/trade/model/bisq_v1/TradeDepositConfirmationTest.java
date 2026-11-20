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

package bisq.core.trade.model.bisq_v1;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferMaker;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.Threading;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The deposit tx of a trade can be replaced, for example a tx seen in the network by the tx of the deposit tx
// message (report7b). Only the confirmation of the current deposit tx may move the trade to deposit confirmed.
class TradeDepositConfirmationTest {
    private static final NetworkParameters PARAMS = RegTestParams.get();

    private Executor userThread;

    @BeforeEach
    void setUp() {
        Context.propagate(new Context(PARAMS));
        // Confidence listeners run on the bitcoinj user thread; run them synchronously in the test.
        userThread = Threading.USER_THREAD;
        Threading.USER_THREAD = Threading.SAME_THREAD;
    }

    @AfterEach
    void tearDown() {
        Threading.USER_THREAD = userThread;
    }

    @Test
    void confirmationOfReplacedDepositTxDoesNotConfirmTheTrade() {
        SellerAsMakerTrade trade = trade();
        Transaction replacedDepositTx = transaction(1_000);
        Transaction depositTx = transaction(2_000);

        trade.applyDepositTx(replacedDepositTx);
        trade.applyDepositTx(depositTx);
        confirm(replacedDepositTx);

        assertFalse(trade.isDepositConfirmed());

        confirm(depositTx);

        assertTrue(trade.isDepositConfirmed());
    }

    @Test
    void depositTxThatIsAlreadyInABlockConfirmsTheTrade() {
        SellerAsMakerTrade trade = trade();
        Transaction depositTx = transaction(1_000);
        confirm(depositTx);

        trade.applyDepositTx(depositTx);

        assertTrue(trade.isDepositConfirmed());
    }

    private static void confirm(Transaction tx) {
        TransactionConfidence confidence = tx.getConfidence();
        confidence.setAppearedAtChainHeight(100);
        confidence.setDepthInBlocks(1);
        confidence.queueListeners(TransactionConfidence.Listener.ChangeReason.DEPTH);
    }

    private static Transaction transaction(long value) {
        Transaction tx = new Transaction(PARAMS);
        tx.addOutput(Coin.valueOf(value), ScriptBuilder.createP2WPKHOutputScript(new ECKey()));
        return tx;
    }

    private static SellerAsMakerTrade trade() {
        Offer offer = make(a(OfferMaker.Offer, with(OfferMaker.direction, OfferDirection.SELL)));
        return new SellerAsMakerTrade(offer,
                Coin.valueOf(1_000) /* tradeTxFee */,
                Coin.valueOf(100) /* takerFee */,
                true,
                null /* arbitratorNodeAddress */,
                null /* mediatorNodeAddress */,
                null /* refundAgentNodeAddress */,
                null /* btcWalletService, unused for an applied deposit tx */,
                null /* processModel, unused by the confidence listener */,
                "uid");
    }
}
