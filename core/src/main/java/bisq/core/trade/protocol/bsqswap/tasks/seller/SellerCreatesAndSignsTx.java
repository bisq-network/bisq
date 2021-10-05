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

package bisq.core.trade.protocol.bsqswap.tasks.seller;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.bsqswap.BsqSwapCalculation;
import bisq.core.trade.protocol.bsqswap.BsqSwapTradePeer;
import bisq.core.trade.protocol.bsqswap.tasks.BsqSwapTask;

import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;


@Slf4j
public abstract class SellerCreatesAndSignsTx extends BsqSwapTask {
    // Both traders pay half of base tx size: 10 / 2
    // Smallest value is 1 segwit input: 41 + 29
    // Min. 1 output for BSQ payout, change is optional: 31
    private static final int MIN_SELLERS_TX_SIZE = 106;

    public SellerCreatesAndSignsTx(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            TradeWalletService tradeWalletService = protocolModel.getTradeWalletService();
            BsqSwapTradePeer tradePeer = protocolModel.getTradePeer();

            protocolModel.setBtcAddress(protocolModel.getBtcWalletService().getFreshAddressEntry().getAddressString());
            protocolModel.setBsqAddress(protocolModel.getBsqWalletService().getUnusedAddress().toString());
            protocolModel.setPayout(trade.getBsqTradeAmount());

            // Figure out how large out tx will be
            int iterations = 0;
            Tuple2<List<RawTransactionInput>, Coin> tuple = null;
            Coin previous = null;
            long sellersTradeFee = getSellersTradeFee();
            // At first we try with min. tx size
            int sellersTxSize = MIN_SELLERS_TX_SIZE;
            Coin required = BsqSwapCalculation.getSellersRequiredBtcInput(trade, sellersTradeFee, sellersTxSize);

            // As fee calculation is not deterministic it could be that we toggle between a too small and too large
            // input. We would take the latest result before we break iteration. Worst case is that we under- or
            // overpay a bit. As fee rate is anyway an estimation we ignore that imperfection.
            while (iterations < 10 && !required.equals(previous)) {
                tuple = tradeWalletService.getSellersBtcInputsForBsqSwapTx(required);
                previous = required;

                // We calculate more exact tx size based on resulted inputs and change
                sellersTxSize = BsqSwapCalculation.getTxSize(protocolModel.getTradeWalletService(), tuple.first, tuple.second.getValue());
                required = BsqSwapCalculation.getSellersRequiredBtcInput(trade, sellersTradeFee, sellersTxSize);
                iterations++;
            }

            checkNotNull(tuple);
            List<RawTransactionInput> sellersInputs = tuple.first;
            long sellersChange = tuple.second.value;

            protocolModel.setInputs(sellersInputs);
            protocolModel.setChange(sellersChange);

            long buyersBtcPayout = BsqSwapCalculation.getBuyersBtcPayoutAmount(sellersTradeFee,
                    trade,
                    getBuyersTxSize(),
                    getBuyersTradeFee());
            tradePeer.setPayout(buyersBtcPayout);

            List<RawTransactionInput> buyersBsqInputs = Objects.requireNonNull(tradePeer.getInputs());
            List<RawTransactionInput> sellersBtcInputs = Objects.requireNonNull(sellersInputs);

            Coin sellersBsqPayoutAmount = Coin.valueOf(protocolModel.getPayout());
            String sellersBsqPayoutAddress = protocolModel.getBsqAddress();

            Coin buyersBsqChangeAmount = Coin.valueOf(tradePeer.getChange());
            String buyersBsqChangeAddress = tradePeer.getBsqAddress();

            Coin buyersBtcPayoutAmount = Coin.valueOf(buyersBtcPayout);
            String buyersBtcPayoutAddress = tradePeer.getBtcAddress();

            Coin sellersBtcChangeAmount = Coin.valueOf(sellersChange);
            String sellersBtcChangeAddress = protocolModel.getBtcAddress();

            Transaction transaction = tradeWalletService.sellerBuildBsqSwapTx(
                    buyersBsqInputs,
                    sellersBtcInputs,
                    sellersBsqPayoutAmount,
                    sellersBsqPayoutAddress,
                    buyersBsqChangeAmount,
                    buyersBsqChangeAddress,
                    buyersBtcPayoutAmount,
                    buyersBtcPayoutAddress,
                    sellersBtcChangeAmount,
                    sellersBtcChangeAddress);

            // Sign tx
            int buyersInputSize = buyersBsqInputs.size();
            List<TransactionInput> myInputs = transaction.getInputs().stream()
                    .filter(input -> input.getIndex() >= buyersInputSize)
                    .collect(Collectors.toList());
            tradeWalletService.signBsqSwapTransaction(transaction, myInputs);

            log.error("Sellers signed transaction {}", transaction);
            protocolModel.applyTransaction(transaction);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    private int getBuyersTxSize() {
        return BsqSwapCalculation.getTxSize(protocolModel.getTradeWalletService(),
                protocolModel.getTradePeer().getInputs(),
                protocolModel.getTradePeer().getChange());
    }

    protected abstract long getBuyersTradeFee();

    protected abstract long getSellersTradeFee();
}
