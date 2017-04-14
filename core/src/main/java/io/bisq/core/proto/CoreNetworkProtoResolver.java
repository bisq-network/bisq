package io.bisq.core.proto;

import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.crypto.SealedAndSigned;
import io.bisq.common.locale.CountryUtil;
import io.bisq.common.network.Msg;
import io.bisq.common.proto.NetworkProtoResolver;
import io.bisq.core.alert.Alert;
import io.bisq.core.alert.PrivateNotificationMsg;
import io.bisq.core.alert.PrivateNotificationPayload;
import io.bisq.core.arbitration.Arbitrator;
import io.bisq.core.arbitration.Attachment;
import io.bisq.core.arbitration.DisputeResult;
import io.bisq.core.arbitration.Mediator;
import io.bisq.core.arbitration.messages.*;
import io.bisq.core.btc.data.RawTransactionInput;
import io.bisq.core.dao.compensation.CompensationRequestPayload;
import io.bisq.core.filter.Filter;
import io.bisq.core.filter.PaymentAccountFilter;
import io.bisq.core.offer.AvailabilityResult;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.offer.messages.OfferAvailabilityRequest;
import io.bisq.core.offer.messages.OfferAvailabilityResponse;
import io.bisq.core.payment.payload.*;
import io.bisq.core.trade.messages.*;
import io.bisq.core.trade.statistics.TradeStatistics;
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

import javax.inject.Inject;
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
public class CoreNetworkProtoResolver implements NetworkProtoResolver {

