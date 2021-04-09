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

package bisq.desktop.main.offer.atomictakeoffer;

import bisq.desktop.Navigation;
import bisq.desktop.common.model.ActivatableDataModel;
import bisq.desktop.main.offer.offerbook.OfferBook;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferPayloadI;
import bisq.core.offer.OfferUtil;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.TradeManager;
import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.atomic.AtomicTxBuilder;
import bisq.core.trade.handlers.TradeResultHandler;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.coin.CoinUtil;

import bisq.network.p2p.P2PService;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Set;

import lombok.Getter;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
class AtomicTakeOfferDataModel extends ActivatableDataModel {
    private final OfferUtil offerUtil;
    @Getter
    private final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    private final TradeManager tradeManager;
    private final OfferBook offerBook;
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final User user;
    private final FeeService feeService;
    private final FilterManager filterManager;
    final Preferences preferences;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final Navigation navigation;
    private final P2PService p2PService;

    private Offer offer;

    private final ObjectProperty<Coin> amount = new SimpleObjectProperty<>();
    final ObjectProperty<Volume> volume = new SimpleObjectProperty<>();
    final BooleanProperty isTxBuilderReady = new SimpleBooleanProperty();


    private PaymentAccount paymentAccount;
    Price tradePrice;
    private AtomicTxBuilder atomicTxBuilder;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    AtomicTakeOfferDataModel(TradeManager tradeManager,
                             OfferBook offerBook,
                             OfferUtil offerUtil,
                             BtcWalletService btcWalletService,
                             BsqWalletService bsqWalletService,
                             User user,
                             FeeService feeService,
                             FilterManager filterManager,
                             Preferences preferences,
                             AccountAgeWitnessService accountAgeWitnessService,
                             Navigation navigation,
                             P2PService p2PService
    ) {
        this.offerUtil = offerUtil;
        this.tradeManager = tradeManager;
        this.offerBook = offerBook;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.user = user;
        this.feeService = feeService;
        this.filterManager = filterManager;
        this.preferences = preferences;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.navigation = navigation;
        this.p2PService = p2PService;
    }

    @Override
    protected void activate() {
        // when leaving screen we reset state
        offer.setState(Offer.State.UNKNOWN);
        addListeners();

        if (canTakeOffer()) {
            tradeManager.checkOfferAvailability(offer,
                    false,
                    () -> {
                    },
                    errorMessage -> new Popup().warning(errorMessage).show());
        }
    }

