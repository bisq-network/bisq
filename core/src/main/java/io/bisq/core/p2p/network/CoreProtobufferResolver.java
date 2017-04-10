package io.bisq.core.p2p.network;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.protobuf.ByteString;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.crypto.SealedAndSigned;
import io.bisq.common.locale.*;
import io.bisq.common.monetary.Price;
import io.bisq.common.persistance.Msg;
import io.bisq.common.persistance.Persistable;
import io.bisq.common.persistance.ProtobufferResolver;
import io.bisq.core.alert.Alert;
import io.bisq.core.alert.PrivateNotificationMsg;
import io.bisq.core.alert.PrivateNotificationPayload;
import io.bisq.core.arbitration.*;
import io.bisq.core.arbitration.messages.*;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.AddressEntryList;
import io.bisq.core.btc.data.RawTransactionInput;
import io.bisq.core.dao.compensation.CompensationRequestPayload;
import io.bisq.core.filter.Filter;
import io.bisq.core.filter.PaymentAccountFilter;
import io.bisq.core.offer.AvailabilityResult;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.offer.messages.OfferAvailabilityRequest;
import io.bisq.core.offer.messages.OfferAvailabilityResponse;
import io.bisq.core.payment.payload.*;
import io.bisq.core.trade.Contract;
import io.bisq.core.trade.messages.*;
import io.bisq.core.trade.statistics.TradeStatistics;
import io.bisq.core.user.BlockChainExplorer;
import io.bisq.core.user.Preferences;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.CloseConnectionMsg;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.PrefixedSealedAndSignedMsg;
import io.bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import io.bisq.network.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import io.bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import io.bisq.network.p2p.peers.keepalive.messages.Ping;
import io.bisq.network.p2p.peers.keepalive.messages.Pong;
import io.bisq.network.p2p.peers.peerexchange.Peer;
import io.bisq.network.p2p.peers.peerexchange.messages.GetPeersRequest;
import io.bisq.network.p2p.peers.peerexchange.messages.GetPeersResponse;
import io.bisq.network.p2p.storage.messages.AddDataMsg;
import io.bisq.network.p2p.storage.messages.RefreshTTLMsg;
import io.bisq.network.p2p.storage.messages.RemoveDataMsg;
import io.bisq.network.p2p.storage.messages.RemoveMailboxDataMsg;
import io.bisq.network.p2p.storage.payload.MailboxStoragePayload;
import io.bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import io.bisq.network.p2p.storage.payload.StoragePayload;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static io.bisq.generated.protobuffer.PB.Envelope.MessageCase.*;

/**
 * If the Messages class is giving errors in IntelliJ, you should change the IntelliJ IDEA Platform Properties file,
 * idea.properties, to something bigger like 12500:
 * <p>
 * #---------------------------------------------------------------------
 * # Maximum file size (kilobytes) IDE should provide code assistance for.
 * # The larger file is the slower its editor works and higher overall system memory requirements are
 * # if code assistance is enabled. Remove this property or set to very large number if you need
 * # code assistance for any files available regardless their size.
 * #---------------------------------------------------------------------
 * idea.max.intellisense.filesize=2500
 */
@Slf4j
public class CoreProtobufferResolver implements ProtobufferResolver {

    //@Inject
    private Provider<AddressEntryList> addressEntryList;

    //@Inject
    private Provider<Preferences> preferencesProvider;

    @Override
    public Optional<Msg> fromProto(PB.Envelope envelope) {
        if (Objects.isNull(envelope)) {
            log.warn("fromProtoBuf called with empty envelope.");
            return Optional.empty();
        }
        if (envelope.getMessageCase() != PING && envelope.getMessageCase() != PONG && envelope.getMessageCase() != REFRESH_TTL_MESSAGE) {
            log.debug("Convert protobuffer envelope: {}, {}", envelope.getMessageCase(), envelope.toString());
        } else {
            log.debug("Convert protobuffer envelope: {}", envelope.getMessageCase());
            log.trace("Convert protobuffer envelope: {}", envelope.toString());
        }

        Msg result = null;
        switch (envelope.getMessageCase()) {
            case PING:
                result = getPing(envelope);
                break;
            case PONG:
                result = getPong(envelope);
                break;
            case REFRESH_TTL_MESSAGE:
                result = getRefreshTTLMessage(envelope);
                break;
            case CLOSE_CONNECTION_MESSAGE:
                result = getCloseConnectionMessage(envelope);
                break;
            case PRELIMINARY_GET_DATA_REQUEST:
                result = getPreliminaryGetDataRequest(envelope);
                break;
            case GET_UPDATED_DATA_REQUEST:
                result = getGetUpdatedDataRequest(envelope);
                break;
            case GET_PEERS_REQUEST:
                result = getGetPeersRequest(envelope);
                break;
            case GET_PEERS_RESPONSE:
                result = getGetPeersResponse(envelope);
                break;
            case GET_DATA_RESPONSE:
                result = getGetDataResponse(envelope);
                break;
            case PREFIXED_SEALED_AND_SIGNED_MESSAGE:
                result = getPrefixedSealedAndSignedMessage(envelope);
                break;
            case OFFER_AVAILABILITY_RESPONSE:
                result = getOfferAvailabilityResponse(envelope);
                break;
            case OFFER_AVAILABILITY_REQUEST:
                result = getOfferAvailabilityRequest(envelope);
                break;
            case REMOVE_DATA_MESSAGE:
                result = getRemoveDataMessage(envelope);
                break;
            case ADD_DATA_MESSAGE:
                result = getAddDataMessage(envelope);
                break;
            case REMOVE_MAILBOX_DATA_MESSAGE:
                result = getRemoveMailBoxDataMessage(envelope.getRemoveMailboxDataMessage());
                break;
            case DEPOSIT_TX_PUBLISHED_MESSAGE:
                result = getDepositTxPublishedMessage(envelope.getDepositTxPublishedMessage());
                break;
            case FINALIZE_PAYOUT_TX_REQUEST:
                result = getFinalizePayoutTxRequest(envelope.getFinalizePayoutTxRequest());
                break;
            case DISPUTE_COMMUNICATION_MESSAGE:
                result = getDisputeCommunicationMessage(envelope.getDisputeCommunicationMessage());
                break;
            case OPEN_NEW_DISPUTE_MESSAGE:
                result = getOpenNewDisputeMessage(envelope.getOpenNewDisputeMessage());
                break;
            case PEER_OPENED_DISPUTE_MESSAGE:
                result = getPeerOpenedDisputeMessage(envelope.getPeerOpenedDisputeMessage());
                break;
            case DISPUTE_RESULT_MESSAGE:
                result = getDisputeResultMessage(envelope.getDisputeResultMessage());
                break;
            case PEER_PUBLISHED_PAYOUT_TX_MESSAGE:
                result = getPeerPublishedPayoutTxMessage(envelope.getPeerPublishedPayoutTxMessage());
                break;
            case PAY_DEPOSIT_REQUEST:
                result = getPayDepositRequest(envelope.getPayDepositRequest());
                break;
            case PUBLISH_DEPOSIT_TX_REQUEST:
                result = getPublishDepositTxRequest(envelope.getPublishDepositTxRequest());
                break;
            case FIAT_TRANSFER_STARTED_MESSAGE:
                result = getFiatTransferStartedMessage(envelope.getFiatTransferStartedMessage());
                break;
            case PAYOUT_TX_PUBLISHED_MESSAGE:
                result = getPayoutTxPublishedMessage(envelope.getPayoutTxPublishedMessage());
                break;
            case PRIVATE_NOTIFICATION_MESSAGE:
                result = getPrivateNotificationMessage(envelope.getPrivateNotificationMessage());
                break;
            default:
                log.warn("Unknown message case:{}:{}", envelope.getMessageCase());
        }
        return Optional.ofNullable(result);
    }

