package bisq.httpapi.facade;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.TakerUtil;
import bisq.core.offer.TxFeeEstimation;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.util.Tuple2;

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

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
            if (!p2PService.isBootstrapped())
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
        CompletableFuture<Trade> futureResult = new CompletableFuture<>();
        Offer offer;
        try {
            offer = findOffer(offerId);
        } catch (NotFoundException e) {
            return ResourceHelper.completeExceptionally(futureResult, e);
        }

        if (offer.getMakerNodeAddress().equals(p2PService.getAddress())) {
            return ResourceHelper.completeExceptionally(futureResult, new OfferTakerSameAsMakerException("Taker's address same as maker's"));
        }

        // check the paymentAccountId is valid
        PaymentAccount paymentAccount = user.getPaymentAccount(paymentAccountId);
        if (paymentAccount == null) {
            return ResourceHelper.completeExceptionally(futureResult, new PaymentAccountNotFoundException("Could not find payment account with id: " + paymentAccountId));
        }

        // check the paymentAccountId is compatible with the offer
        if (!isPaymentAccountValidForOffer(offer, paymentAccount)) {
            final String errorMessage = "PaymentAccount is not valid for offer, needs " + offer.getCurrencyCode();
            return ResourceHelper.completeExceptionally(futureResult, new IncompatiblePaymentAccountException(errorMessage));
        }

        // check the amount is within the range
        Coin amountAsCoin = Coin.valueOf(amount);
        // workaround because TradeTask does not have an error handler to notify us that something went wrong
        if (btcWalletService.getAvailableBalance().isLessThan(amountAsCoin)) {
            String errorMessage = "Available balance " + btcWalletService.getAvailableBalance() + " is less than needed amount: " + amountAsCoin;
            return ResourceHelper.completeExceptionally(futureResult, new InsufficientMoneyException(errorMessage));
        }

        // check that the price is correct ??

        Coin txFeeFromFeeService = feeService.getTxFee(380);
        Coin fundsNeededForTaker = TakerUtil.getFundsNeededForTakeOffer(amountAsCoin, txFeeFromFeeService, txFeeFromFeeService, offer);
        Coin takerFee = TakerUtil.getTakerFee(amountAsCoin, preferences, bsqWalletService);
        checkNotNull(takerFee, "takerFee must not be null");
        Tuple2<Coin, Integer> estimatedFeeAndTxSize = TxFeeEstimation.getEstimatedFeeAndTxSizeForTaker(fundsNeededForTaker,
                takerFee,
                feeService,
                btcWalletService,
                preferences);
        txFeeFromFeeService = estimatedFeeAndTxSize.first;
        checkNotNull(txFeeFromFeeService, "txFeeFromFeeService must not be null");

        boolean currencyForTakerFeeBtc = TakerUtil.isCurrencyForTakerFeeBtc(amountAsCoin, preferences, bsqWalletService);
        return tradeManager.onTakeOffer(amountAsCoin,
                txFeeFromFeeService,
                takerFee,
                currencyForTakerFeeBtc,
                offer.getPrice().getValue(),
                fundsNeededForTaker,
                offer,
                paymentAccount.getId(),
                useSavingsWallet);
    }
}
