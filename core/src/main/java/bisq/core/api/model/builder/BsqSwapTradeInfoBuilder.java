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

package bisq.core.api.model.builder;

import bisq.core.api.model.BsqSwapTradeInfo;

import lombok.Getter;

/**
 * Proto wrapper for BSQ swap protocol details not common to Bisq v1
 * trade protocol details.
 *
 * This builder helps avoid bungling use of a large BsqSwapTradeInfo constructor
 * argument list.  If consecutive argument values of the same type are not
 * ordered correctly, the compiler won't complain but the resulting bugs could
 * be hard to find and fix.
 */
@Getter
public final class BsqSwapTradeInfoBuilder {

    private String txId;
    private long bsqTradeAmount;
    private long btcTradeAmount;
    private long bsqMakerTradeFee;
    private long bsqTakerTradeFee;
    private long txFeePerVbyte;
    private String makerBsqAddress;
    private String makerBtcAddress;
    private String takerBsqAddress;
    private String takerBtcAddress;
    private long numConfirmations;
    private String errorMessage;
    private long payout;
    private long swapPeerPayout;

    public BsqSwapTradeInfoBuilder withTxId(String txId) {
        this.txId = txId;
        return this;
    }

    public BsqSwapTradeInfoBuilder withBsqTradeAmount(long bsqTradeAmount) {
        this.bsqTradeAmount = bsqTradeAmount;
        return this;
    }

    public BsqSwapTradeInfoBuilder withBtcTradeAmount(long btcTradeAmount) {
        this.btcTradeAmount = btcTradeAmount;
        return this;
    }

    public BsqSwapTradeInfoBuilder withBsqMakerTradeFee(long bsqMakerTradeFee) {
        this.bsqMakerTradeFee = bsqMakerTradeFee;
        return this;
    }

    public BsqSwapTradeInfoBuilder withBsqTakerTradeFee(long bsqTakerTradeFee) {
        this.bsqTakerTradeFee = bsqTakerTradeFee;
        return this;
    }

    public BsqSwapTradeInfoBuilder withTxFeePerVbyte(long txFeePerVbyte) {
        this.txFeePerVbyte = txFeePerVbyte;
        return this;
    }

    public BsqSwapTradeInfoBuilder withMakerBsqAddress(String makerBsqAddress) {
        this.makerBsqAddress = makerBsqAddress;
        return this;
    }

    public BsqSwapTradeInfoBuilder withMakerBtcAddress(String makerBtcAddress) {
        this.makerBtcAddress = makerBtcAddress;
        return this;
    }

    public BsqSwapTradeInfoBuilder withTakerBsqAddress(String takerBsqAddress) {
        this.takerBsqAddress = takerBsqAddress;
        return this;
    }

    public BsqSwapTradeInfoBuilder withTakerBtcAddress(String takerBtcAddress) {
        this.takerBtcAddress = takerBtcAddress;
        return this;
    }

    public BsqSwapTradeInfoBuilder withNumConfirmations(long numConfirmations) {
        this.numConfirmations = numConfirmations;
        return this;
    }

    public BsqSwapTradeInfoBuilder withErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public BsqSwapTradeInfoBuilder withPayout(long payout) {
        this.payout = payout;
        return this;
    }

    public BsqSwapTradeInfoBuilder withSwapPeerPayout(long swapPeerPayout) {
        this.swapPeerPayout = swapPeerPayout;
        return this;
    }

    public BsqSwapTradeInfo build() {
        return new BsqSwapTradeInfo(this);
    }
}

