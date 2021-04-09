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

package bisq.core.trade.atomic.protocol;

// Keep details specific to Atomic trades separate. Normal trade details are still handled by ProcessModel
// Atomic trades are handled in one sequence of interaction without downtime. If the trade negotiation fails
// there is no mediation, just no trade.
// Only atomic Tx Id is persisted with the Trade

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.monetary.Volume;
import bisq.core.trade.Trade;
import bisq.core.trade.atomic.messages.CreateAtomicTxRequest;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Getter
@Slf4j
public class AtomicModel {
    @Setter
    private long bsqTradeAmount;
    @Setter
    private long bsqMaxTradeAmount;
    @Setter
    private long bsqMinTradeAmount;
    @Setter
    private long btcTradeAmount;
    @Setter
    private long btcMaxTradeAmount;
    @Setter
    private long btcMinTradeAmount;
    @Setter
    private long tradePrice;
    @Setter
    private long bsqTradeFee;
    @Setter
    private long btcTradeFee;
    @Setter
    private long txFee;
    @Setter
    private long takerBsqOutputAmount;
    @Setter
    private long takerBtcOutputAmount;
    @Setter
    private long makerBsqOutputAmount;
    @Setter
    private long makerBtcOutputAmount;
    @Nullable
    @Setter
    private String takerBsqAddress;
    @Nullable
    @Setter
    private String takerBtcAddress;
    @Nullable
    @Setter
    private String makerBsqAddress;
    @Nullable
    @Setter
    private String makerBtcAddress;
    @Setter
    private List<RawTransactionInput> rawTakerBsqInputs = new ArrayList<>();
    @Setter
    private List<RawTransactionInput> rawTakerBtcInputs = new ArrayList<>();
    @Setter
    private List<TransactionInput> makerBsqInputs = new ArrayList<>();
    @Setter
    private List<TransactionInput> makerBtcInputs = new ArrayList<>();
    @Nullable
    @Setter
    private byte[] atomicTx;
    @Nullable
    @Setter
    private Transaction verifiedAtomicTx;

    public void initFromTrade(Trade trade) {
        var offer = trade.getOffer();
        checkNotNull(offer, "offer must not be null");
        if (trade.getTradeAmount() != null && trade.getTradeAmount().isPositive())
            bsqAmountFromVolume(trade.getTradeVolume()).ifPresent(this::setBsqTradeAmount);
        bsqAmountFromVolume(offer.getVolume()).ifPresent(this::setBsqMaxTradeAmount);
        bsqAmountFromVolume(offer.getMinVolume()).ifPresent(this::setBsqMinTradeAmount);
        // Atomic trades only allow fixed prices
        var price = offer.isUseMarketBasedPrice() ? 0 : Objects.requireNonNull(offer.getPrice()).getValue();
        setTradePrice(price);
        if (trade.getTradeAmount() != null && trade.getTradeAmount().isPositive())
            setBtcTradeAmount(trade.getTradeAmount().getValue());
        setBtcMaxTradeAmount(offer.getAmount().getValue());
        setBtcMinTradeAmount(offer.getMinAmount().getValue());
        setBsqTradeFee(trade.isCurrencyForTakerFeeBtc() ? 0 : trade.getTakerFeeAsLong());
        setBtcTradeFee(trade.isCurrencyForTakerFeeBtc() ? trade.getTakerFeeAsLong() : 0);
    }

    public void updateFromCreateAtomicTxRequest(CreateAtomicTxRequest message) {
        setTakerBsqOutputAmount(message.getTakerBsqOutputValue());
        setTakerBtcOutputAmount(message.getTakerBtcOutputValue());
        setTakerBsqAddress(message.getTakerBsqOutputAddress());
        setTakerBtcAddress(message.getTakerBtcOutputAddress());
        setRawTakerBsqInputs(message.getTakerBsqInputs());
        setRawTakerBtcInputs(message.getTakerBtcInputs());
        setBsqTradeAmount(message.getBsqTradeAmount());
        setBtcTradeAmount(message.getBtcTradeAmount());
    }

    public Optional<Long> bsqAmountFromVolume(Volume volume) {
        // The Altcoin class have the smallest unit set to 8 decimals, BSQ has the smallest unit at 2 decimals.
        return volume == null ? Optional.empty() : Optional.of((volume.getMonetary().getValue() + 500_000) / 1_000_000);
    }
}