    @Inject
    public CoreNetworkProtoResolver() {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handle by Envelope.MessagesCase
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Optional<Msg> fromProto(PB.Envelope envelope) {
        if (Objects.isNull(envelope)) {
            log.warn("fromProtoBuf called with empty envelope.");
            return Optional.empty();
        }
        if (envelope.getMessageCase() != PING && envelope.getMessageCase() != PONG &&
                envelope.getMessageCase() != REFRESH_TTL_MESSAGE) {
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Msg
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static Msg getOfferAvailabilityRequest(PB.Envelope envelope) {
        PB.OfferAvailabilityRequest msg = envelope.getOfferAvailabilityRequest();
        return new OfferAvailabilityRequest(msg.getOfferId(), ProtoUtil.getPubKeyRing(msg.getPubKeyRing()), msg.getTakersTradePrice());

    }

    private static Msg getPrivateNotificationMessage(PB.PrivateNotificationMessage privateNotificationMessage) {
        return new PrivateNotificationMsg(getPrivateNotification(privateNotificationMessage.getPrivateNotificationPayload()),
                ProtoUtil.getNodeAddress(privateNotificationMessage.getMyNodeAddress()),
                privateNotificationMessage.getUid());
    }

    private static Msg getPayoutTxPublishedMessage(PB.PayoutTxPublishedMessage payoutTxPublishedMessage) {
        return new PayoutTxPublishedMsg(payoutTxPublishedMessage.getTradeId(),
                payoutTxPublishedMessage.getPayoutTx().toByteArray(),
                ProtoUtil.getNodeAddress(payoutTxPublishedMessage.getSenderNodeAddress()),
                payoutTxPublishedMessage.getUid());
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

    private static Msg getFiatTransferStartedMessage(PB.FiatTransferStartedMessage fiatTransferStartedMessage) {
        return new FiatTransferStartedMsg(fiatTransferStartedMessage.getTradeId(),
                fiatTransferStartedMessage.getBuyerPayoutAddress(),
                ProtoUtil.getNodeAddress(fiatTransferStartedMessage.getSenderNodeAddress()),
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
                ProtoUtil.getNodeAddress(publishDepositTxRequest.getSenderNodeAddress()),
                publishDepositTxRequest.getUid());
    }

    private static Msg getPayDepositRequest(PB.PayDepositRequest payDepositRequest) {
        List<RawTransactionInput> rawTransactionInputs = payDepositRequest.getRawTransactionInputsList().stream()
                .map(rawTransactionInput -> new RawTransactionInput(rawTransactionInput.getIndex(),
                        rawTransactionInput.getParentTransaction().toByteArray(), rawTransactionInput.getValue()))
                .collect(Collectors.toList());
        List<NodeAddress> arbitratorNodeAddresses = payDepositRequest.getAcceptedArbitratorNodeAddressesList().stream()
                .map(ProtoUtil::getNodeAddress).collect(Collectors.toList());
        List<NodeAddress> mediatorNodeAddresses = payDepositRequest.getAcceptedMediatorNodeAddressesList().stream()
                .map(ProtoUtil::getNodeAddress).collect(Collectors.toList());
        return new PayDepositRequest(ProtoUtil.getNodeAddress(payDepositRequest.getSenderNodeAddress()),
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
                ProtoUtil.getPubKeyRing(payDepositRequest.getTakerPubKeyRing()),
                getPaymentAccountPayload(payDepositRequest.getTakerPaymentAccountPayload()),
                payDepositRequest.getTakerAccountId(),
                payDepositRequest.getTakerFeeTxId(),
                arbitratorNodeAddresses,
                mediatorNodeAddresses,
                ProtoUtil.getNodeAddress(payDepositRequest.getArbitratorNodeAddress()),
                ProtoUtil.getNodeAddress(payDepositRequest.getMediatorNodeAddress()));
    }

    private static Msg getPeerPublishedPayoutTxMessage(PB.PeerPublishedPayoutTxMessage peerPublishedPayoutTxMessage) {
        return new PeerPublishedPayoutTxMsg(peerPublishedPayoutTxMessage.getTransaction().toByteArray(),
                peerPublishedPayoutTxMessage.getTradeId(),
                ProtoUtil.getNodeAddress(peerPublishedPayoutTxMessage.getMyNodeAddress()),
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
                ProtoUtil.getNodeAddress(disputeResultMessage.getMyNodeAddress()),
                disputeResultMessage.getUid());
    }

    private static Msg getPeerOpenedDisputeMessage(PB.PeerOpenedDisputeMessage peerOpenedDisputeMessage) {
        return new PeerOpenedDisputeMsg(ProtoUtil.getDispute(peerOpenedDisputeMessage.getDispute()),
                ProtoUtil.getNodeAddress(peerOpenedDisputeMessage.getMyNodeAddress()), peerOpenedDisputeMessage.getUid());
    }

    private static Msg getOpenNewDisputeMessage(PB.OpenNewDisputeMessage openNewDisputeMessage) {
        return new OpenNewDisputeMsg(ProtoUtil.getDispute(openNewDisputeMessage.getDispute()),
                ProtoUtil.getNodeAddress(openNewDisputeMessage.getMyNodeAddress()), openNewDisputeMessage.getUid());
    }

    private static Msg getDisputeCommunicationMessage(PB.DisputeCommunicationMessage disputeCommunicationMessage) {
        return new DisputeCommunicationMsg(disputeCommunicationMessage.getTradeId(),
                disputeCommunicationMessage.getTraderId(),
                disputeCommunicationMessage.getSenderIsTrader(),
                disputeCommunicationMessage.getMessage(),
                disputeCommunicationMessage.getAttachmentsList().stream()
                        .map(attachment -> new Attachment(attachment.getFileName(), attachment.getBytes().toByteArray()))
                        .collect(Collectors.toList()),
                ProtoUtil.getNodeAddress(disputeCommunicationMessage.getMyNodeAddress()),
                disputeCommunicationMessage.getDate(),
                disputeCommunicationMessage.getArrived(),
                disputeCommunicationMessage.getStoredInMailbox(),
                disputeCommunicationMessage.getUid());
    }

    private static Msg getFinalizePayoutTxRequest(PB.FinalizePayoutTxRequest finalizePayoutTxRequest) {
        return new FinalizePayoutTxRequest(finalizePayoutTxRequest.getTradeId(),
                finalizePayoutTxRequest.getSellerSignature().toByteArray(),
                finalizePayoutTxRequest.getSellerPayoutAddress(),
                ProtoUtil.getNodeAddress(finalizePayoutTxRequest.getSenderNodeAddress()),
                finalizePayoutTxRequest.getUid());
    }

    private static Msg getDepositTxPublishedMessage(PB.DepositTxPublishedMessage depositTxPublishedMessage) {
        return new DepositTxPublishedMsg(depositTxPublishedMessage.getTradeId(),
                depositTxPublishedMessage.getDepositTx().toByteArray(),
                ProtoUtil.getNodeAddress(depositTxPublishedMessage.getSenderNodeAddress()), depositTxPublishedMessage.getUid());
    }

    private static Msg getRemoveMailBoxDataMessage(PB.RemoveMailboxDataMessage msg) {
        return new RemoveMailboxDataMsg(getProtectedMailBoxStorageEntry(msg.getProtectedStorageEntry()));
    }

    public static Msg getAddDataMessage(PB.Envelope envelope) {
        return new AddDataMsg(getProtectedOrMailboxStorageEntry(envelope.getAddDataMessage().getEntry()));
    }

    private static Msg getRemoveDataMessage(PB.Envelope envelope) {
        return new RemoveDataMsg(getProtectedStorageEntry(envelope.getRemoveDataMessage().getProtectedStorageEntry()));
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
        return new GetDataResponse(set, envelope.getGetDataResponse().getRequestNonce(),
                envelope.getGetDataResponse().getIsGetUpdatedDataResponse());
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
        Set<byte[]> updatedDataRequestSet = ProtoUtil.getByteSet(msg.getExcludedKeysList());
        result = new GetUpdatedDataRequest(nodeAddress, msg.getNonce(), updatedDataRequestSet);
        return result;
    }

    @NotNull
    private static Msg getPreliminaryGetDataRequest(PB.Envelope envelope) {
        Msg result;
        result = new PreliminaryGetDataRequest(envelope.getPreliminaryGetDataRequest().getNonce(),
                ProtoUtil.getByteSet(envelope.getPreliminaryGetDataRequest().getExcludedKeysList()));
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handle by StoragePayload.MessageCase
    ///////////////////////////////////////////////////////////////////////////////////////////

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
                storagePayload = new Arbitrator(ProtoUtil.getNodeAddress(arbitrator.getNodeAddress()),
                        arbitrator.getBtcPubKey().toByteArray(),
                        arbitrator.getBtcAddress(),
                        ProtoUtil.getPubKeyRing(arbitrator.getPubKeyRing()),
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
                storagePayload = new Mediator(ProtoUtil.getNodeAddress(mediator.getNodeAddress()),
                        ProtoUtil.getPubKeyRing(mediator.getPubKeyRing()),
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
                        .stream().map(accountFilter -> ProtoUtil.getPaymentAccountFilter(accountFilter)).collect(Collectors.toList());
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Payload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @NotNull
    public static OKPayAccountPayload getOkPayAccountPayload(PB.PaymentAccountPayload protoEntry) {
        return new OKPayAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                protoEntry.getMaxTradePeriod(), protoEntry.getOKPayAccountPayload().getAccountNr());
    }

    private static PrivateNotificationPayload getPrivateNotification(PB.PrivateNotificationPayload privateNotification) {
        return new PrivateNotificationPayload(privateNotification.getMessage());
    }

    public static OfferPayload getOfferPayload(PB.OfferPayload pbOffer) {
        List<NodeAddress> arbitratorNodeAddresses = pbOffer.getArbitratorNodeAddressesList().stream()
                .map(ProtoUtil::getNodeAddress).collect(Collectors.toList());
        List<NodeAddress> mediatorNodeAddresses = pbOffer.getMediatorNodeAddressesList().stream()
                .map(ProtoUtil::getNodeAddress).collect(Collectors.toList());

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
                ProtoUtil.getNodeAddress(pbOffer.getMakerNodeAddress()),
                ProtoUtil.getPubKeyRing(pbOffer.getPubKeyRing()),
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

    @NotNull
    public static OfferPayload.Direction getDirection(PB.OfferPayload.Direction direction) {
        return OfferPayload.Direction.valueOf(direction.name());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handle by PaymentAccountPayload.MessageCase
    ///////////////////////////////////////////////////////////////////////////////////////////

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
                                ProtoUtil.fillInBankAccountPayload(protoEntry, nationalBankAccountPayload);
                                ProtoUtil.fillInCountryBasedPaymentAccountPayload(protoEntry, nationalBankAccountPayload);
                                result = nationalBankAccountPayload;
                                break;
                            case SAME_BANK_ACCONT_PAYLOAD:
                                SameBankAccountPayload sameBankAccountPayload = new SameBankAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                                        protoEntry.getMaxTradePeriod());
                                ProtoUtil.fillInBankAccountPayload(protoEntry, sameBankAccountPayload);
                                ProtoUtil.fillInCountryBasedPaymentAccountPayload(protoEntry, sameBankAccountPayload);
                                result = sameBankAccountPayload;
                                break;
                            case SPECIFIC_BANKS_ACCOUNT_PAYLOAD:
                                SpecificBanksAccountPayload specificBanksAccountPayload = new SpecificBanksAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                                        protoEntry.getMaxTradePeriod());
                                ProtoUtil.fillInBankAccountPayload(protoEntry, specificBanksAccountPayload);
                                ProtoUtil.fillInCountryBasedPaymentAccountPayload(protoEntry, specificBanksAccountPayload);
                                result = specificBanksAccountPayload;
                                break;
                        }
                        break;
                    case CASH_DEPOSIT_ACCOUNT_PAYLOAD:
                        CashDepositAccountPayload cashDepositAccountPayload = new CashDepositAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                                protoEntry.getMaxTradePeriod());
                        ProtoUtil.fillInCountryBasedPaymentAccountPayload(protoEntry, cashDepositAccountPayload);
                        result = cashDepositAccountPayload;
                        break;
                    case SEPA_ACCOUNT_PAYLOAD:
                        SepaAccountPayload sepaAccountPayload = new SepaAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                                protoEntry.getMaxTradePeriod(), CountryUtil.getAllSepaCountries());
                        ProtoUtil.fillInCountryBasedPaymentAccountPayload(protoEntry, sepaAccountPayload);
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handle by ProtectedStorageEntryOrProtectedMailboxStorageEntry.MessageCase
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static ProtectedStorageEntry getProtectedOrMailboxStorageEntry(PB.ProtectedStorageEntryOrProtectedMailboxStorageEntry entry) {
        if (entry.getMessageCase() == PB.ProtectedStorageEntryOrProtectedMailboxStorageEntry.MessageCase.PROTECTED_MAILBOX_STORAGE_ENTRY) {
            return getProtectedMailBoxStorageEntry(entry.getProtectedMailboxStorageEntry());
        } else {
            return getProtectedStorageEntry(entry.getProtectedStorageEntry());
        }
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
}
