package bisq.core.offer.takeoffer;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferUtil;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;

import bisq.common.taskrunner.Model;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import java.util.Objects;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import static bisq.core.btc.model.AddressEntry.Context.OFFER_FUNDING;
import static bisq.core.offer.OfferPayload.Direction.SELL;
import static bisq.core.util.VolumeUtil.getAdjustedVolumeForHalCash;
import static bisq.core.util.VolumeUtil.getRoundedFiatVolume;
import static bisq.core.util.coin.CoinUtil.minCoin;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TakeOfferModel implements Model {
    // Immutable
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final BtcWalletService btcWalletService;
    private final FeeService feeService;
    private final OfferUtil offerUtil;
    private final PriceFeedService priceFeedService;

    // Mutable
    @Getter
    private AddressEntry addressEntry;
    @Getter
    private Coin amount;
    @Getter
    private boolean isCurrencyForTakerFeeBtc;
    private Offer offer;
    private PaymentAccount paymentAccount;
    @Getter
    private Coin securityDeposit;
    private boolean useSavingsWallet;

    // 260 kb is typical trade fee tx size with 1 input, but trade tx (deposit + payout)
    // are larger so we adjust to 320.
    private final int feeTxSize = 320;
    private Coin txFeePerByteFromFeeService;
    @Getter
    private Coin txFeeFromFeeService;
    @Getter
    private Coin takerFee;
    @Getter
    private Coin totalToPayAsCoin;
    @Getter
    private Coin missingCoin = Coin.ZERO;
    @Getter
    private Coin totalAvailableBalance;
    @Getter
    private Coin balance;
    @Getter
    private boolean isBtcWalletFunded;
    @Getter
    private Volume volume;

    @Inject
    public TakeOfferModel(AccountAgeWitnessService accountAgeWitnessService,
                          BtcWalletService btcWalletService,
                          FeeService feeService,
                          OfferUtil offerUtil,
                          PriceFeedService priceFeedService) {
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.btcWalletService = btcWalletService;
        this.feeService = feeService;
        this.offerUtil = offerUtil;
        this.priceFeedService = priceFeedService;
    }

    public void initModel(Offer offer,
                          PaymentAccount paymentAccount,
                          boolean useSavingsWallet) {
        this.clearModel();
        this.offer = offer;
        this.paymentAccount = paymentAccount;
        this.addressEntry = btcWalletService.getOrCreateAddressEntry(offer.getId(), OFFER_FUNDING);
        validateModelInputs();

        this.useSavingsWallet = useSavingsWallet;
        this.amount = Coin.valueOf(Math.min(offer.getAmount().value, getMaxTradeLimit()));
        this.securityDeposit = offer.getDirection() == SELL
                ? offer.getBuyerSecurityDeposit()
                : offer.getSellerSecurityDeposit();
        this.isCurrencyForTakerFeeBtc = offerUtil.isCurrencyForTakerFeeBtc(amount);
        this.takerFee = offerUtil.getTakerFee(isCurrencyForTakerFeeBtc, amount);

        calculateTxFees();
        calculateVolume();
        calculateTotalToPay();
        offer.resetState();

        priceFeedService.setCurrencyCode(offer.getCurrencyCode());
    }

    @Override
    public void onComplete() {
        // empty
    }

    private void calculateTxFees() {
        // Taker pays 3 times the tx fee (taker fee, deposit, payout) because the mining fee might be different when maker created the offer
        // and reserved his funds. Taker creates at least taker fee and deposit tx at nearly the same moment. Just the payout will
        // be later and still could lead to issues if the required fee changed a lot in the meantime. using RBF and/or
        // multiple batch-signed payout tx with different fees might be an option but RBF is not supported yet in BitcoinJ
        // and batched txs would add more complexity to the trade protocol.

        // A typical trade fee tx has about 260 bytes (if one input). The trade txs has about 336-414 bytes.
        // We use 320 as a average value.

        // trade fee tx: 260 bytes (1 input)
        // deposit tx: 336 bytes (1 MS output+ OP_RETURN) - 414 bytes (1 MS output + OP_RETURN + change in case of smaller trade amount)
        // payout tx: 371 bytes
        // disputed payout tx: 408 bytes

        // Set the default values (in rare cases if the fee request was not done yet we get the hard coded default values)
        // But the "take offer" happens usually after that so we should have already the value from the estimation service.
        txFeePerByteFromFeeService = feeService.getTxFeePerByte();
        txFeeFromFeeService = offerUtil.getTxFeeBySize(txFeePerByteFromFeeService, feeTxSize);

        // We request to get the actual estimated fee
        log.info("Start requestTxFee: txFeeFromFeeService={}", txFeeFromFeeService);
        feeService.requestFees(() -> {
            txFeePerByteFromFeeService = feeService.getTxFeePerByte();
            txFeeFromFeeService = offerUtil.getTxFeeBySize(txFeePerByteFromFeeService, feeTxSize);
            calculateTotalToPay();
        });
    }

    private void calculateTotalToPay() {
        // Taker pays 2 times the tx fee because the mining fee might be different when
        // maker created the offer and reserved his funds, so that would not work well
        // with dynamic fees.  The mining fee for the takeOfferFee tx is deducted from
        // the createOfferFee and not visible to the trader.
        Coin feeAndSecDeposit = getTotalTxFee().add(securityDeposit);
        if (isCurrencyForTakerFeeBtc)
            feeAndSecDeposit = feeAndSecDeposit.add(takerFee);

        totalToPayAsCoin = offer.isBuyOffer()
                ? feeAndSecDeposit.add(amount)
                : feeAndSecDeposit;

        updateBalance();
    }

    private void calculateVolume() {
        Price tradePrice = offer.getPrice();
        Volume volumeByAmount = Objects.requireNonNull(tradePrice).getVolumeByAmount(amount);

        if (offer.getPaymentMethod().getId().equals(PaymentMethod.HAL_CASH_ID))
            volumeByAmount = getAdjustedVolumeForHalCash(volumeByAmount);
        else if (CurrencyUtil.isFiatCurrency(offer.getCurrencyCode()))
            volumeByAmount = getRoundedFiatVolume(volumeByAmount);

        volume = volumeByAmount;

        updateBalance();
    }

    private void updateBalance() {
        Coin tradeWalletBalance = btcWalletService.getBalanceForAddress(addressEntry.getAddress());
        if (useSavingsWallet) {
            Coin savingWalletBalance = btcWalletService.getSavingWalletBalance();
            totalAvailableBalance = savingWalletBalance.add(tradeWalletBalance);
            if (totalToPayAsCoin != null)
                balance = minCoin(totalToPayAsCoin, totalAvailableBalance);

        } else {
            balance = tradeWalletBalance;
        }
        missingCoin = offerUtil.getBalanceShortage(totalToPayAsCoin, balance);
        isBtcWalletFunded = offerUtil.isBalanceSufficient(totalToPayAsCoin, balance);
    }

    private long getMaxTradeLimit() {
        return accountAgeWitnessService.getMyTradeLimit(paymentAccount,
                offer.getCurrencyCode(),
                offer.getMirroredDirection());
    }

    public Coin getTotalTxFee() {
        Coin totalTxFees = txFeeFromFeeService.add(getTxFeeForDepositTx()).add(getTxFeeForPayoutTx());
        if (isCurrencyForTakerFeeBtc)
            return totalTxFees;
        else
            return totalTxFees.subtract(takerFee);
    }

    @NotNull
    public Coin getFundsNeededForTrade() {
        return securityDeposit
                .add(getTxFeeForDepositTx())
                .add(getTxFeeForPayoutTx()
                        .add(amount));
    }

    private Coin getTxFeeForDepositTx() {
        // TODO fix with new trade protocol!
        // Unfortunately we cannot change that to the correct fees as it would break backward compatibility
        // We still might find a way with offer version or app version checks so lets keep that commented out
        // code as that shows how it should be.
        return txFeeFromFeeService;
    }

    private Coin getTxFeeForPayoutTx() {
        // TODO fix with new trade protocol!
        // Unfortunately we cannot change that to the correct fees as it would break backward compatibility
        // We still might find a way with offer version or app version checks so lets keep that commented out
        // code as that shows how it should be.
        return txFeeFromFeeService;
    }

    private void validateModelInputs() {
        checkNotNull(offer, "offer must not be null");
        checkNotNull(offer.getAmount(), "offer amount must not be null");
        checkArgument(offer.getAmount().value > 0, "offer amount must not be zero");
        checkNotNull(offer.getPrice(), "offer price must not be null");
        checkNotNull(paymentAccount, "payment account must not be null");
        checkNotNull(addressEntry, "address entry must not be null");
    }

    private void clearModel() {
        this.addressEntry = null;
        this.amount = null;
        this.balance = null;
        this.isBtcWalletFunded = false;
        this.isCurrencyForTakerFeeBtc = false;
        this.missingCoin = Coin.ZERO;
        this.offer = null;
        this.paymentAccount = null;
        this.securityDeposit = null;
        this.takerFee = null;
        this.totalAvailableBalance = null;
        this.totalToPayAsCoin = null;
        this.txFeeFromFeeService = null;
        this.txFeePerByteFromFeeService = null;
        this.useSavingsWallet = true;
        this.volume = null;
    }

    @Override
    public String toString() {
        return "TakeOfferModel{" +
                "  offer.id=" + offer.getId() + "\n" +
                "  offer.state=" + offer.getState() + "\n" +
                ", paymentAccount.id=" + paymentAccount.getId() + "\n" +
                ", paymentAccount.method.id=" + paymentAccount.getPaymentMethod().getId() + "\n" +
                ", useSavingsWallet=" + useSavingsWallet + "\n" +
                ", addressEntry=" + addressEntry + "\n" +
                ", amount=" + amount + "\n" +
                ", securityDeposit=" + securityDeposit + "\n" +
                ", feeTxSize=" + feeTxSize + "\n" +
                ", txFeePerByteFromFeeService=" + txFeePerByteFromFeeService + "\n" +
                ", txFeeFromFeeService=" + txFeeFromFeeService + "\n" +
                ", takerFee=" + takerFee + "\n" +
                ", totalToPayAsCoin=" + totalToPayAsCoin + "\n" +
                ", missingCoin=" + missingCoin + "\n" +
                ", totalAvailableBalance=" + totalAvailableBalance + "\n" +
                ", balance=" + balance + "\n" +
                ", volume=" + volume + "\n" +
                ", fundsNeededForTrade=" + getFundsNeededForTrade() + "\n" +
                ", isCurrencyForTakerFeeBtc=" + isCurrencyForTakerFeeBtc + "\n" +
                ", isBtcWalletFunded=" + isBtcWalletFunded + "\n" +
                '}';
    }
}
