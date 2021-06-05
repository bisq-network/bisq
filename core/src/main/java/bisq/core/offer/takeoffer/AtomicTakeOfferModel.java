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

package bisq.core.offer.takeoffer;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.param.Param;
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
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.TradeManager;
import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.atomic.AtomicTxBuilder;
import bisq.core.trade.handlers.TradeResultHandler;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.coin.CoinUtil;

import bisq.network.p2p.P2PService;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.taskrunner.Model;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Set;

import lombok.Getter;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class AtomicTakeOfferModel implements Model {
    private final OfferUtil offerUtil;
    @Getter
    private final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    @Getter
    private final TradeManager tradeManager;
    //    private final OfferBook offerBook;
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final TradeWalletService tradeWalletService;
    private final User user;
    private final FeeService feeService;
    private final FilterManager filterManager;
    final Preferences preferences;
    private final AccountAgeWitnessService accountAgeWitnessService;
    //    private final Navigation navigation;
    private final P2PService p2PService;
    private final DaoFacade daoFacade;

    private Offer offer;

    private final ObjectProperty<Coin> amount = new SimpleObjectProperty<>();
    @Getter
    final ObjectProperty<Volume> volume = new SimpleObjectProperty<>();
    @Getter
    final BooleanProperty isTxBuilderReady = new SimpleBooleanProperty();


    private PaymentAccount paymentAccount;
    @Getter
    Price tradePrice;
    @Getter
    private AtomicTxBuilder atomicTxBuilder;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    AtomicTakeOfferModel(TradeManager tradeManager,
                         OfferUtil offerUtil,
                         BtcWalletService btcWalletService,
                         BsqWalletService bsqWalletService,
                         TradeWalletService tradeWalletService,
                         User user,
                         FeeService feeService,
                         FilterManager filterManager,
                         Preferences preferences,
                         AccountAgeWitnessService accountAgeWitnessService,
                         P2PService p2PService,
                         DaoFacade daoFacade
    ) {
        this.offerUtil = offerUtil;
        this.tradeManager = tradeManager;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.tradeWalletService = tradeWalletService;
        this.user = user;
        this.feeService = feeService;
        this.filterManager = filterManager;
        this.preferences = preferences;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.p2PService = p2PService;
        this.daoFacade = daoFacade;
    }

    public void activate(ErrorMessageHandler errorMessageHandler) {
        // when leaving screen we reset state
        offer.setState(Offer.State.UNKNOWN);
        addListeners();

        tradeManager.checkOfferAvailability(offer,
                false,
                () -> {
                },
                errorMessageHandler);
    }

    public void deactivate() {
        removeListeners();
        if (offer != null) {
            offer.cancelAvailabilityRequest();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // called before activate
    public void initWithData(Offer offer) {
        this.offer = offer;

        tradePrice = offer.getPrice();
        paymentAccount = getPaymentAccount();
        checkNotNull(paymentAccount, "PaymentAccount must not be null");

        this.amount.set(Coin.valueOf(Math.min(offer.getAmount().value, getMaxTradeLimit())));
        atomicTxBuilder = new AtomicTxBuilder(feeService,
                btcWalletService,
                bsqWalletService,
                tradeWalletService,
                offer.getDirection() == OfferPayloadI.Direction.SELL,
                false,
                offer.getPrice(),
                amount.getValue(),
                Coin.ZERO,
                btcWalletService.getFreshAddressEntry().getAddressString(),
                bsqWalletService.getUnusedAddress().toString(),
                daoFacade.getParamValue(Param.RECIPIENT_BTC_ADDRESS));
        feeService.requestFees(() -> atomicTxBuilder.setTxFeePerVbyte(feeService.getTxFeePerVbyte()));

        calculateVolume();

        offer.resetState();
    }

    @Override
    public void onComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onTakeOffer(TradeResultHandler<AtomicTrade> tradeResultHandler,
                            ErrorMessageHandler warningHandler,
                            ErrorMessageHandler errorHandler) {
        checkArgument(atomicTxBuilder.getCanBuildMySide().get(), "Missing data to create transaction");

        if (filterManager.isCurrencyBanned(offer.getCurrencyCode())) {
            warningHandler.handleErrorMessage(Res.get("offerbook.warning.currencyBanned"));
        } else if (filterManager.isPaymentMethodBanned(offer.getPaymentMethod())) {
            warningHandler.handleErrorMessage(Res.get("offerbook.warning.paymentMethodBanned"));
        } else if (filterManager.isOfferIdBanned(offer.getId())) {
            warningHandler.handleErrorMessage(Res.get("offerbook.warning.offerBlocked"));
        } else if (filterManager.isNodeAddressBanned(offer.getMakerNodeAddress())) {
            warningHandler.handleErrorMessage(Res.get("offerbook.warning.nodeBlocked"));
        } else if (filterManager.requireUpdateToNewVersionForTrading()) {
            warningHandler.handleErrorMessage(Res.get("offerbook.warning.requireUpdateToNewVersion"));
        } else if (tradeManager.wasOfferAlreadyUsedInTrade(offer.getId())) {
            warningHandler.handleErrorMessage(Res.get("offerbook.warning.offerWasAlreadyUsedInTrade"));
        } else {
            tradeManager.onTakeAtomicOffer(offer,
                    amount.get(),
                    tradePrice.getValue(),
                    feeService.getTxFeePerVbyte().getValue(),
                    offer.isCurrencyForMakerFeeBtc(),
                    isCurrencyForTakerFeeBtc(),
                    getMakerFee(offer.isCurrencyForMakerFeeBtc()).getValue(),
                    getTakerFee().getValue(),
                    false,
                    tradeResultHandler,
                    errorHandler
            );
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OfferPayload.Direction getDirection() {
        return offer.getDirection();
    }

    public Offer getOffer() {
        return offer;
    }

    public PaymentAccount getPaymentAccount() {
        Set<PaymentAccount> paymentAccounts = user.getPaymentAccounts();
        checkNotNull(paymentAccounts, "paymentAccounts must not be null");
        return paymentAccounts.stream()
                .filter(p -> p.getPaymentMethod().isAtomic())
                .findAny()
                .orElse(null);
    }

    public long getMaxTradeLimit() {
        if (paymentAccount != null) {
            return accountAgeWitnessService.getMyTradeLimit(paymentAccount, getCurrencyCode(),
                    offer.getMirroredDirection());
        } else {
            return 0;
        }
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

    public void calculateVolume() {
        if (tradePrice != null && offer != null &&
                amount.get() != null &&
                !amount.get().isZero()) {
            Volume volumeByAmount = tradePrice.getVolumeByAmount(amount.get());

            volume.set(volumeByAmount);
        }
    }

    public void applyAmount(Coin amount) {
        this.amount.set(Coin.valueOf(Math.min(amount.value, getMaxTradeLimit())));
        atomicTxBuilder.setBtcAmount(amount);
    }

    public Coin getTakerFee(boolean isCurrencyForTakerFeeBtc) {
        var fee = CoinUtil.getTakerFee(isCurrencyForTakerFeeBtc, this.amount.get());
        atomicTxBuilder.setMyTradeFee(isCurrencyForTakerFeeBtc, fee);

        return fee != null ? fee : Coin.ZERO;
    }

    public Coin getTakerFee() {
        return getTakerFee(isCurrencyForTakerFeeBtc());
    }

    public Coin getMakerFee(boolean isCurrencyForMakerFeeBtc) {
        return CoinUtil.getMakerFee(isCurrencyForMakerFeeBtc, this.amount.get());
    }

    public boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (offer != null && amount.get() != null)
            return !offer.getMinAmount().isGreaterThan(amount.get());
        return true;
    }

    public boolean isAmountLargerThanOfferAmount() {
        //noinspection SimplifiableIfStatement
        if (amount.get() != null && offer != null)
            return amount.get().isGreaterThan(offer.getAmount());
        return true;
    }

    public ReadOnlyObjectProperty<Coin> getAmount() {
        return amount;
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
        Coin usableBsqBalance = bsqWalletService.getAvailableConfirmedBalance().subtract(
                Restrictions.getMinNonDustOutput());
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

    public Coin getTakerFeeInBtc() {
        return offerUtil.getTakerFee(true, amount.get());
    }

    public Coin getTakerFeeInBsq() {
        return offerUtil.getTakerFee(false, amount.get());
    }

    public boolean isTakerFeeValid() {
        return preferences.getPayFeeInBtc() || offerUtil.isBsqForTakerFeeAvailable(amount.get());
    }

    public boolean hasEnoughBtc() {
        return !btcWalletService.getSavingWalletBalance().isLessThan(atomicTxBuilder.myBtc.get());
    }

    public boolean hasEnoughBsq() {
        return !offerUtil.getUsableBsqBalance().isLessThan(atomicTxBuilder.myBsq.get());
    }
}