    private static Msg getOfferAvailabilityRequest(PB.Envelope envelope) {
        PB.OfferAvailabilityRequest msg = envelope.getOfferAvailabilityRequest();
        return new OfferAvailabilityRequest(msg.getOfferId(), getPubKeyRing(msg.getPubKeyRing()), msg.getTakersTradePrice());

    }

    private static Msg getPrivateNotificationMessage(PB.PrivateNotificationMessage privateNotificationMessage) {
        return new PrivateNotificationMsg(getPrivateNotification(privateNotificationMessage.getPrivateNotificationPayload()),
                getNodeAddress(privateNotificationMessage.getMyNodeAddress()),
                privateNotificationMessage.getUid());
    }

    private static PrivateNotificationPayload getPrivateNotification(PB.PrivateNotificationPayload privateNotification) {
        return new PrivateNotificationPayload(privateNotification.getMessage());
    }

    private static Msg getPayoutTxPublishedMessage(PB.PayoutTxPublishedMessage payoutTxPublishedMessage) {
        return new PayoutTxPublishedMsg(payoutTxPublishedMessage.getTradeId(),
                payoutTxPublishedMessage.getPayoutTx().toByteArray(),
                getNodeAddress(payoutTxPublishedMessage.getSenderNodeAddress()),
                payoutTxPublishedMessage.getUid());
    }

    private static Msg getFiatTransferStartedMessage(PB.FiatTransferStartedMessage fiatTransferStartedMessage) {
        return new FiatTransferStartedMsg(fiatTransferStartedMessage.getTradeId(),
                fiatTransferStartedMessage.getBuyerPayoutAddress(),
                getNodeAddress(fiatTransferStartedMessage.getSenderNodeAddress()),
                fiatTransferStartedMessage.getBuyerSignature().toByteArray(),
                fiatTransferStartedMessage.getUid()
        );
    }

    private static Msg getPublishDepositTxRequest(PB.PublishDepositTxRequest publishDepositTxRequest) {
        List<RawTransactionInput> rawTransactionInputs = publishDepositTxRequest.getMakerInputsList().stream()
                .map(rawTransactionInput -> new RawTransactionInput(rawTransactionInput.getIndex(),
                        rawTransactionInput.getParentTransaction().toByteArray(), rawTransactionInput.getValue()))
                .collect(Collectors.toList());

        return new PublishDepositTxRequest(publishDepositTxRequest.getTradeId(),
                getPaymentAccountPayload(publishDepositTxRequest.getMakerPaymentAccountPayload()),
                publishDepositTxRequest.getMakerAccountId(),
                publishDepositTxRequest.getMakerMultiSigPubKey().toByteArray(),
                publishDepositTxRequest.getMakerContractAsJson(),
                publishDepositTxRequest.getMakerContractSignature(),
                publishDepositTxRequest.getMakerPayoutAddressString(),
                publishDepositTxRequest.getPreparedDepositTx().toByteArray(),
                rawTransactionInputs,
                getNodeAddress(publishDepositTxRequest.getSenderNodeAddress()),
                publishDepositTxRequest.getUid());
    }

    private static Msg getPayDepositRequest(PB.PayDepositRequest payDepositRequest) {
        List<RawTransactionInput> rawTransactionInputs = payDepositRequest.getRawTransactionInputsList().stream()
                .map(rawTransactionInput -> new RawTransactionInput(rawTransactionInput.getIndex(),
                        rawTransactionInput.getParentTransaction().toByteArray(), rawTransactionInput.getValue()))
                .collect(Collectors.toList());
        List<NodeAddress> arbitratorNodeAddresses = payDepositRequest.getAcceptedArbitratorNodeAddressesList().stream()
                .map(CoreProtobufferResolver::getNodeAddress).collect(Collectors.toList());
        List<NodeAddress> mediatorNodeAddresses = payDepositRequest.getAcceptedMediatorNodeAddressesList().stream()
                .map(CoreProtobufferResolver::getNodeAddress).collect(Collectors.toList());
        return new PayDepositRequest(getNodeAddress(payDepositRequest.getSenderNodeAddress()),
                payDepositRequest.getTradeId(),
                payDepositRequest.getTradeAmount(),
                payDepositRequest.getTradePrice(),
                payDepositRequest.getTxFee(),
                payDepositRequest.getTakerFee(),
                payDepositRequest.getIsCurrencyForTakerFeeBtc(),
                rawTransactionInputs, payDepositRequest.getChangeOutputValue(),
                payDepositRequest.getChangeOutputAddress(),
                payDepositRequest.getTakerMultiSigPubKey().toByteArray(),
                payDepositRequest.getTakerPayoutAddressString(),
                getPubKeyRing(payDepositRequest.getTakerPubKeyRing()),
                getPaymentAccountPayload(payDepositRequest.getTakerPaymentAccountPayload()),
                payDepositRequest.getTakerAccountId(),
                payDepositRequest.getTakerFeeTxId(),
                arbitratorNodeAddresses,
                mediatorNodeAddresses,
                getNodeAddress(payDepositRequest.getArbitratorNodeAddress()),
                getNodeAddress(payDepositRequest.getMediatorNodeAddress()));
    }

