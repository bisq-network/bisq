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

package bisq.core.offer.bsq_swap;

import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.bsq_swap.BsqSwapCalculation;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;

import javafx.beans.InvalidationListener;

import java.util.Objects;

import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

/**
 * Wrapper for OpenOffer listening for txFee and wallet changes.
 * After a change event we recalculate the required funds and compare it with the available
 * wallet funds. If not enough funds we set the bsqSwapOfferHasMissingFunds flag at
 * openOffer and call the disableBsqSwapOffer at bsqSwapOpenOfferService.
 * If we have been in the disabled state and we have now sufficient funds we call the
 * enableBsqSwapOffer at bsqSwapOpenOfferService and update the
 * bsqSwapOfferHasMissingFunds.
 */
@Slf4j
class OpenBsqSwapOffer {
    @Getter
    @Delegate
    private final OpenOffer openOffer;

    private final OpenBsqSwapOfferService openBsqSwapOfferService;
    private final FeeService feeService;
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;

    private final long tradeFee;
    private final boolean isBuyOffer;
    private final InvalidationListener feeChangeListener;
    private final BsqBalanceListener bsqBalanceListener;
    private final WalletChangeEventListener btcWalletChangeEventListener;
    private final Coin btcAmount;
    private final Coin requiredBsqInput;

    // Mutable data
    private long txFeePerVbyte;
    private Coin walletBalance;
    private boolean hasMissingFunds;

    public OpenBsqSwapOffer(OpenOffer openOffer,
                            OpenBsqSwapOfferService openBsqSwapOfferService,
                            FeeService feeService,
                            BtcWalletService btcWalletService,
                            BsqWalletService bsqWalletService) {
        this.openOffer = openOffer;
        this.openBsqSwapOfferService = openBsqSwapOfferService;
        this.feeService = feeService;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;

        Offer offer = openOffer.getOffer();
        isBuyOffer = offer.isBuyOffer();
        tradeFee = offer.getMakerFee().getValue();

        txFeePerVbyte = feeService.getTxFeePerVbyte().getValue();
        feeChangeListener = observable -> {
            long newTxFeePerVbyte = feeService.getTxFeePerVbyte().value;
            if (newTxFeePerVbyte != this.txFeePerVbyte) {
                this.txFeePerVbyte = newTxFeePerVbyte;
                evaluateFundedState();
                log.info("Updated because of fee change. txFeePerVbyte={}, hasMissingFunds={}",
                        txFeePerVbyte, hasMissingFunds);

            }
        };
        feeService.feeUpdateCounterProperty().addListener(feeChangeListener);

        if (isBuyOffer) {

            Coin bsqAmount = BsqSwapCalculation.getBsqTradeAmount(Objects.requireNonNull(offer.getVolume()));
            requiredBsqInput = BsqSwapCalculation.getBuyersBsqInputValue(bsqAmount.getValue(), tradeFee);
            walletBalance = bsqWalletService.getVerifiedBalance();
            bsqBalanceListener = (availableBalance,
                                  availableNonBsqBalance,
                                  unverifiedBalance,
                                  unconfirmedChangeBalance,
                                  lockedForVotingBalance,
                                  lockedInBondsBalance,
                                  unlockingBondsBalance) -> {
                if (!walletBalance.equals(availableBalance)) {
                    walletBalance = bsqWalletService.getVerifiedBalance();
                    evaluateFundedState();
                    applyFundingState();
                    log.info("Updated because of BSQ wallet balance change. walletBalance={}, hasMissingFunds={}",
                            walletBalance, hasMissingFunds);
                }
            };
            bsqWalletService.addBsqBalanceListener(bsqBalanceListener);
            btcWalletChangeEventListener = null;
            btcAmount = null;
        } else {
            btcAmount = offer.getAmount();
            walletBalance = btcWalletService.getSavingWalletBalance();
            btcWalletChangeEventListener = wallet -> {
                Coin newBalance = btcWalletService.getSavingWalletBalance();
                if (!this.walletBalance.equals(newBalance)) {
                    this.walletBalance = newBalance;
                    evaluateFundedState();
                    applyFundingState();
                    log.info("Updated because of BTC wallet balance change. walletBalance={}, hasMissingFunds={}",
                            walletBalance, hasMissingFunds);
                }
            };
            btcWalletService.addChangeEventListener(btcWalletChangeEventListener);
            bsqBalanceListener = null;
            requiredBsqInput = null;
        }

        // We might need to reset the state
        if (openOffer.isBsqSwapOfferHasMissingFunds()) {
            openOffer.setState(OpenOffer.State.AVAILABLE);
            openBsqSwapOfferService.requestPersistence();
        }

        evaluateFundedState();
        applyFundingState();
    }

    public void removeListeners() {
        feeService.feeUpdateCounterProperty().removeListener(feeChangeListener);
        if (isBuyOffer) {
            bsqWalletService.removeBsqBalanceListener(bsqBalanceListener);
        } else {
            btcWalletService.removeChangeEventListener(btcWalletChangeEventListener);
        }
    }

    // We apply the
    public void applyFundingState() {
        boolean prev = openOffer.isBsqSwapOfferHasMissingFunds();
        if (hasMissingFunds && !prev) {
            openOffer.setBsqSwapOfferHasMissingFunds(true);
            openBsqSwapOfferService.requestPersistence();

            if (!isDeactivated()) {
                openBsqSwapOfferService.disableBsqSwapOffer(getOpenOffer());
            }

        } else if (!hasMissingFunds && prev) {
            openOffer.setBsqSwapOfferHasMissingFunds(false);
            openBsqSwapOfferService.requestPersistence();

            if (!isDeactivated()) {
                openBsqSwapOfferService.enableBsqSwapOffer(getOpenOffer());
            }
        }
    }

    private void evaluateFundedState() {
        if (isBuyOffer) {
            hasMissingFunds = walletBalance.isLessThan(requiredBsqInput);
        } else {
            try {
                Coin requiredInput = BsqSwapCalculation.getSellersBtcInputValue(btcWalletService,
                        btcAmount,
                        txFeePerVbyte,
                        tradeFee);
                hasMissingFunds = walletBalance.isLessThan(requiredInput);
            } catch (InsufficientMoneyException e) {
                hasMissingFunds = true;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenBsqSwapOffer that = (OpenBsqSwapOffer) o;
        return tradeFee == that.tradeFee && isBuyOffer == that.isBuyOffer && openOffer.equals(that.openOffer) &&
                btcAmount.equals(that.btcAmount) && requiredBsqInput.equals(that.requiredBsqInput);
    }

    @Override
    public int hashCode() {
        return Objects.hash(openOffer, tradeFee, isBuyOffer, btcAmount, requiredBsqInput);
    }
}
