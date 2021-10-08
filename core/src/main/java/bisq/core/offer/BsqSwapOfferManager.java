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

package bisq.core.offer;

import bisq.core.btc.BsqSwapTxHelper;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.provider.fee.FeeService;
import bisq.core.util.coin.CoinUtil;

import bisq.common.UserThread;
import bisq.common.crypto.KeyRing;

import org.bitcoinj.core.Coin;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;

import javax.inject.Inject;

import javafx.collections.ListChangeListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BsqSwapOfferManager implements WalletChangeEventListener {

    private OpenOfferManager openOfferManager;
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final TradeWalletService tradeWalletService;
    private final FeeService feeService;
    private final KeyRing keyRing;
    private final Set<OpenOffer> openBsqSwapOffers = new HashSet<>();
    private final ListChangeListener<OpenOffer> listChangeListener;
    private int tryFeeServiceCounter = 0;

    @Inject
    public BsqSwapOfferManager(OpenOfferManager openOfferManager,
                               BtcWalletService btcWalletService,
                               BsqWalletService bsqWalletService,
                               TradeWalletService tradeWalletService,
                               FeeService feeService,
                               KeyRing keyRing) {
        this.openOfferManager = openOfferManager;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.tradeWalletService = tradeWalletService;
        this.feeService = feeService;
        this.keyRing = keyRing;

        listChangeListener = c -> {
            c.next();
            if (c.wasAdded()) {
                onOpenOffersAdded(c.getAddedSubList());
            } else if (c.wasRemoved()) {
                onOpenOffersRemoved(c.getRemoved());
            }
        };
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        btcWalletService.addChangeEventListener(this);
        bsqWalletService.addChangeEventListener(this);
        openOfferManager.getObservableList().addListener(listChangeListener);
        onOpenOffersAdded(openOfferManager.getObservableList());
    }

    public void shutDown() {
        btcWalletService.removeChangeEventListener(this);
        bsqWalletService.removeChangeEventListener(this);
        openOfferManager.getObservableList().removeListener(listChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // WalletChangeEventListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onWalletChanged(Wallet wallet) {
        openBsqSwapOffers.forEach(this::updateFundingState);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onOpenOffersAdded(List<? extends OpenOffer> list) {
        list.stream().filter(openOffer -> openOffer.getOffer().isBsqSwapOffer())
                .forEach(openOffer -> {
                    openBsqSwapOffers.add(openOffer);
                    updateFundingState(openOffer);
                });
    }

    private void onOpenOffersRemoved(List<? extends OpenOffer> list) {
        list.stream().filter(openOffer -> openOffer.getOffer().isBsqSwapOffer())
                .forEach(openBsqSwapOffers::remove);
    }

    private void updateFundingState(OpenOffer openOffer) {
        if (!feeService.isFeeAvailable() && tryFeeServiceCounter < 10) {
            // At startup the fee service might not be available still so we call again after a delay
            UserThread.runAfter(() -> {
                tryFeeServiceCounter++;
                if (openBsqSwapOffers.contains(openOffer)) {
                    updateFundingState(openOffer);
                }
            }, 10);
            return;
        }

        // We deactivate if the wallet update results in an offer not funded sufficiently anymore
        // Re-activation has to be done by the user. The deactivation should be communicated with a popup in the UI.
        if (!openOffer.isDeactivated() && !isFunded(openOffer.getOffer())) {
            openOfferManager.deactivateOpenOffer(openOffer,
                    () -> log.info("Deactivated open offer {}", openOffer.getShortId()),
                    errorMessage -> log.warn("Failed to deactivate open offer {}", openOffer.getShortId()));
        }
    }

    private boolean isFunded(Offer offer) {
        var isMaker = offer.isMyOffer(keyRing);
        var isBuyer = isMaker == offer.isBuyOffer();
        var price = offer.getPrice();
        var btcAmount = offer.getAmount();
        Coin makerFee = CoinUtil.getMakerFee(false, btcAmount);
        Coin takerFee = CoinUtil.getTakerFee(false, btcAmount);
        var bsqSwapTxHelper = new BsqSwapTxHelper(bsqWalletService,
                tradeWalletService,
                isBuyer,
                price,
                btcAmount,
                feeService.getTxFeePerVbyte(),
                btcWalletService.getFreshAddressEntry().getAddressString(),
                bsqWalletService.getUnusedAddress().toString());
        if (isMaker) {
            bsqSwapTxHelper.setMyTradeFee(makerFee);
            bsqSwapTxHelper.setPeerTradeFee(takerFee);
        } else {
            bsqSwapTxHelper.setMyTradeFee(takerFee);
            bsqSwapTxHelper.setPeerTradeFee(makerFee);
        }
        return bsqSwapTxHelper.buildMySide(0, null, !isMaker) != null;
    }
}