    private static Msg getPeerPublishedPayoutTxMessage(PB.PeerPublishedPayoutTxMessage peerPublishedPayoutTxMessage) {
        return new PeerPublishedPayoutTxMsg(peerPublishedPayoutTxMessage.getTransaction().toByteArray(),
                peerPublishedPayoutTxMessage.getTradeId(),
                getNodeAddress(peerPublishedPayoutTxMessage.getMyNodeAddress()),
                peerPublishedPayoutTxMessage.getUid());
    }

    private static Msg getDisputeResultMessage(PB.DisputeResultMessage disputeResultMessage) {

        PB.DisputeResult disputeResultproto = disputeResultMessage.getDisputeResult();
        DisputeResult disputeResult = new DisputeResult(disputeResultproto.getTradeId(),
                disputeResultproto.getTraderId(),
                DisputeResult.Winner.valueOf(disputeResultproto.getWinner().name()), disputeResultproto.getReasonOrdinal(),
                disputeResultproto.getTamperProofEvidence(), disputeResultproto.getIdVerification(), disputeResultproto.getScreenCast(),
                disputeResultproto.getSummaryNotes(),
                (DisputeCommunicationMsg) getDisputeCommunicationMessage(disputeResultproto.getDisputeCommunicationMessage()),
                disputeResultproto.getArbitratorSignature().toByteArray(), disputeResultproto.getBuyerPayoutAmount(),
                disputeResultproto.getSellerPayoutAmount(),
                disputeResultproto.getArbitratorPubKey().toByteArray(), disputeResultproto.getCloseDate(),
                disputeResultproto.getIsLoserPublisher());
        return new DisputeResultMsg(disputeResult,
                getNodeAddress(disputeResultMessage.getMyNodeAddress()),
                disputeResultMessage.getUid());
    }

    private static Msg getPeerOpenedDisputeMessage(PB.PeerOpenedDisputeMessage peerOpenedDisputeMessage) {
        return new PeerOpenedDisputeMsg(getDispute(peerOpenedDisputeMessage.getDispute()),
                getNodeAddress(peerOpenedDisputeMessage.getMyNodeAddress()), peerOpenedDisputeMessage.getUid());
    }

    private static Msg getOpenNewDisputeMessage(PB.OpenNewDisputeMessage openNewDisputeMessage) {
        return new OpenNewDisputeMsg(getDispute(openNewDisputeMessage.getDispute()),
                getNodeAddress(openNewDisputeMessage.getMyNodeAddress()), openNewDisputeMessage.getUid());
    }

    private static Dispute getDispute(PB.Dispute dispute) {
        return new Dispute(dispute.getTradeId(), dispute.getTraderId(),
                dispute.getDisputeOpenerIsBuyer(), dispute.getDisputeOpenerIsMaker(),
                getPubKeyRing(dispute.getTraderPubKeyRing()), new Date(dispute.getTradeDate()), getContract(dispute.getContract()),
                dispute.getContractHash().toByteArray(), dispute.getDepositTxSerialized().toByteArray(), dispute.getPayoutTxSerialized().toByteArray(),
                dispute.getDepositTxId(), dispute.getPayoutTxId(), dispute.getContractAsJson(), dispute.getMakerContractSignature(),
                dispute.getTakerContractSignature(), getPubKeyRing(dispute.getArbitratorPubKeyRing()), dispute.getIsSupportTicket());

    }

    private static Contract getContract(PB.Contract contract) {
        return new Contract(getOfferPayload(contract.getOfferPayload()),
                Coin.valueOf(contract.getTradeAmount()),
                Price.valueOf(getCurrencyCode(contract.getOfferPayload()), contract.getTradePrice()),
                contract.getTakerFeeTxId(),
                getNodeAddress(contract.getBuyerNodeAddress()),
                getNodeAddress(contract.getSellerNodeAddress()),
                getNodeAddress(contract.getArbitratorNodeAddress()),
                getNodeAddress(contract.getMediatorNodeAddress()),
                contract.getIsBuyerMakerAndSellerTaker(),
                contract.getMakerAccountId(),
                contract.getTakerAccountId(),
                getPaymentAccountPayload(contract.getMakerPaymentAccountPayload()),
                getPaymentAccountPayload(contract.getTakerPaymentAccountPayload()),
                getPubKeyRing(contract.getMakerPubKeyRing()),
                getPubKeyRing(contract.getTakerPubKeyRing()),
                contract.getMakerPayoutAddressString(),
                contract.getTakerPayoutAddressString(),
                contract.getMakerBtcPubKey().toByteArray(),
                contract.getTakerBtcPubKey().toByteArray());
    }

    private static String getCurrencyCode(PB.OfferPayload pbOffer) {
        String currencyCode;
        if (CurrencyUtil.isCryptoCurrency(pbOffer.getBaseCurrencyCode()))
            currencyCode = pbOffer.getBaseCurrencyCode();
        else
            currencyCode = pbOffer.getCounterCurrencyCode();
        return currencyCode;
    }