    @Override
    protected void deactivate() {
        removeListeners();
        if (offer != null) {
            offer.cancelAvailabilityRequest();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // called before activate
    void initWithData(Offer offer) {
        this.offer = offer;

        tradePrice = offer.getPrice();
        paymentAccount = getPaymentAccount();
        checkNotNull(paymentAccount, "PaymentAccount must not be null");

        this.amount.set(Coin.valueOf(Math.min(offer.getAmount().value, getMaxTradeLimit())));

        atomicTxBuilder = new AtomicTxBuilder(feeService,
                offer.getDirection() == OfferPayloadI.Direction.SELL,
                false);
        atomicTxBuilder.setPrice(offer.getPrice());
        atomicTxBuilder.setBtcAmount(amount.getValue());

        calculateVolume();

        offer.resetState();
    }

    public void onClose(boolean removeOffer) {
        // We do not wait until the offer got removed by a network remove message but remove it
        // directly from the offer book. The broadcast gets now bundled and has 2 sec. delay so the
        // removal from the network is a bit slower as it has been before. To avoid that the taker gets
        // confused to see the same offer still in the offerbook we remove it manually. This removal has
        // only local effect. Other trader might see the offer for a few seconds
        // still (but cannot take it).
        if (removeOffer) {
            offerBook.removeOffer(checkNotNull(offer));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    // errorMessageHandler is used only in the check availability phase. As soon we have a trade we write the error msg in the trade object as we want to
    // have it persisted as well.
    void onTakeOffer(TradeResultHandler<AtomicTrade> tradeResultHandler) {
        checkArgument(atomicTxBuilder.getCanBuildMySide().get(), "Missing data to create transaction");

        if (filterManager.isCurrencyBanned(offer.getCurrencyCode())) {
            new Popup().warning(Res.get("offerbook.warning.currencyBanned")).show();
        } else if (filterManager.isPaymentMethodBanned(offer.getPaymentMethod())) {
            new Popup().warning(Res.get("offerbook.warning.paymentMethodBanned")).show();
        } else if (filterManager.isOfferIdBanned(offer.getId())) {
            new Popup().warning(Res.get("offerbook.warning.offerBlocked")).show();
        } else if (filterManager.isNodeAddressBanned(offer.getMakerNodeAddress())) {
            new Popup().warning(Res.get("offerbook.warning.nodeBlocked")).show();
        } else if (filterManager.requireUpdateToNewVersionForTrading()) {
            new Popup().warning(Res.get("offerbook.warning.requireUpdateToNewVersion")).show();
        } else if (tradeManager.wasOfferAlreadyUsedInTrade(offer.getId())) {
            new Popup().warning(Res.get("offerbook.warning.offerWasAlreadyUsedInTrade")).show();
        } else {
            // TODO(sq): Add takeAtomicOffer
//            tradeManager.onTakeOffer(amount.get(),
//                    txFeeFromFeeService,
//                    getTakerFee(),
//                    isCurrencyForTakerFeeBtc(),
//                    tradePrice.getValue(),
//                    fundsNeededForTrade,
//                    offer,
//                    paymentAccount.getId(),
//                    useSavingsWallet,
//                    false,
//                    tradeResultHandler,
//                    errorMessage -> {
//                        log.warn(errorMessage);
//                        new Popup().warning(errorMessage).show();
//                    }
//            );
            new Popup().information("Offer Taken").show();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    OfferPayload.Direction getDirection() {
        return offer.getDirection();
    }

    public Offer getOffer() {
        return offer;
    }

    PaymentAccount getPaymentAccount() {
        Set<PaymentAccount> paymentAccounts = user.getPaymentAccounts();
        checkNotNull(paymentAccounts, "paymentAccounts must not be null");
        return paymentAccounts.stream()
                .filter(p -> p.getPaymentMethod().isAtomic())
                .findAny()
                .orElse(null);
    }

    long getMaxTradeLimit() {
        if (paymentAccount != null) {
            return accountAgeWitnessService.getMyTradeLimit(paymentAccount, getCurrencyCode(),
                    offer.getMirroredDirection());
        } else {
            return 0;
        }
    }

    boolean canTakeOffer() {
        return GUIUtil.canCreateOrTakeOfferOrShowPopup(user, navigation) &&
                GUIUtil.isBootstrappedOrShowPopup(p2PService);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addListeners() {
        isTxBuilderReady.bind(atomicTxBuilder.getCanBuildMySide());
    }

    private void removeListeners() {
        isTxBuilderReady.unbind();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    void calculateVolume() {
        if (tradePrice != null && offer != null &&
                amount.get() != null &&
                !amount.get().isZero()) {
            Volume volumeByAmount = tradePrice.getVolumeByAmount(amount.get());

            volume.set(volumeByAmount);
        }
    }

    void applyAmount(Coin amount) {
        this.amount.set(Coin.valueOf(Math.min(amount.value, getMaxTradeLimit())));
        atomicTxBuilder.setBtcAmount(amount);
    }

    @Nullable
    Coin getTakerFee(boolean isCurrencyForTakerFeeBtc) {
        Coin amount = this.amount.get();
        if (amount != null) {
            Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getTakerFeePerBtc(isCurrencyForTakerFeeBtc), amount);
            var fee = CoinUtil.maxCoin(feePerBtc, FeeService.getMinTakerFee(isCurrencyForTakerFeeBtc));

            atomicTxBuilder.setMyTradeFee(isCurrencyForTakerFeeBtc, fee);
            return atomicTxBuilder.getMyTradeFee();
        } else {
            return null;
        }
    }

    @Nullable
    public Coin getTakerFee() {
        return getTakerFee(isCurrencyForTakerFeeBtc());
    }

    boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (offer != null && amount.get() != null)
            return !offer.getMinAmount().isGreaterThan(amount.get());
        return true;
    }

    boolean isAmountLargerThanOfferAmount() {
        //noinspection SimplifiableIfStatement
        if (amount.get() != null && offer != null)
            return amount.get().isGreaterThan(offer.getAmount());
        return true;
    }

    ReadOnlyObjectProperty<Coin> getAmount() {
        return amount;
    }

    public PaymentMethod getPaymentMethod() {
        return offer.getPaymentMethod();
    }

    public String getCurrencyCode() {
        return offer.getCurrencyCode();
    }

    public String getCurrencyNameAndCode() {
        return CurrencyUtil.getNameByCode(offer.getCurrencyCode());
    }

    public Coin getUsableBsqBalance() {
        // we have to keep a minimum amount of BSQ == bitcoin dust limit
        // otherwise there would be dust violations for change UTXOs
        // essentially means the minimum usable balance of BSQ is 5.46
        Coin usableBsqBalance = bsqWalletService.getAvailableConfirmedBalance().subtract(Restrictions.getMinNonDustOutput());
        if (usableBsqBalance.isNegative())
            usableBsqBalance = Coin.ZERO;
        return usableBsqBalance;
    }

    public boolean isCurrencyForTakerFeeBtc() {
        return offerUtil.isCurrencyForTakerFeeBtc(amount.get());
    }

    public void setPreferredCurrencyForTakerFeeBtc(boolean isCurrencyForTakerFeeBtc) {
        preferences.setPayFeeInBtc(isCurrencyForTakerFeeBtc);
    }

    public boolean isPreferredFeeCurrencyBtc() {
        return preferences.isPayFeeInBtc();
    }

    public Coin getTakerFeeInBtc() {
        return offerUtil.getTakerFee(true, amount.get());
    }

    public Coin getTakerFeeInBsq() {
        return offerUtil.getTakerFee(false, amount.get());
    }

    boolean isTakerFeeValid() {
        return preferences.getPayFeeInBtc() || offerUtil.isBsqForTakerFeeAvailable(amount.get());
    }

    public boolean hasEnoughBtc() {
        return !btcWalletService.getSavingWalletBalance().isLessThan(atomicTxBuilder.myBtc.get());
    }

    public boolean hasEnoughBsq() {
        return !offerUtil.getUsableBsqBalance().isLessThan(atomicTxBuilder.myBsq.get());
    }
}
