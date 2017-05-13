package io.bisq.core.proto;

import io.bisq.common.network.NetworkEnvelope;
import io.bisq.common.network.NetworkPayload;
import io.bisq.common.proto.NetworkProtoResolver;
import io.bisq.core.alert.Alert;
import io.bisq.core.alert.PrivateNotificationMessage;
import io.bisq.core.arbitration.Arbitrator;
import io.bisq.core.arbitration.Mediator;
import io.bisq.core.arbitration.messages.*;
import io.bisq.core.dao.blockchain.p2p.GetBsqBlocksRequest;
import io.bisq.core.dao.blockchain.p2p.GetBsqBlocksResponse;
import io.bisq.core.dao.blockchain.p2p.NewBsqBlockBroadcastMessage;
import io.bisq.core.dao.compensation.CompensationRequestPayload;
import io.bisq.core.filter.Filter;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.offer.messages.OfferAvailabilityRequest;
import io.bisq.core.offer.messages.OfferAvailabilityResponse;
import io.bisq.core.trade.messages.*;
import io.bisq.core.trade.statistics.TradeStatistics;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.CloseConnectionMessage;
import io.bisq.network.p2p.PrefixedSealedAndSignedMessage;
import io.bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import io.bisq.network.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import io.bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import io.bisq.network.p2p.peers.keepalive.messages.Ping;
import io.bisq.network.p2p.peers.keepalive.messages.Pong;
import io.bisq.network.p2p.peers.peerexchange.messages.GetPeersRequest;
import io.bisq.network.p2p.peers.peerexchange.messages.GetPeersResponse;
import io.bisq.network.p2p.storage.messages.AddDataMessage;
import io.bisq.network.p2p.storage.messages.RefreshOfferMessage;
import io.bisq.network.p2p.storage.messages.RemoveDataMessage;
import io.bisq.network.p2p.storage.messages.RemoveMailboxDataMessage;
import io.bisq.network.p2p.storage.payload.MailboxStoragePayload;
import io.bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