    public static PaymentAccountPayload getPaymentAccountPayload(PB.PaymentAccountPayload protoEntry) {
        PaymentAccountPayload result = null;
        switch (protoEntry.getMessageCase()) {
            case ALI_PAY_ACCOUNT_PAYLOAD:
                result = new AliPayAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getAliPayAccountPayload().getAccountNr());
                break;
            case CHASE_QUICK_PAY_ACCOUNT_PAYLOAD:
                result = new ChaseQuickPayAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getChaseQuickPayAccountPayload().getEmail(),
                        protoEntry.getChaseQuickPayAccountPayload().getHolderName());
                break;
            case CLEAR_XCHANGE_ACCOUNT_PAYLOAD:
                result = new ClearXchangeAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getClearXchangeAccountPayload().getHolderName(),
                        protoEntry.getClearXchangeAccountPayloadOrBuilder().getEmailOrMobileNr());
                break;
            case COUNTRY_BASED_PAYMENT_ACCOUNT_PAYLOAD:
                switch (protoEntry.getCountryBasedPaymentAccountPayload().getMessageCase()) {
                    case BANK_ACCOUNT_PAYLOAD:
                        switch (protoEntry.getCountryBasedPaymentAccountPayload().getBankAccountPayload().getMessageCase()) {
                            case NATIONAL_BANK_ACCOUNT_PAYLOAD:
                                NationalBankAccountPayload nationalBankAccountPayload = new NationalBankAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                                        protoEntry.getMaxTradePeriod());
                                fillInBankAccountPayload(protoEntry, nationalBankAccountPayload);
                                fillInCountryBasedPaymentAccountPayload(protoEntry, nationalBankAccountPayload);
                                result = nationalBankAccountPayload;
                                break;
                            case SAME_BANK_ACCONT_PAYLOAD:
                                SameBankAccountPayload sameBankAccountPayload = new SameBankAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                                        protoEntry.getMaxTradePeriod());
                                fillInBankAccountPayload(protoEntry, sameBankAccountPayload);
                                fillInCountryBasedPaymentAccountPayload(protoEntry, sameBankAccountPayload);
                                result = sameBankAccountPayload;
                                break;
                            case SPECIFIC_BANKS_ACCOUNT_PAYLOAD:
                                SpecificBanksAccountPayload specificBanksAccountPayload = new SpecificBanksAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                                        protoEntry.getMaxTradePeriod());
                                fillInBankAccountPayload(protoEntry, specificBanksAccountPayload);
                                fillInCountryBasedPaymentAccountPayload(protoEntry, specificBanksAccountPayload);
                                result = specificBanksAccountPayload;
                                break;
                        }
                        break;
                    case CASH_DEPOSIT_ACCOUNT_PAYLOAD:
                        CashDepositAccountPayload cashDepositAccountPayload = new CashDepositAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                                protoEntry.getMaxTradePeriod());
                        fillInCountryBasedPaymentAccountPayload(protoEntry, cashDepositAccountPayload);
                        result = cashDepositAccountPayload;
                        break;
                    case SEPA_ACCOUNT_PAYLOAD:
                        SepaAccountPayload sepaAccountPayload = new SepaAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                                protoEntry.getMaxTradePeriod(), CountryUtil.getAllSepaCountries());
                        fillInCountryBasedPaymentAccountPayload(protoEntry, sepaAccountPayload);
                        result = sepaAccountPayload;
                        break;
                }
                break;
            case CRYPTO_CURRENCY_ACCOUNT_PAYLOAD:
                result = new CryptoCurrencyAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getCryptoCurrencyAccountPayload().getAddress());
                break;
            case FASTER_PAYMENTS_ACCOUNT_PAYLOAD:
                result = new FasterPaymentsAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getFasterPaymentsAccountPayload().getSortCode(),
                        protoEntry.getFasterPaymentsAccountPayload().getAccountNr());
                break;
            case INTERAC_E_TRANSFER_ACCOUNT_PAYLOAD:
                PB.InteracETransferAccountPayload interacETransferAccountPayload =
                        protoEntry.getInteracETransferAccountPayload();
                result = new InteracETransferAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), interacETransferAccountPayload.getEmail(),
                        interacETransferAccountPayload.getHolderName(),
                        interacETransferAccountPayload.getQuestion(),
                        interacETransferAccountPayload.getAnswer());
                break;
            case O_K_PAY_ACCOUNT_PAYLOAD:
                result = getOkPayAccountPayload(protoEntry);
                break;
            case PERFECT_MONEY_ACCOUNT_PAYLOAD:
                result = new PerfectMoneyAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getPerfectMoneyAccountPayload().getAccountNr());
                break;
            case SWISH_ACCOUNT_PAYLOAD:
                result = new SwishAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getSwishAccountPayload().getMobileNr(),
                        protoEntry.getSwishAccountPayload().getHolderName());
                break;
            case U_S_POSTAL_MONEY_ORDER_ACCOUNT_PAYLOAD:
                result = new USPostalMoneyOrderAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getUSPostalMoneyOrderAccountPayload().getPostalAddress(),
                        protoEntry.getUSPostalMoneyOrderAccountPayload().getHolderName());
                break;
            default:
                log.error("Unknown paymentaccountcontractdata:{}", protoEntry.getMessageCase());
        }
        return result;
    }

    @NotNull
    public static OKPayAccountPayload getOkPayAccountPayload(PB.PaymentAccountPayload protoEntry) {
        return new OKPayAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                protoEntry.getMaxTradePeriod(), protoEntry.getOKPayAccountPayload().getAccountNr());
    }

    private static void fillInBankAccountPayload(PB.PaymentAccountPayload protoEntry, BankAccountPayload bankAccountPayload) {
        PB.BankAccountPayload bankProto = protoEntry.getCountryBasedPaymentAccountPayload().getBankAccountPayload();
        bankAccountPayload.setHolderName(bankProto.getHolderName());
        bankAccountPayload.setBankName(bankProto.getBankName());
        bankAccountPayload.setBankId(bankProto.getBankId());
        bankAccountPayload.setBranchId(bankProto.getBranchId());
        bankAccountPayload.setAccountNr(bankProto.getAccountNr());
        bankAccountPayload.setAccountType(bankProto.getAccountType());
    }

    private static void fillInCountryBasedPaymentAccountPayload(PB.PaymentAccountPayload protoEntry,
                                                                CountryBasedPaymentAccountPayload countryBasedPaymentAccountPayload) {
        countryBasedPaymentAccountPayload.setCountryCode(protoEntry.getCountryBasedPaymentAccountPayload().getCountryCode());
    }

    public static OfferPayload getOfferPayload(PB.OfferPayload pbOffer) {
        List<NodeAddress> arbitratorNodeAddresses = pbOffer.getArbitratorNodeAddressesList().stream()
                .map(CoreProtobufferResolver::getNodeAddress).collect(Collectors.toList());
        List<NodeAddress> mediatorNodeAddresses = pbOffer.getMediatorNodeAddressesList().stream()
                .map(CoreProtobufferResolver::getNodeAddress).collect(Collectors.toList());

        // Nullable object need to be checked against the default values in PB (not nice... ;-( )

        // convert these lists because otherwise when they're empty they are lazyStringArrayList objects and NOT serializable,
        // which is needed for the P2PStorage getHash() operation
        List<String> acceptedCountryCodes = pbOffer.getAcceptedCountryCodesList().isEmpty() ?
                null : pbOffer.getAcceptedCountryCodesList().stream().collect(Collectors.toList());
        List<String> acceptedBankIds = pbOffer.getAcceptedBankIdsList().isEmpty() ?
                null : pbOffer.getAcceptedBankIdsList().stream().collect(Collectors.toList());
        Map<String, String> extraDataMapMap = CollectionUtils.isEmpty(pbOffer.getExtraDataMapMap()) ?
                null : pbOffer.getExtraDataMapMap();
        final String countryCode1 = pbOffer.getCountryCode();
        String countryCode = countryCode1.isEmpty() ? null : countryCode1;
        String bankId = pbOffer.getBankId().isEmpty() ? null : pbOffer.getBankId();
        String offerFeePaymentTxId = pbOffer.getOfferFeePaymentTxId().isEmpty() ? null : pbOffer.getOfferFeePaymentTxId();
        String hashOfChallenge = pbOffer.getHashOfChallenge().isEmpty() ? null : pbOffer.getHashOfChallenge();

        return new OfferPayload(pbOffer.getId(),
                pbOffer.getDate(),
                getNodeAddress(pbOffer.getMakerNodeAddress()),
                getPubKeyRing(pbOffer.getPubKeyRing()),
                getDirection(pbOffer.getDirection()),
                pbOffer.getPrice(),
                pbOffer.getMarketPriceMargin(),
                pbOffer.getUseMarketBasedPrice(),
                pbOffer.getAmount(),
                pbOffer.getMinAmount(),
                pbOffer.getBaseCurrencyCode(),
                pbOffer.getCounterCurrencyCode(),
                arbitratorNodeAddresses,
                mediatorNodeAddresses,
                pbOffer.getPaymentMethodId(),
                pbOffer.getMakerPaymentAccountId(),
                offerFeePaymentTxId,
                countryCode,
                acceptedCountryCodes,
                bankId,
                acceptedBankIds,
                pbOffer.getVersionNr(),
                pbOffer.getBlockHeightAtOfferCreation(),
                pbOffer.getTxFee(),
                pbOffer.getMakerFee(),
                pbOffer.getIsCurrencyForMakerFeeBtc(),
                pbOffer.getBuyerSecurityDeposit(),
                pbOffer.getSellerSecurityDeposit(),
                pbOffer.getMaxTradeLimit(),
                pbOffer.getMaxTradePeriod(),
                pbOffer.getUseAutoClose(),
                pbOffer.getUseReOpenAfterAutoClose(),
                pbOffer.getLowerClosePrice(),
                pbOffer.getUpperClosePrice(),
                pbOffer.getIsPrivateOffer(),
                hashOfChallenge,
                extraDataMapMap);
    }

    private static Msg getDisputeCommunicationMessage(PB.DisputeCommunicationMessage disputeCommunicationMessage) {
        return new DisputeCommunicationMsg(disputeCommunicationMessage.getTradeId(),
                disputeCommunicationMessage.getTraderId(),
                disputeCommunicationMessage.getSenderIsTrader(),
                disputeCommunicationMessage.getMessage(),
                disputeCommunicationMessage.getAttachmentsList().stream()
                        .map(attachment -> new Attachment(attachment.getFileName(), attachment.getBytes().toByteArray()))
                        .collect(Collectors.toList()),
                getNodeAddress(disputeCommunicationMessage.getMyNodeAddress()),
                disputeCommunicationMessage.getDate(),
                disputeCommunicationMessage.getArrived(),
                disputeCommunicationMessage.getStoredInMailbox(),
                disputeCommunicationMessage.getUid());
    }

    private static Msg getFinalizePayoutTxRequest(PB.FinalizePayoutTxRequest finalizePayoutTxRequest) {
        return new FinalizePayoutTxRequest(finalizePayoutTxRequest.getTradeId(),
                finalizePayoutTxRequest.getSellerSignature().toByteArray(),
                finalizePayoutTxRequest.getSellerPayoutAddress(),
                getNodeAddress(finalizePayoutTxRequest.getSenderNodeAddress()),
                finalizePayoutTxRequest.getUid());
    }

    private static Msg getDepositTxPublishedMessage(PB.DepositTxPublishedMessage depositTxPublishedMessage) {
        return new DepositTxPublishedMsg(depositTxPublishedMessage.getTradeId(),
                depositTxPublishedMessage.getDepositTx().toByteArray(),
                getNodeAddress(depositTxPublishedMessage.getSenderNodeAddress()), depositTxPublishedMessage.getUid());
    }

    private static Msg getRemoveMailBoxDataMessage(PB.RemoveMailboxDataMessage msg) {
        return new RemoveMailboxDataMsg(getProtectedMailBoxStorageEntry(msg.getProtectedStorageEntry()));
    }

    public static Msg getAddDataMessage(PB.Envelope envelope) {
        return new AddDataMsg(getProtectedOrMailboxStorageEntry(envelope.getAddDataMessage().getEntry()));
    }

    public static ProtectedStorageEntry getProtectedOrMailboxStorageEntry(PB.ProtectedStorageEntryOrProtectedMailboxStorageEntry entry) {
        if (entry.getMessageCase() == PB.ProtectedStorageEntryOrProtectedMailboxStorageEntry.MessageCase.PROTECTED_MAILBOX_STORAGE_ENTRY) {
            return getProtectedMailBoxStorageEntry(entry.getProtectedMailboxStorageEntry());
        } else {
            return getProtectedStorageEntry(entry.getProtectedStorageEntry());
        }
    }

    private static Msg getRemoveDataMessage(PB.Envelope envelope) {
        return new RemoveDataMsg(getProtectedStorageEntry(envelope.getRemoveDataMessage().getProtectedStorageEntry()));
    }

    public static ProtectedStorageEntry getProtectedStorageEntry(PB.ProtectedStorageEntry protoEntry) {
        StoragePayload storagePayload = getStoragePayload(protoEntry.getStoragePayload());
        ProtectedStorageEntry storageEntry = new ProtectedStorageEntry(protoEntry.getCreationTimeStamp(), storagePayload,
                protoEntry.getOwnerPubKeyBytes().toByteArray(), protoEntry.getSequenceNumber(),
                protoEntry.getSignature().toByteArray());
        return storageEntry;
    }

    private static ProtectedMailboxStorageEntry getProtectedMailBoxStorageEntry(PB.ProtectedMailboxStorageEntry protoEntry) {
        ProtectedStorageEntry entry = getProtectedStorageEntry(protoEntry.getEntry());

        if (!(entry.getStoragePayload() instanceof MailboxStoragePayload)) {
            log.error("Trying to extract MailboxStoragePayload from a ProtectedMailboxStorageEntry," +
                    " but it's the wrong type {}", entry.getStoragePayload().toString());
            return null;
        }

        ProtectedMailboxStorageEntry storageEntry = new ProtectedMailboxStorageEntry(
                entry.creationTimeStamp,
                (MailboxStoragePayload) entry.getStoragePayload(),
                entry.ownerPubKey.getEncoded(), entry.sequenceNumber,
                entry.signature, protoEntry.getReceiversPubKeyBytes().toByteArray());
        return storageEntry;
    }

    @Nullable
    private static StoragePayload getStoragePayload(PB.StoragePayload protoEntry) {
        StoragePayload storagePayload = null;
        Map<String, String> extraDataMapMap;
        switch (protoEntry.getMessageCase()) {
            case ALERT:
                PB.Alert protoAlert = protoEntry.getAlert();
                extraDataMapMap = CollectionUtils.isEmpty(protoAlert.getExtraDataMapMap()) ?
                        null : protoAlert.getExtraDataMapMap();
                storagePayload = new Alert(protoAlert.getMessage(),
                        protoAlert.getIsUpdateInfo(),
                        protoAlert.getVersion(),
                        protoAlert.getStoragePublicKeyBytes().toByteArray(),
                        protoAlert.getSignatureAsBase64(),
                        extraDataMapMap);
                break;
            case ARBITRATOR:
                PB.Arbitrator arbitrator = protoEntry.getArbitrator();
                extraDataMapMap = CollectionUtils.isEmpty(arbitrator.getExtraDataMapMap()) ?
                        null : arbitrator.getExtraDataMapMap();
                List<String> strings = arbitrator.getLanguageCodesList().stream().collect(Collectors.toList());
                Date date = new Date(arbitrator.getRegistrationDate());
                String emailAddress = arbitrator.getEmailAddress().isEmpty() ? null : arbitrator.getEmailAddress();
                storagePayload = new Arbitrator(getNodeAddress(arbitrator.getNodeAddress()),
                        arbitrator.getBtcPubKey().toByteArray(),
                        arbitrator.getBtcAddress(),
                        getPubKeyRing(arbitrator.getPubKeyRing()),
                        strings,
                        date,
                        arbitrator.getRegistrationPubKey().toByteArray(),
                        arbitrator.getRegistrationSignature(),
                        emailAddress,
                        extraDataMapMap);
                break;
            case MEDIATOR:
                PB.Mediator mediator = protoEntry.getMediator();
                extraDataMapMap = CollectionUtils.isEmpty(mediator.getExtraDataMapMap()) ?
                        null : mediator.getExtraDataMapMap();
                strings = mediator.getLanguageCodesList().stream().collect(Collectors.toList());
                date = new Date(mediator.getRegistrationDate());
                emailAddress = mediator.getEmailAddress().isEmpty() ? null : mediator.getEmailAddress();
                storagePayload = new Mediator(getNodeAddress(mediator.getNodeAddress()),
                        getPubKeyRing(mediator.getPubKeyRing()),
                        strings,
                        date,
                        mediator.getRegistrationPubKey().toByteArray(),
                        mediator.getRegistrationSignature(),
                        emailAddress,
                        extraDataMapMap);
                break;
            case FILTER:
                PB.Filter filter = protoEntry.getFilter();
                extraDataMapMap = CollectionUtils.isEmpty(filter.getExtraDataMapMap()) ?
                        null : filter.getExtraDataMapMap();
                List<PaymentAccountFilter> paymentAccountFilters = filter.getBannedPaymentAccountsList()
                        .stream().map(accountFilter -> getPaymentAccountFilter(accountFilter)).collect(Collectors.toList());
                storagePayload = new Filter(filter.getBannedOfferIdsList().stream().collect(Collectors.toList()),
                        filter.getBannedNodeAddressList().stream().collect(Collectors.toList()),
                        paymentAccountFilters,
                        filter.getSignatureAsBase64(),
                        filter.getPublicKeyBytes().toByteArray(),
                        extraDataMapMap);
                break;
            case COMPENSATION_REQUEST_PAYLOAD:
                PB.CompensationRequestPayload compensationRequestPayload = protoEntry.getCompensationRequestPayload();
                extraDataMapMap = CollectionUtils.isEmpty(compensationRequestPayload.getExtraDataMapMap()) ?
                        null : compensationRequestPayload.getExtraDataMapMap();
                storagePayload = new CompensationRequestPayload(compensationRequestPayload.getUid(),
                        compensationRequestPayload.getName(),
                        compensationRequestPayload.getTitle(),
                        compensationRequestPayload.getCategory(),
                        compensationRequestPayload.getDescription(),
                        compensationRequestPayload.getLink(),
                        new Date(compensationRequestPayload.getStartDate()),
                        new Date(compensationRequestPayload.getEndDate()),
                        Coin.valueOf(compensationRequestPayload.getRequestedBtc()),
                        compensationRequestPayload.getBtcAddress(),
                        new NodeAddress(compensationRequestPayload.getNodeAddress()),
                        compensationRequestPayload.getP2PStorageSignaturePubKeyBytes().toByteArray(),
                        extraDataMapMap);
                break;
            case TRADE_STATISTICS:
                PB.TradeStatistics protoTrade = protoEntry.getTradeStatistics();
                extraDataMapMap = CollectionUtils.isEmpty(protoTrade.getExtraDataMapMap()) ?
                        null : protoTrade.getExtraDataMapMap();
                storagePayload = new TradeStatistics(getDirection(protoTrade.getDirection()),
                        protoTrade.getBaseCurrency(),
                        protoTrade.getCounterCurrency(),
                        protoTrade.getPaymentMethodId(),
                        protoTrade.getOfferDate(),
                        protoTrade.getUseMarketBasedPrice(),
                        protoTrade.getMarketPriceMargin(),
                        protoTrade.getOfferAmount(),
                        protoTrade.getOfferMinAmount(),
                        protoTrade.getOfferId(),
                        protoTrade.getTradePrice(),
                        protoTrade.getTradeAmount(),
                        protoTrade.getTradeDate(),
                        protoTrade.getDepositTxId(),
                        new PubKeyRing(protoTrade.getPubKeyRing().getSignaturePubKeyBytes().toByteArray(),
                                protoTrade.getPubKeyRing().getEncryptionPubKeyBytes().toByteArray(),
                                protoTrade.getPubKeyRing().getPgpPubKeyAsPem()),
                        extraDataMapMap);
                break;
            case MAILBOX_STORAGE_PAYLOAD:
                PB.MailboxStoragePayload mbox = protoEntry.getMailboxStoragePayload();
                extraDataMapMap = CollectionUtils.isEmpty(mbox.getExtraDataMapMap()) ?
                        null : mbox.getExtraDataMapMap();
                storagePayload = new MailboxStoragePayload(
                        getPrefixedSealedAndSignedMessage(mbox.getPrefixedSealedAndSignedMessage()),
                        mbox.getSenderPubKeyForAddOperationBytes().toByteArray(),
                        mbox.getReceiverPubKeyForRemoveOperationBytes().toByteArray(),
                        extraDataMapMap);
                break;
            case OFFER_PAYLOAD:
                storagePayload = getOfferPayload(protoEntry.getOfferPayload());
                break;
            default:
                log.error("Unknown storagepayload:{}", protoEntry.getMessageCase());
        }
        return storagePayload;
    }

    @NotNull
    public static OfferPayload.Direction getDirection(PB.OfferPayload.Direction direction) {
        return OfferPayload.Direction.valueOf(direction.name());
    }

    @NotNull
    private static PubKeyRing getPubKeyRing(PB.PubKeyRing pubKeyRing) {
        return new PubKeyRing(pubKeyRing.getSignaturePubKeyBytes().toByteArray(),
                pubKeyRing.getEncryptionPubKeyBytes().toByteArray(),
                pubKeyRing.getPgpPubKeyAsPem());
    }

    private static NodeAddress getNodeAddress(PB.NodeAddress protoNode) {
        return new NodeAddress(protoNode.getHostName(), protoNode.getPort());
    }

    private static PaymentAccountFilter getPaymentAccountFilter(PB.PaymentAccountFilter accountFilter) {
        return new PaymentAccountFilter(accountFilter.getPaymentMethodId(), accountFilter.getGetMethodName(),
                accountFilter.getValue());
    }

    private static Msg getOfferAvailabilityResponse(PB.Envelope envelope) {
        PB.OfferAvailabilityResponse msg = envelope.getOfferAvailabilityResponse();
        return new OfferAvailabilityResponse(msg.getOfferId(),
                AvailabilityResult.valueOf(
                        PB.AvailabilityResult.forNumber(msg.getAvailabilityResult().getNumber()).name()));
    }


    @NotNull
    private static Msg getPrefixedSealedAndSignedMessage(PB.Envelope envelope) {
        return getPrefixedSealedAndSignedMessage(envelope.getPrefixedSealedAndSignedMessage());
    }

    @NotNull
    private static PrefixedSealedAndSignedMsg getPrefixedSealedAndSignedMessage(PB.PrefixedSealedAndSignedMessage msg) {
        NodeAddress nodeAddress;
        nodeAddress = new NodeAddress(msg.getNodeAddress().getHostName(), msg.getNodeAddress().getPort());
        SealedAndSigned sealedAndSigned = new SealedAndSigned(msg.getSealedAndSigned().getEncryptedSecretKey().toByteArray(),
                msg.getSealedAndSigned().getEncryptedPayloadWithHmac().toByteArray(),
                msg.getSealedAndSigned().getSignature().toByteArray(), msg.getSealedAndSigned().getSigPublicKeyBytes().toByteArray());
        return new PrefixedSealedAndSignedMsg(nodeAddress, sealedAndSigned, msg.getAddressPrefixHash().toByteArray(), msg.getUid());
    }

    @NotNull
    private static Msg getGetDataResponse(PB.Envelope envelope) {
        HashSet<ProtectedStorageEntry> set = new HashSet<>(
                envelope.getGetDataResponse().getDataSetList()
                        .stream()
                        .map(protectedStorageEntry ->
                                getProtectedOrMailboxStorageEntry(protectedStorageEntry)).collect(Collectors.toList()));
        return new GetDataResponse(set, envelope.getGetDataResponse().getRequestNonce(), envelope.getGetDataResponse().getIsGetUpdatedDataResponse());
    }

    @NotNull
    private static Msg getGetPeersResponse(PB.Envelope envelope) {
        Msg result;
        PB.GetPeersResponse msg = envelope.getGetPeersResponse();
        HashSet<Peer> set = new HashSet<>(
                msg.getReportedPeersList()
                        .stream()
                        .map(peer ->
                                new Peer(new NodeAddress(peer.getNodeAddress().getHostName(),
                                        peer.getNodeAddress().getPort()))).collect(Collectors.toList()));
        result = new GetPeersResponse(msg.getRequestNonce(), set);
        return result;
    }

    @NotNull
    private static Msg getGetPeersRequest(PB.Envelope envelope) {
        NodeAddress nodeAddress;
        Msg result;
        PB.GetPeersRequest msg = envelope.getGetPeersRequest();
        nodeAddress = new NodeAddress(msg.getSenderNodeAddress().getHostName(), msg.getSenderNodeAddress().getPort());
        HashSet<Peer> set = new HashSet<>(
                msg.getReportedPeersList()
                        .stream()
                        .map(peer ->
                                new Peer(new NodeAddress(peer.getNodeAddress().getHostName(),
                                        peer.getNodeAddress().getPort()))).collect(Collectors.toList()));
        result = new GetPeersRequest(nodeAddress, msg.getNonce(), set);
        return result;
    }

    @NotNull
    private static Msg getGetUpdatedDataRequest(PB.Envelope envelope) {
        NodeAddress nodeAddress;
        Msg result;
        PB.GetUpdatedDataRequest msg = envelope.getGetUpdatedDataRequest();
        nodeAddress = new NodeAddress(msg.getSenderNodeAddress().getHostName(), msg.getSenderNodeAddress().getPort());
        Set<byte[]> updatedDataRequestSet = getByteSet(msg.getExcludedKeysList());
        result = new GetUpdatedDataRequest(nodeAddress, msg.getNonce(), updatedDataRequestSet);
        return result;
    }

    @NotNull
    private static Msg getPreliminaryGetDataRequest(PB.Envelope envelope) {
        Msg result;
        result = new PreliminaryGetDataRequest(envelope.getPreliminaryGetDataRequest().getNonce(),
                getByteSet(envelope.getPreliminaryGetDataRequest().getExcludedKeysList()));
        return result;
    }

    @NotNull
    private static Msg getCloseConnectionMessage(PB.Envelope envelope) {
        Msg result;
        result = new CloseConnectionMsg(envelope.getCloseConnectionMessage().getReason());
        return result;
    }

    @NotNull
    private static Msg getRefreshTTLMessage(PB.Envelope envelope) {
        Msg result;
        PB.RefreshTTLMessage msg = envelope.getRefreshTtlMessage();
        result = new RefreshTTLMsg(msg.getHashOfDataAndSeqNr().toByteArray(),
                msg.getSignature().toByteArray(), msg.getHashOfPayload().toByteArray(), msg.getSequenceNumber());
        return result;
    }

    @NotNull
    private static Msg getPong(PB.Envelope envelope) {
        Msg result;
        result = new Pong(envelope.getPong().getRequestNonce());
        return result;
    }

    @NotNull
    private static Msg getPing(PB.Envelope envelope) {
        Msg result;
        result = new Ping(envelope.getPing().getNonce(), envelope.getPing().getLastRoundTripTime());
        return result;
    }

    private static Set<byte[]> getByteSet(List<ByteString> byteStringList) {
        return new HashSet<>(
                byteStringList
                        .stream()
                        .map(ByteString::toByteArray).collect(Collectors.toList()));
    }


    //////////////////////////////// DISK /////////////////////////////////////

    @Override
    public Optional<Persistable> fromProto(PB.DiskEnvelope envelope) {
        if (Objects.isNull(envelope)) {
            log.warn("fromProtoBuf called with empty disk envelope.");
            return Optional.empty();
        }

        log.debug("Convert protobuffer disk envelope: {}", envelope.getMessageCase());

        Persistable result = null;
        switch (envelope.getMessageCase()) {
            case ADDRESS_ENTRY_LIST:
                addToAddressEntryList(envelope);
                result = addressEntryList.get();
                break;
                /*
            case NAVIGATION:
                result = getPing(envelope);
                break;
            case PERSISTED_PEERS:
                result = getPing(envelope);
                break;
                */
            case PREFERENCES:
                setPreferences(envelope);
                break;
                /*
            case USER:
                result = getPing(envelope);
                break;
            case PERSISTED_P2P_STORAGE_DATA:
                result = getPing(envelope);
                break;
            case SEQUENCE_NUMBER_MAP:
                result = getPing(envelope);
                break;
                */
            default:
                log.warn("Unknown message case:{}:{}", envelope.getMessageCase());
        }
        return Optional.ofNullable(result);
    }

    private void setPreferences(PB.DiskEnvelope envelope) {
        Preferences preferences = preferencesProvider.get();
        preferences.setUserLanguage(envelope.getPreferences().getUserLanguage());
        PB.Country userCountry = envelope.getPreferences().getUserCountry();
        preferences.setUserCountry(new Country(userCountry.getCode(), userCountry.getName(), new Region(userCountry.getRegion().getCode(), userCountry.getRegion().getName())));
        envelope.getPreferences().getFiatCurrenciesList().stream()
                .forEach(tradeCurrency -> preferences.addFiatCurrency((FiatCurrency) getTradeCurrency(tradeCurrency)));
        envelope.getPreferences().getCryptoCurrenciesList().stream()
                .forEach(tradeCurrency -> preferences.addCryptoCurrency((CryptoCurrency) getTradeCurrency(tradeCurrency)));
        PB.BlockChainExplorer bceMain = envelope.getPreferences().getBlockChainExplorerMainNet();
        preferences.setBlockChainExplorerMainNet(new BlockChainExplorer(bceMain.getName(), bceMain.getTxUrl(), bceMain.getAddressUrl()));
        PB.BlockChainExplorer bceTest = envelope.getPreferences().getBlockChainExplorerTestNet();
        preferences.setBlockChainExplorerTestNet(new BlockChainExplorer(bceTest.getName(), bceTest.getTxUrl(), bceTest.getAddressUrl()));
        preferences.setBackupDirectory(envelope.getPreferences().getBackupDirectory());
        preferences.setAutoSelectArbitrators(envelope.getPreferences().getAutoSelectArbitrators());
        preferences.setDontShowAgainMap(envelope.getPreferences().getDontShowAgainMapMap());
        preferences.setTacAccepted(envelope.getPreferences().getTacAccepted());
        preferences.setUseTorForBitcoinJ(envelope.getPreferences().getUseTorForBitcoinJ());
        preferences.setShowOwnOffersInOfferBook(envelope.getPreferences().getShowOwnOffersInOfferBook());
        PB.Locale preferredLocale = envelope.getPreferences().getPreferredLocale();
        PB.TradeCurrency preferredTradeCurrency = envelope.getPreferences().getPreferredTradeCurrency();
        preferences.setPreferredTradeCurrency(getTradeCurrency(preferredTradeCurrency));
        preferences.setWithdrawalTxFeeInBytes(envelope.getPreferences().getWithdrawalTxFeeInBytes());
        preferences.setMaxPriceDistanceInPercent(envelope.getPreferences().getMaxPriceDistanceInPercent());
        preferences.setOfferBookChartScreenCurrencyCode(envelope.getPreferences().getOfferBookChartScreenCurrencyCode());
        preferences.setTradeChartsScreenCurrencyCode(envelope.getPreferences().getTradeChartsScreenCurrencyCode());
        preferences.setUseStickyMarketPrice(envelope.getPreferences().getUseStickyMarketPrice());
        preferences.setSortMarketCurrenciesNumerically(envelope.getPreferences().getSortMarketCurrenciesNumerically());
        preferences.setUsePercentageBasedPrice(envelope.getPreferences().getUsePercentageBasedPrice());
        preferences.setPeerTagMap(envelope.getPreferences().getPeerTagMapMap());
        preferences.setBitcoinNodes(envelope.getPreferences().getBitcoinNodes());
        preferences.setIgnoreTradersList(envelope.getPreferences().getIgnoreTradersListList());
        preferences.setDirectoryChooserPath(envelope.getPreferences().getDirectoryChooserPath());
        preferences.setBuyerSecurityDepositAsLong(envelope.getPreferences().getBuyerSecurityDepositAsLong());
    }

    private TradeCurrency getTradeCurrency(PB.TradeCurrency tradeCurrency) {
        switch (tradeCurrency.getMessageCase()) {
            case FIAT_CURRENCY:
                return new FiatCurrency(tradeCurrency.getCode(), getLocale(tradeCurrency.getFiatCurrency().getDefaultLocale()));
            case CRYPTO_CURRENCY:
                return new CryptoCurrency(tradeCurrency.getCode(), tradeCurrency.getName(), tradeCurrency.getSymbol(),
                        tradeCurrency.getCryptoCurrency().getIsAsset());
            default:
                log.warn("Unknown tradecurrency: {}", tradeCurrency.getMessageCase());
        }

        return null;
    }

    private Locale getLocale(PB.Locale locale) {
        return new Locale(locale.getLanguage(), locale.getCountry(), locale.getVariant());
    }

    private void addToAddressEntryList(PB.DiskEnvelope envelope) {
        envelope.getAddressEntryList().getAddressEntryList().stream().map(addressEntry -> addressEntryList.get().addAddressEntry(
                new AddressEntry(addressEntry.getPubKey().toByteArray(), addressEntry.getPubKeyHash().toByteArray(), addressEntry.getParamId(), AddressEntry.Context.valueOf(addressEntry.getContext().name()),
                        addressEntry.getOfferId(), Coin.valueOf(addressEntry.getCoinLockedInMultiSig().getValue()), addressEntryList.get().getKeyBagSupplier())));
    }


}
