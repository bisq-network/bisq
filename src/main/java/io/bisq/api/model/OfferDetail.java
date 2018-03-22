package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OfferPayload;
import io.bisq.network.p2p.NodeAddress;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OfferDetail {
    public List<String> acceptedBankIds;
    public List<String> acceptedCountryCodes;
    public long amount;
    public List<String> arbitratorNodeAddresses;
    public String bankId;
    public String baseCurrencyCode;
    public long blockHeightAtOfferCreation;
    public long buyerSecurityDeposit;
    public String counterCurrencyCode;
    public String countryCode;
    public String currencyCode;
    public Date date;
    public OfferPayload.Direction direction;
    public String hashOfChallenge;
    public String id;
    public boolean isCurrencyForMakerFeeBtc;
    public boolean isPrivateOffer;
    public long lowerClosePrice;
    public long makerFee;
    public String makerPaymentAccountId;
    public double marketPriceMargin;
    public long maxTradeLimit;
    public long maxTradePeriod;
    public long minAmount;
    public String offerFeePaymentTxId;
    public String ownerNodeAddress;
    public String paymentMethodId;
    public long price;
    public int protocolVersion;
    public long sellerSecurityDeposit;
    public Offer.State state;
    public long txFee;
    public long upperClosePrice;
    public boolean useAutoClose;
    public boolean useMarketBasedPrice;
    public boolean useReOpenAfterAutoClose;
    public String versionNr;


    public OfferDetail() {
    }

    public OfferDetail(Offer offer) {
        final OfferPayload offerPayload = offer.getOfferPayload();
        this.id = offer.getId();
        this.direction = offer.getDirection();
        this.state = offer.getState();
        this.date = offer.getDate();
        this.arbitratorNodeAddresses = offerPayload.getArbitratorNodeAddresses().stream().map(NodeAddress::toString).collect(Collectors.toList());
        this.ownerNodeAddress = offerPayload.getOwnerNodeAddress().toString();
        this.price = offerPayload.getPrice();
        this.currencyCode = offerPayload.getCurrencyCode();
        this.marketPriceMargin = offerPayload.getMarketPriceMargin();
        this.useMarketBasedPrice = offerPayload.isUseMarketBasedPrice();
        this.amount = offerPayload.getAmount();
        this.minAmount = offerPayload.getMinAmount();
        this.baseCurrencyCode = offerPayload.getBaseCurrencyCode();
        this.counterCurrencyCode = offerPayload.getCounterCurrencyCode();
        this.paymentMethodId = offerPayload.getPaymentMethodId();
        this.makerPaymentAccountId = offerPayload.getMakerPaymentAccountId();
        this.offerFeePaymentTxId = offerPayload.getOfferFeePaymentTxId();
        this.countryCode = offerPayload.getCountryCode();
        this.acceptedCountryCodes = offerPayload.getAcceptedCountryCodes();
        this.bankId = offerPayload.getBankId();
        this.acceptedBankIds = offerPayload.getAcceptedBankIds();
        this.versionNr = offerPayload.getVersionNr();
        this.blockHeightAtOfferCreation = offerPayload.getBlockHeightAtOfferCreation();
        this.txFee = offerPayload.getTxFee();
        this.makerFee = offerPayload.getMakerFee();
        this.isCurrencyForMakerFeeBtc = offerPayload.isCurrencyForMakerFeeBtc();
        this.buyerSecurityDeposit = offerPayload.getBuyerSecurityDeposit();
        this.sellerSecurityDeposit = offerPayload.getSellerSecurityDeposit();
        this.maxTradeLimit = offerPayload.getMaxTradeLimit();
        this.maxTradePeriod = offerPayload.getMaxTradePeriod();
        this.useAutoClose = offerPayload.isUseAutoClose();
        this.useReOpenAfterAutoClose = offerPayload.isUseReOpenAfterAutoClose();
        this.lowerClosePrice = offerPayload.getLowerClosePrice();
        this.upperClosePrice = offerPayload.getUpperClosePrice();
        this.isPrivateOffer = offerPayload.isPrivateOffer();
        this.hashOfChallenge = offerPayload.getHashOfChallenge();
        this.protocolVersion = offerPayload.getProtocolVersion();
    }
}

