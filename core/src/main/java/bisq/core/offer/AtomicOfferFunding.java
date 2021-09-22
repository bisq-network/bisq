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

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.atomic.AtomicTxBuilder;
import bisq.core.util.coin.CoinUtil;

import bisq.common.crypto.KeyRing;

import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AtomicOfferFunding implements WalletChangeEventListener {

    public interface Listener {
        void isFunded(boolean funded);

        Offer getOffer();
    }

    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final TradeWalletService tradeWalletService;
    private final FeeService feeService;
    private final KeyRing keyRing;
    private final List<Listener> listeners = new ArrayList<>();

    @Inject
    public AtomicOfferFunding(BtcWalletService btcWalletService,
                              BsqWalletService bsqWalletService,
                              TradeWalletService tradeWalletService,
                              FeeService feeService,
                              KeyRing keyRing) {
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.tradeWalletService = tradeWalletService;
        this.feeService = feeService;
        this.keyRing = keyRing;

    }

    public void onAllServicesInitialized() {
        btcWalletService.addChangeEventListener(this);
        bsqWalletService.addChangeEventListener(this);
    }

    public void shutDown() {
        btcWalletService.removeChangeEventListener(this);
        bsqWalletService.removeChangeEventListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListener(Listener listener) {
        if (!listeners.contains(listener) && listener.getOffer().isAtomicOffer()) {
            listeners.add(listener);
        }
    }

    // Remove listeners by Offer
    public void removeListener(Offer offer) {
        var toRemove = listeners.stream()
                .filter(listener -> listener.getOffer().equals(offer))
                .collect(Collectors.toList());
        toRemove.forEach(listeners::remove);
    }

    public boolean isFunded(Offer offer) {
        if (!feeService.isFeeAvailable())
            return false;

        var isMaker = offer.isMyOffer(keyRing);
        var isBuyer = isMaker == offer.isBuyOffer();
        var price = offer.getPrice();
        var btcAmount = offer.getAmount();
        var atomicTxBuilder = new AtomicTxBuilder(bsqWalletService,
                tradeWalletService,
                isBuyer,
                price,
                btcAmount,
                feeService.getTxFeePerVbyte(),
                btcWalletService.getFreshAddressEntry().getAddressString(),
                bsqWalletService.getUnusedAddress().toString());

        if (isMaker) {
            atomicTxBuilder.setMyTradeFee(CoinUtil.getMakerFee(false, btcAmount));
            atomicTxBuilder.setPeerTradeFee(CoinUtil.getTakerFee(false, btcAmount));
        } else {
            atomicTxBuilder.setMyTradeFee(CoinUtil.getTakerFee(false, btcAmount));
            atomicTxBuilder.setPeerTradeFee(CoinUtil.getMakerFee(false, btcAmount));
        }
        return atomicTxBuilder.buildMySide(0, null, !isMaker) != null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // WalletChangeEventListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onWalletChanged(Wallet wallet) {
        update();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void update() {
        listeners.forEach(this::isFunded);
    }

    private void isFunded(Listener listener) {
        feeService.requestFees(() -> listener.isFunded(isFunded(listener.getOffer())),
                (String errorMessage, Throwable throwable) -> {
                    log.warn("Fee request failed, unable to calculate atomic funding status");
                    if (throwable != null) {
                        log.warn("Exception: {}", throwable.getMessage());
                    }
                });
    }

//    private void checkFunding(Listener listener) {
//        var offer = listener.getOffer();
//        var isMaker = offer.isMyOffer(keyRing);
//        var isBuyer = isMaker == offer.isBuyOffer();
//        var price = offer.getPrice();
//        var btcAmount = offer.getAmount();
//        var atomicTxBuilder = new AtomicTxBuilder(bsqWalletService,
//                tradeWalletService,
//                isBuyer,
//                price,
//                btcAmount,
//                Coin.ZERO,
//                btcWalletService.getFreshAddressEntry().getAddressString(),
//                bsqWalletService.getUnusedAddress().toString());
//        feeService.requestFees(() -> {
//                    atomicTxBuilder.setTxFeePerVbyte(feeService.getTxFeePerVbyte());
//                    if (isMaker) {
//                        atomicTxBuilder.setMyTradeFee(CoinUtil.getMakerFee(false, btcAmount));
//                        atomicTxBuilder.setPeerTradeFee(CoinUtil.getTakerFee(false, btcAmount));
//                    } else {
//                        atomicTxBuilder.setMyTradeFee(CoinUtil.getTakerFee(false, btcAmount));
//                        atomicTxBuilder.setPeerTradeFee(CoinUtil.getMakerFee(false, btcAmount));
//                    }
//                    var funded = atomicTxBuilder.buildMySide(0, null, !isMaker) != null;
//                    listener.funded(funded);
//                },
//                (String errorMessage, Throwable throwable) -> {
//                    log.warn("Fee request failed, unable to calculate atomic funding status");
//                    if (throwable != null) {
//                        log.warn("Exception: {}", throwable.getMessage());
//                    }
//                });
//    }

}
