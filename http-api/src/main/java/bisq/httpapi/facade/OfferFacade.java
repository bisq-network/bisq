package bisq.httpapi.facade;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.Restrictions;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.CoinUtil;

import bisq.httpapi.exceptions.AmountTooHighException;
import bisq.httpapi.exceptions.IncompatiblePaymentAccountException;
import bisq.httpapi.exceptions.InsufficientMoneyException;
import bisq.httpapi.exceptions.NotBootstrappedException;
import bisq.httpapi.exceptions.NotFoundException;
import bisq.httpapi.exceptions.OfferTakerSameAsMakerException;
import bisq.httpapi.exceptions.PaymentAccountNotFoundException;
import bisq.httpapi.model.InputDataForOffer;
import bisq.httpapi.model.OfferDetail;
import bisq.httpapi.model.PriceType;
import bisq.httpapi.service.endpoint.OfferBuilder;
import bisq.httpapi.util.ResourceHelper;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import static bisq.core.payment.PaymentAccountUtil.isPaymentAccountValidForOffer;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;



import javax.validation.ValidationException;

public class OfferFacade {

    private final OfferBookService offerBookService;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    private final OfferBuilder offerBuilder;
    private final P2PService p2PService;
    private final Preferences preferences;
    private final FeeService feeService;
    private final User user;
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;

    @Inject
    public OfferFacade(OfferBookService offerBookService,
                       TradeManager tradeManager,
                       OpenOfferManager openOfferManager,
                       OfferBuilder offerBuilder,
                       P2PService p2PService,
                       Preferences preferences,
                       FeeService feeService,
                       User user,
                       BtcWalletService btcWalletService,
                       BsqWalletService bsqWalletService) {
        this.offerBookService = offerBookService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.offerBuilder = offerBuilder;
        this.p2PService = p2PService;
        this.preferences = preferences;
        this.feeService = feeService;
        this.user = user;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
    }

    public List<OfferDetail> getAllOffers() {
        return offerBookService.getOffers().stream().map(OfferDetail::new).collect(toList());
    }

    public Offer findOffer(String offerId) {
        Optional<Offer> offerOptional = offerBookService.getOffers().stream()
                .filter(offer -> offer.getId().equals(offerId))
                .findAny();
        if (!offerOptional.isPresent()) {
            throw new NotFoundException("Offer not found: " + offerId);
        }
        return offerOptional.get();
    }

    public CompletableFuture<Void> cancelOffer(String offerId) {
        final CompletableFuture<Void> futureResult = new CompletableFuture<>();
        UserThread.execute(() -> {
            if (!isBootstrapped())
                futureResult.completeExceptionally(new NotBootstrappedException());

            Optional<OpenOffer> openOfferById = openOfferManager.getOpenOfferById(offerId);
            if (!openOfferById.isPresent()) {
                futureResult.completeExceptionally(new NotFoundException("Offer not found: " + offerId));
                return;
            }

            openOfferManager.removeOpenOffer(openOfferById.get(),
                    () -> futureResult.complete(null),
                    errorMessage -> futureResult.completeExceptionally(new RuntimeException(errorMessage)));
        });
        return futureResult;
    }

    public CompletableFuture<Offer> createOffer(InputDataForOffer input) {
        //TODO use UserThread.execute

        OfferPayload.Direction direction = OfferPayload.Direction.valueOf(input.direction);
        PriceType priceType = PriceType.valueOf(input.priceType);
        Double marketPriceMargin = null == input.percentageFromMarketPrice ? null : input.percentageFromMarketPrice.doubleValue();
        boolean fundUsingBisqWallet = input.fundUsingBisqWallet;
        String offerId = input.offerId;
        String accountId = input.accountId;
        long amount = input.amount;
        long minAmount = input.minAmount;
        boolean useMarketBasedPrice = PriceType.PERCENTAGE.equals(priceType);
        String marketPair = input.marketPair;
        long fiatPrice = input.fixedPrice;
        Long buyerSecurityDeposit = input.buyerSecurityDeposit;

        // exception from gui code is not clear enough, so this check is added. Missing money is another possible check but that's clear in the gui exception.
        final CompletableFuture<Offer> futureResult = new CompletableFuture<>();

        //TODO @bernard what is meant by "Specify offerId of earlier prepared offer if you want to use dedicated wallet address."?
        if (!fundUsingBisqWallet && null == offerId)
            return ResourceHelper.completeExceptionally(futureResult,
                    new ValidationException("Specify offerId of earlier prepared offer if you want to use dedicated wallet address."));

        Offer offer;
        try {
            offer = offerBuilder.build(offerId, accountId, direction, amount, minAmount, useMarketBasedPrice,
                    marketPriceMargin, marketPair, fiatPrice, buyerSecurityDeposit);
        } catch (Exception e) {
            return ResourceHelper.completeExceptionally(futureResult, e);
        }

        boolean isBuyOffer = OfferUtil.isBuyOffer(direction);
        Coin reservedFundsForOffer = isBuyOffer ? preferences.getBuyerSecurityDepositAsCoin() : Restrictions.getSellerSecurityDeposit();
        if (!isBuyOffer)
            reservedFundsForOffer = reservedFundsForOffer.add(Coin.valueOf(amount));

//        TODO check if there is sufficient money cause openOfferManager will log exception and pass just message
//        TODO openOfferManager should return CompletableFuture or at least send full exception to error handler

        // @bernard: ValidateOffer returns plenty of diff. error messages. To handle all separately would be a big
        // overkill. I think it should be ok to just display the errorMessage and not handle the diff. errors on your
        // side.
        // TODO check for tradeLimit is missing in ValidateOffer
        openOfferManager.placeOffer(offer,
                reservedFundsForOffer,
                fundUsingBisqWallet,
                transaction -> futureResult.complete(offer),
                errorMessage -> {
                    if (errorMessage.contains("Insufficient money"))
                        futureResult.completeExceptionally(new InsufficientMoneyException(errorMessage));
                    else if (errorMessage.contains("Amount is larger"))
                        futureResult.completeExceptionally(new AmountTooHighException(errorMessage));
                    else
                        futureResult.completeExceptionally(new RuntimeException(errorMessage));
                });

        return futureResult;
    }

