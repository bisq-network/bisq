package io.bisq.core.proto;

import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.crypto.SealedAndSigned;
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
import io.bisq.core.dao.blockchain.p2p.GetBsqBlocksRequest;
import io.bisq.core.dao.blockchain.p2p.GetBsqBlocksResponse;
import io.bisq.core.dao.blockchain.p2p.NewBsqBlockBroadcastMsg;
import io.bisq.core.dao.compensation.CompensationRequestPayload;
import io.bisq.core.filter.Filter;
import io.bisq.core.offer.AvailabilityResult;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.offer.messages.OfferAvailabilityRequest;
import io.bisq.core.offer.messages.OfferAvailabilityResponse;
import io.bisq.core.payment.payload.PaymentAccountPayload;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.CollectionUtils;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

import static io.bisq.generated.protobuffer.PB.Msg.MessageCase.*;

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
    // Handle by Msg.MessagesCase
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Optional<Msg> fromProto(PB.Msg msg) {
        if (Objects.isNull(msg)) {
            log.warn("fromProtoBuf called with empty msg.");
            return Optional.empty();
        }
        if (msg.getMessageCase() != PING && msg.getMessageCase() != PONG &&
                msg.getMessageCase() != REFRESH_TTL_MESSAGE) {
            log.debug("Convert protobuffer msg: {}, {}", msg.getMessageCase(), msg.toString());
        } else {
            log.debug("Convert protobuffer msg: {}", msg.getMessageCase());
            log.trace("Convert protobuffer msg: {}", msg.toString());
        }

        Msg result = null;
        switch (msg.getMessageCase()) {
            case PING:
                result = getPing(msg);
                break;
            case PONG:
                result = getPong(msg);
                break;
            case REFRESH_TTL_MESSAGE:
                result = getRefreshTTLMessage(msg);
                break;
            case CLOSE_CONNECTION_MESSAGE:
                result = getCloseConnectionMessage(msg);
                break;
            case PRELIMINARY_GET_DATA_REQUEST:
                result = getPreliminaryGetDataRequest(msg);
                break;
            case GET_UPDATED_DATA_REQUEST:
                result = getGetUpdatedDataRequest(msg);
                break;
            case GET_PEERS_REQUEST:
                result = getGetPeersRequest(msg);
                break;
            case GET_PEERS_RESPONSE:
                result = getGetPeersResponse(msg);
                break;
            case GET_DATA_RESPONSE:
                result = getGetDataResponse(msg);
                break;
            case PREFIXED_SEALED_AND_SIGNED_MESSAGE:
                result = getPrefixedSealedAndSignedMessage(msg);
                break;
            case OFFER_AVAILABILITY_RESPONSE:
                result = getOfferAvailabilityResponse(msg);
                break;
            case OFFER_AVAILABILITY_REQUEST:
                result = getOfferAvailabilityRequest(msg);
                break;
            case GET_BSQ_BLOCKS_REQUEST:
                result = GetBsqBlocksRequest.fromProto(msg);
                break;
            case GET_BSQ_BLOCKS_RESPONSE:
                result = GetBsqBlocksResponse.fromProto(msg);
                break;
            case NEW_BSQ_BLOCK_BROADCAST_MSG:
                result = NewBsqBlockBroadcastMsg.fromProto(msg);
                break;
            case REMOVE_DATA_MESSAGE:
                result = getRemoveDataMessage(msg);
                break;
            case ADD_DATA_MESSAGE:
                result = getAddDataMessage(msg);
                break;
            case REMOVE_MAILBOX_DATA_MESSAGE:
                result = getRemoveMailBoxDataMessage(msg.getRemoveMailboxDataMessage());
                break;
            case DEPOSIT_TX_PUBLISHED_MESSAGE:
                result = getDepositTxPublishedMessage(msg.getDepositTxPublishedMessage());
                break;
            case FINALIZE_PAYOUT_TX_REQUEST:
                result = getFinalizePayoutTxRequest(msg.getFinalizePayoutTxRequest());
                break;
            case DISPUTE_COMMUNICATION_MESSAGE:
                result = getDisputeCommunicationMessage(msg.getDisputeCommunicationMessage());
                break;
            case OPEN_NEW_DISPUTE_MESSAGE:
                result = getOpenNewDisputeMessage(msg.getOpenNewDisputeMessage());
                break;
            case PEER_OPENED_DISPUTE_MESSAGE:
                result = getPeerOpenedDisputeMessage(msg.getPeerOpenedDisputeMessage());
                break;
            case DISPUTE_RESULT_MESSAGE:
                result = getDisputeResultMessage(msg.getDisputeResultMessage());
                break;
            case PEER_PUBLISHED_PAYOUT_TX_MESSAGE:
                result = getPeerPublishedPayoutTxMessage(msg.getPeerPublishedPayoutTxMessage());
                break;
            case PAY_DEPOSIT_REQUEST:
                result = getPayDepositRequest(msg.getPayDepositRequest());
                break;
            case PUBLISH_DEPOSIT_TX_REQUEST:
                result = getPublishDepositTxRequest(msg.getPublishDepositTxRequest());
                break;
            case FIAT_TRANSFER_STARTED_MESSAGE:
                result = getFiatTransferStartedMessage(msg.getFiatTransferStartedMessage());
                break;
            case PAYOUT_TX_PUBLISHED_MESSAGE:
                result = getPayoutTxPublishedMessage(msg.getPayoutTxPublishedMessage());
                break;
            case PRIVATE_NOTIFICATION_MESSAGE:
                result = getPrivateNotificationMessage(msg.getPrivateNotificationMessage());
                break;
            default:
                log.warn("Unknown message case:{}:{}", msg.getMessageCase());
        }
        return Optional.ofNullable(result);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Msg
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static Msg getOfferAvailabilityRequest(PB.Msg envelope) {
        PB.OfferAvailabilityRequest msg = envelope.getOfferAvailabilityRequest();
        return new OfferAvailabilityRequest(msg.getOfferId(), PubKeyRing.fromProto(msg.getPubKeyRing()), msg.getTakersTradePrice());
    }

    private static Msg getPrivateNotificationMessage(PB.PrivateNotificationMessage privateNotificationMessage) {
        return new PrivateNotificationMsg(getPrivateNotification(privateNotificationMessage.getPrivateNotificationPayload()),
                NodeAddress.fromProto(privateNotificationMessage.getMyNodeAddress()),
                privateNotificationMessage.getUid());
    }

    private static Msg getPayoutTxPublishedMessage(PB.PayoutTxPublishedMessage payoutTxPublishedMessage) {
        return new PayoutTxPublishedMsg(payoutTxPublishedMessage.getTradeId(),
                payoutTxPublishedMessage.getPayoutTx().toByteArray(),
                NodeAddress.fromProto(payoutTxPublishedMessage.getSenderNodeAddress()),
                payoutTxPublishedMessage.getUid());
    }

    private static Msg getOfferAvailabilityResponse(PB.Msg envelope) {
        PB.OfferAvailabilityResponse msg = envelope.getOfferAvailabilityResponse();
        return new OfferAvailabilityResponse(msg.getOfferId(),
                AvailabilityResult.valueOf(
                        PB.AvailabilityResult.forNumber(msg.getAvailabilityResult().getNumber()).name()));
    }


    @NotNull
    private static Msg getPrefixedSealedAndSignedMessage(PB.Msg envelope) {
        return getPrefixedSealedAndSignedMessage(envelope.getPrefixedSealedAndSignedMessage());
    }

    private static Msg getFiatTransferStartedMessage(PB.FiatTransferStartedMessage fiatTransferStartedMessage) {
        return new FiatTransferStartedMsg(fiatTransferStartedMessage.getTradeId(),
                fiatTransferStartedMessage.getBuyerPayoutAddress(),
                NodeAddress.fromProto(fiatTransferStartedMessage.getSenderNodeAddress()),
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
                PaymentAccountPayload.fromProto(publishDepositTxRequest.getMakerPaymentAccountPayload()),
                publishDepositTxRequest.getMakerAccountId(),
                publishDepositTxRequest.getMakerMultiSigPubKey().toByteArray(),
                publishDepositTxRequest.getMakerContractAsJson(),
                publishDepositTxRequest.getMakerContractSignature(),
                publishDepositTxRequest.getMakerPayoutAddressString(),
                publishDepositTxRequest.getPreparedDepositTx().toByteArray(),
                rawTransactionInputs,
                NodeAddress.fromProto(publishDepositTxRequest.getSenderNodeAddress()),
                publishDepositTxRequest.getUid());
    }

    private static Msg getPayDepositRequest(PB.PayDepositRequest payDepositRequest) {
        List<RawTransactionInput> rawTransactionInputs = payDepositRequest.getRawTransactionInputsList().stream()
                .map(rawTransactionInput -> new RawTransactionInput(rawTransactionInput.getIndex(),
                        rawTransactionInput.getParentTransaction().toByteArray(), rawTransactionInput.getValue()))
                .collect(Collectors.toList());
        List<NodeAddress> arbitratorNodeAddresses = payDepositRequest.getAcceptedArbitratorNodeAddressesList().stream()
                .map(NodeAddress::fromProto).collect(Collectors.toList());
        List<NodeAddress> mediatorNodeAddresses = payDepositRequest.getAcceptedMediatorNodeAddressesList().stream()
                .map(NodeAddress::fromProto).collect(Collectors.toList());
        return new PayDepositRequest(NodeAddress.fromProto(payDepositRequest.getSenderNodeAddress()),
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
                PubKeyRing.fromProto(payDepositRequest.getTakerPubKeyRing()),
                PaymentAccountPayload.fromProto(payDepositRequest.getTakerPaymentAccountPayload()),
                payDepositRequest.getTakerAccountId(),
                payDepositRequest.getTakerFeeTxId(),
                arbitratorNodeAddresses,
                mediatorNodeAddresses,
                NodeAddress.fromProto(payDepositRequest.getArbitratorNodeAddress()),
                NodeAddress.fromProto(payDepositRequest.getMediatorNodeAddress()));
    }

    private static Msg getPeerPublishedPayoutTxMessage(PB.PeerPublishedPayoutTxMessage peerPublishedPayoutTxMessage) {
        return new PeerPublishedPayoutTxMsg(peerPublishedPayoutTxMessage.getTransaction().toByteArray(),
                peerPublishedPayoutTxMessage.getTradeId(),
                NodeAddress.fromProto(peerPublishedPayoutTxMessage.getMyNodeAddress()),
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
                NodeAddress.fromProto(disputeResultMessage.getMyNodeAddress()),
                disputeResultMessage.getUid());
    }

    private static Msg getPeerOpenedDisputeMessage(PB.PeerOpenedDisputeMessage peerOpenedDisputeMessage) {
        return new PeerOpenedDisputeMsg(ProtoUtil.getDispute(peerOpenedDisputeMessage.getDispute()),
                NodeAddress.fromProto(peerOpenedDisputeMessage.getMyNodeAddress()), peerOpenedDisputeMessage.getUid());
    }

    private static Msg getOpenNewDisputeMessage(PB.OpenNewDisputeMessage openNewDisputeMessage) {
        return new OpenNewDisputeMsg(ProtoUtil.getDispute(openNewDisputeMessage.getDispute()),
                NodeAddress.fromProto(openNewDisputeMessage.getMyNodeAddress()), openNewDisputeMessage.getUid());
    }

    private static Msg getDisputeCommunicationMessage(PB.DisputeCommunicationMessage disputeCommunicationMessage) {
        return new DisputeCommunicationMsg(disputeCommunicationMessage.getTradeId(),
                disputeCommunicationMessage.getTraderId(),
                disputeCommunicationMessage.getSenderIsTrader(),
                disputeCommunicationMessage.getMessage(),
                disputeCommunicationMessage.getAttachmentsList().stream()
                        .map(attachment -> new Attachment(attachment.getFileName(), attachment.getBytes().toByteArray()))
                        .collect(Collectors.toList()),
                NodeAddress.fromProto(disputeCommunicationMessage.getMyNodeAddress()),
                disputeCommunicationMessage.getDate(),
                disputeCommunicationMessage.getArrived(),
                disputeCommunicationMessage.getStoredInMailbox(),
                disputeCommunicationMessage.getUid());
    }

    private static Msg getFinalizePayoutTxRequest(PB.FinalizePayoutTxRequest finalizePayoutTxRequest) {
        return new FinalizePayoutTxRequest(finalizePayoutTxRequest.getTradeId(),
                finalizePayoutTxRequest.getSellerSignature().toByteArray(),
                finalizePayoutTxRequest.getSellerPayoutAddress(),
                NodeAddress.fromProto(finalizePayoutTxRequest.getSenderNodeAddress()),
                finalizePayoutTxRequest.getUid());
    }

    private static Msg getDepositTxPublishedMessage(PB.DepositTxPublishedMessage depositTxPublishedMessage) {
        return new DepositTxPublishedMsg(depositTxPublishedMessage.getTradeId(),
                depositTxPublishedMessage.getDepositTx().toByteArray(),
                NodeAddress.fromProto(depositTxPublishedMessage.getSenderNodeAddress()), depositTxPublishedMessage.getUid());
    }

    private static Msg getRemoveMailBoxDataMessage(PB.RemoveMailboxDataMessage msg) {
        return new RemoveMailboxDataMsg(getProtectedMailBoxStorageEntry(msg.getProtectedStorageEntry()));
    }

    public static Msg getAddDataMessage(PB.Msg envelope) {
        return new AddDataMsg(getProtectedOrMailboxStorageEntry(envelope.getAddDataMessage().getEntry()));
    }

    private static Msg getRemoveDataMessage(PB.Msg envelope) {
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
    private static Msg getGetDataResponse(PB.Msg envelope) {
        HashSet<ProtectedStorageEntry> set = new HashSet<>(
                envelope.getGetDataResponse().getDataSetList()
                        .stream()
                        .map(protectedStorageEntry ->
                                getProtectedOrMailboxStorageEntry(protectedStorageEntry)).collect(Collectors.toList()));
        return new GetDataResponse(set, envelope.getGetDataResponse().getRequestNonce(),
                envelope.getGetDataResponse().getIsGetUpdatedDataResponse());
    }

    @NotNull
    private static Msg getGetPeersResponse(PB.Msg envelope) {
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
    private static Msg getGetPeersRequest(PB.Msg envelope) {
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
    private static Msg getGetUpdatedDataRequest(PB.Msg envelope) {
        NodeAddress nodeAddress;
        Msg result;
        PB.GetUpdatedDataRequest msg = envelope.getGetUpdatedDataRequest();
        nodeAddress = new NodeAddress(msg.getSenderNodeAddress().getHostName(), msg.getSenderNodeAddress().getPort());
        Set<byte[]> updatedDataRequestSet = ProtoUtil.getByteSet(msg.getExcludedKeysList());
        result = new GetUpdatedDataRequest(nodeAddress, msg.getNonce(), updatedDataRequestSet);
        return result;
    }

    @NotNull
    private static Msg getPreliminaryGetDataRequest(PB.Msg envelope) {
        Msg result;
        result = new PreliminaryGetDataRequest(envelope.getPreliminaryGetDataRequest().getNonce(),
                ProtoUtil.getByteSet(envelope.getPreliminaryGetDataRequest().getExcludedKeysList()));
        return result;
    }

    @NotNull
    private static Msg getCloseConnectionMessage(PB.Msg msg) {
        return new CloseConnectionMsg(msg.getCloseConnectionMessage().getReason());
    }

    @NotNull
    private static Msg getRefreshTTLMessage(PB.Msg envelope) {
        Msg result;
        PB.RefreshTTLMessage msg = envelope.getRefreshTtlMessage();
        result = new RefreshTTLMsg(msg.getHashOfDataAndSeqNr().toByteArray(),
                msg.getSignature().toByteArray(), msg.getHashOfPayload().toByteArray(), msg.getSequenceNumber());
        return result;
    }

    @NotNull
    private static Msg getPong(PB.Msg envelope) {
        Msg result;
        result = new Pong(envelope.getPong().getRequestNonce());
        return result;
    }

    @NotNull
    private static Msg getPing(PB.Msg envelope) {
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
                storagePayload = Alert.fromProto(protoEntry.getAlert());
                break;
            case ARBITRATOR:
                storagePayload = Arbitrator.fromProto(protoEntry.getArbitrator());
                break;
            case MEDIATOR:
                storagePayload = Mediator.fromProto(protoEntry.getMediator());
                break;
            case FILTER:
                storagePayload = Filter.fromProto(protoEntry.getFilter());
                break;
            case COMPENSATION_REQUEST_PAYLOAD:
                storagePayload = CompensationRequestPayload.fromProto(protoEntry.getCompensationRequestPayload());
                break;
            case TRADE_STATISTICS:
                storagePayload = TradeStatistics.fromProto(protoEntry.getTradeStatistics());
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
                storagePayload = OfferPayload.fromProto(protoEntry.getOfferPayload());
                break;
            default:
                log.error("Unknown storagepayload:{}", protoEntry.getMessageCase());
        }
        return storagePayload;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Payload
    ///////////////////////////////////////////////////////////////////////////////////////////



    private static PrivateNotificationPayload getPrivateNotification(PB.PrivateNotificationPayload privateNotification) {
        return new PrivateNotificationPayload(privateNotification.getMessage());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handle by PaymentAccountPayload.MessageCase
    ///////////////////////////////////////////////////////////////////////////////////////////



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