import static io.bisq.generated.protobuffer.PB.NetworkEnvelope.MessageCase.*;

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
    public NetworkEnvelope fromProto(PB.NetworkEnvelope networkEnvelope) {
        final PB.NetworkEnvelope.MessageCase messageCase = networkEnvelope.getMessageCase();
        if (messageCase != PING && messageCase != PONG &&
                messageCase != REFRESH_OFFER_MESSAGE) {
            log.debug("Convert protobuffer networkEnvelope: {}, {}", messageCase, networkEnvelope.toString());
        } else {
            log.debug("Convert protobuffer networkEnvelope: {}", messageCase);
            log.trace("Convert protobuffer networkEnvelope: {}", networkEnvelope.toString());
        }

        switch (messageCase) {
            case PRELIMINARY_GET_DATA_REQUEST:
                return PreliminaryGetDataRequest.fromProto(networkEnvelope.getPreliminaryGetDataRequest());
            case GET_DATA_RESPONSE:
                return GetDataResponse.fromProto(networkEnvelope.getGetDataResponse(), this);
            case GET_UPDATED_DATA_REQUEST:
                return GetUpdatedDataRequest.fromProto(networkEnvelope.getGetUpdatedDataRequest());

            case GET_PEERS_REQUEST:
                return GetPeersRequest.fromProto(networkEnvelope.getGetPeersRequest());
            case GET_PEERS_RESPONSE:
                return GetPeersResponse.fromProto(networkEnvelope.getGetPeersResponse());
            case PING:
                return Ping.fromProto(networkEnvelope.getPing());
            case PONG:
                return Pong.fromProto(networkEnvelope.getPong());

            case OFFER_AVAILABILITY_REQUEST:
                return OfferAvailabilityRequest.fromProto(networkEnvelope.getOfferAvailabilityRequest());
            case OFFER_AVAILABILITY_RESPONSE:
                return OfferAvailabilityResponse.fromProto(networkEnvelope.getOfferAvailabilityResponse());
            case REFRESH_OFFER_MESSAGE:
                return RefreshOfferMessage.fromProto(networkEnvelope.getRefreshOfferMessage());

            case ADD_DATA_MESSAGE:
                return AddDataMessage.fromProto(networkEnvelope.getAddDataMessage(), this);
            case REMOVE_DATA_MESSAGE:
                return RemoveDataMessage.fromProto(networkEnvelope.getRemoveDataMessage(), this);
            case REMOVE_MAILBOX_DATA_MESSAGE:
                return RemoveMailboxDataMessage.fromProto(networkEnvelope.getRemoveMailboxDataMessage(), this);

            case CLOSE_CONNECTION_MESSAGE:
                return CloseConnectionMessage.fromProto(networkEnvelope.getCloseConnectionMessage());
            case PREFIXED_SEALED_AND_SIGNED_MESSAGE:
                return PrefixedSealedAndSignedMessage.fromProto(networkEnvelope.getPrefixedSealedAndSignedMessage());

            case PAY_DEPOSIT_REQUEST:
                return PayDepositRequest.fromProto(networkEnvelope.getPayDepositRequest());
            case DEPOSIT_TX_PUBLISHED_MESSAGE:
                return DepositTxPublishedMessage.fromProto(networkEnvelope.getDepositTxPublishedMessage());
            case PUBLISH_DEPOSIT_TX_REQUEST:
                return PublishDepositTxRequest.fromProto(networkEnvelope.getPublishDepositTxRequest());
            case FIAT_TRANSFER_STARTED_MESSAGE:
                return FiatTransferStartedMessage.fromProto(networkEnvelope.getFiatTransferStartedMessage());
            case FINALIZE_PAYOUT_TX_REQUEST:
                return FinalizePayoutTxRequest.fromProto(networkEnvelope.getFinalizePayoutTxRequest());
            case PAYOUT_TX_PUBLISHED_MESSAGE:
                return PayoutTxPublishedMessage.fromProto(networkEnvelope.getPayoutTxPublishedMessage());

            case OPEN_NEW_DISPUTE_MESSAGE:
                return OpenNewDisputeMessage.fromProto(networkEnvelope.getOpenNewDisputeMessage());
            case PEER_OPENED_DISPUTE_MESSAGE:
                return PeerOpenedDisputeMessage.fromProto(networkEnvelope.getPeerOpenedDisputeMessage());
            case DISPUTE_COMMUNICATION_MESSAGE:
                return DisputeCommunicationMessage.fromProto(networkEnvelope.getDisputeCommunicationMessage());
            case DISPUTE_RESULT_MESSAGE:
                return DisputeResultMessage.fromProto(networkEnvelope.getDisputeResultMessage());
            case PEER_PUBLISHED_PAYOUT_TX_MESSAGE:
                return PeerPublishedPayoutTxMessage.fromProto(networkEnvelope.getPeerPublishedPayoutTxMessage());

            case PRIVATE_NOTIFICATION_MESSAGE:
                return PrivateNotificationMessage.fromProto(networkEnvelope.getPrivateNotificationMessage());

            case GET_BSQ_BLOCKS_REQUEST:
                return GetBsqBlocksRequest.fromProto(networkEnvelope.getGetBsqBlocksRequest());
            case GET_BSQ_BLOCKS_RESPONSE:
                return GetBsqBlocksResponse.fromProto(networkEnvelope.getGetBsqBlocksResponse());
            case NEW_BSQ_BLOCK_BROADCAST_MESSAGE:
                return NewBsqBlockBroadcastMessage.fromProto(networkEnvelope.getNewBsqBlockBroadcastMessage());

            default:
                log.error("Unknown message case: {}", messageCase);
                throw new RuntimeException("Unknown proto message case. messageCase=" + messageCase);
        }
    }

    public NetworkPayload mapToProtectedStorageEntry(PB.ProtectedStorageEntryOrProtectedMailboxStorageEntry proto) {
        switch (proto.getMessageCase()) {
            case PROTECTED_MAILBOX_STORAGE_ENTRY:
                return ProtectedMailboxStorageEntry.fromProto(proto.getProtectedMailboxStorageEntry(), this);
            case PROTECTED_STORAGE_ENTRY:
                return ProtectedStorageEntry.fromProto(proto.getProtectedStorageEntry(), this);
            default:
                log.error("Unknown message case: {}", proto.getMessageCase());
                throw new RuntimeException("Unknown proto message case. messageCase=" + proto.getMessageCase());
        }
    }

    public NetworkPayload fromStoragePayloadProto(PB.StoragePayload storagePayloadProto) {
        switch (storagePayloadProto.getMessageCase()) {
            case ALERT:
                return Alert.fromProto(storagePayloadProto.getAlert());
            case ARBITRATOR:
                return Arbitrator.fromProto(storagePayloadProto.getArbitrator());
            case MEDIATOR:
                return Mediator.fromProto(storagePayloadProto.getMediator());
            case FILTER:
                return Filter.fromProto(storagePayloadProto.getFilter());
            case COMPENSATION_REQUEST_PAYLOAD:
                return CompensationRequestPayload.fromProto(storagePayloadProto.getCompensationRequestPayload());
            case TRADE_STATISTICS:
                return TradeStatistics.fromProto(storagePayloadProto.getTradeStatistics());
            case MAILBOX_STORAGE_PAYLOAD:
                return MailboxStoragePayload.fromProto(storagePayloadProto.getMailboxStoragePayload(), this);
            case OFFER_PAYLOAD:
                return OfferPayload.fromProto(storagePayloadProto.getOfferPayload());
            default:
                log.error("Unknown StoragePayload:{}", storagePayloadProto.getMessageCase());
                throw new RuntimeException("Unknown proto message case. messageCase=" + storagePayloadProto.getMessageCase());
        }
    }
}