    public CompletableFuture<Trade> offerTake(String offerId, String paymentAccountId, long amount, boolean useSavingsWallet) {
        //TODO use UserThread.execute
        final CompletableFuture<Trade> futureResult = new CompletableFuture<>();
        final Offer offer;
        try {
            offer = findOffer(offerId);
        } catch (NotFoundException e) {
            return ResourceHelper.completeExceptionally(futureResult, e);
        }

        if (offer.getMakerNodeAddress().equals(p2PService.getAddress())) {
            return ResourceHelper.completeExceptionally(futureResult, new OfferTakerSameAsMakerException("Taker's address same as maker's"));
        }

        // check the paymentAccountId is valid
        final PaymentAccount paymentAccount = getPaymentAccount(paymentAccountId);
        if (paymentAccount == null) {
            return ResourceHelper.completeExceptionally(futureResult, new PaymentAccountNotFoundException("Could not find payment account with id: " + paymentAccountId));
        }

        // check the paymentAccountId is compatible with the offer
        if (!isPaymentAccountValidForOffer(offer, paymentAccount)) {
            final String errorMessage = "PaymentAccount is not valid for offer, needs " + offer.getCurrencyCode();
            return ResourceHelper.completeExceptionally(futureResult, new IncompatiblePaymentAccountException(errorMessage));
        }

        // check the amount is within the range
        Coin coinAmount = Coin.valueOf(amount);
        //if(coinAmount.isLessThan(offer.getMinAmount()) || coinAmount.isGreaterThan(offer.getma)

        // workaround because TradeTask does not have an error handler to notify us that something went wrong
        if (btcWalletService.getAvailableBalance().isLessThan(coinAmount)) {
            final String errorMessage = "Available balance " + btcWalletService.getAvailableBalance() + " is less than needed amount: " + coinAmount;
            return ResourceHelper.completeExceptionally(futureResult, new InsufficientMoneyException(errorMessage));
        }

        // check that the price is correct ??

        // check taker fee

        // check security deposit for BTC buyer
        // check security deposit for BTC seller

        Coin securityDeposit = offer.getDirection() == OfferPayload.Direction.SELL ?
                offer.getBuyerSecurityDeposit() :
                offer.getSellerSecurityDeposit();
        Coin txFeeFromFeeService = feeService.getTxFee(600);
        Coin fundsNeededForTradeTemp = securityDeposit.add(txFeeFromFeeService).add(txFeeFromFeeService);
        final Coin fundsNeededForTrade;
        if (offer.isBuyOffer())
            fundsNeededForTrade = fundsNeededForTradeTemp.add(coinAmount);
        else
            fundsNeededForTrade = fundsNeededForTradeTemp;

        Coin takerFee = getTakerFee(coinAmount);
        checkNotNull(txFeeFromFeeService, "txFeeFromFeeService must not be null");
        checkNotNull(takerFee, "takerFee must not be null");

        tradeManager.onTakeOffer(coinAmount,
                txFeeFromFeeService,
                takerFee,
                isCurrencyForTakerFeeBtc(coinAmount),
                offer.getPrice().getValue(),
                fundsNeededForTrade,
                offer,
                paymentAccount.getId(),
                useSavingsWallet,
                futureResult::complete,
                error -> futureResult.completeExceptionally(new RuntimeException(error))
        );
        return futureResult;
    }


    private boolean isCurrencyForTakerFeeBtc(Coin amount) {
        return preferences.getPayFeeInBtc() || !isBsqForFeeAvailable(amount);
    }

    private boolean isBsqForFeeAvailable(Coin amount) {
        return BisqEnvironment.isBaseCurrencySupportingBsq() &&
                getTakerFee(amount, false) != null &&
                bsqWalletService.getAvailableBalance() != null &&
                getTakerFee(amount, false) != null &&
                !bsqWalletService.getAvailableBalance().subtract(getTakerFee(amount, false)).isNegative();
    }


    @Nullable
    private Coin getTakerFee(Coin amount, boolean isCurrencyForTakerFeeBtc) {
        if (amount != null) {
            // TODO write unit test for that
            Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getTakerFeePerBtc(isCurrencyForTakerFeeBtc), amount);
            return CoinUtil.maxCoin(feePerBtc, FeeService.getMinTakerFee(isCurrencyForTakerFeeBtc));
        } else {
            return null;
        }
    }

    @Nullable
    private Coin getTakerFee(Coin amount) {
        return getTakerFee(amount, isCurrencyForTakerFeeBtc(amount));
    }

    private PaymentAccount getPaymentAccount(String paymentAccountId) {
        return user.getPaymentAccount(paymentAccountId);
    }

    private boolean isBootstrapped() {
        return p2PService.isBootstrapped();
    }
}
