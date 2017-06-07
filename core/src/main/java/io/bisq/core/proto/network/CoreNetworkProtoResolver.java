package io.bisq.core.proto.network;

import io.bisq.common.proto.ProtobufferException;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.common.proto.network.NetworkProtoResolver;
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
import io.bisq.core.proto.CoreProtoResolver;
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

@Slf4j
public class CoreNetworkProtoResolver extends CoreProtoResolver implements NetworkProtoResolver {

    @Inject
    public CoreNetworkProtoResolver() {
    }

    @Override
    public NetworkEnvelope fromProto(PB.NetworkEnvelope proto) {
        switch (proto.getMessageCase()) {
            case PRELIMINARY_GET_DATA_REQUEST:
                return PreliminaryGetDataRequest.fromProto(proto.getPreliminaryGetDataRequest());
            case GET_DATA_RESPONSE:
                return GetDataResponse.fromProto(proto.getGetDataResponse(), this);
            case GET_UPDATED_DATA_REQUEST:
                return GetUpdatedDataRequest.fromProto(proto.getGetUpdatedDataRequest());

            case GET_PEERS_REQUEST:
                return GetPeersRequest.fromProto(proto.getGetPeersRequest());
            case GET_PEERS_RESPONSE:
                return GetPeersResponse.fromProto(proto.getGetPeersResponse());
            case PING:
                return Ping.fromProto(proto.getPing());
            case PONG:
                return Pong.fromProto(proto.getPong());

            case OFFER_AVAILABILITY_REQUEST:
                return OfferAvailabilityRequest.fromProto(proto.getOfferAvailabilityRequest());
            case OFFER_AVAILABILITY_RESPONSE:
                return OfferAvailabilityResponse.fromProto(proto.getOfferAvailabilityResponse());
            case REFRESH_OFFER_MESSAGE:
                return RefreshOfferMessage.fromProto(proto.getRefreshOfferMessage());

            case ADD_DATA_MESSAGE:
                return AddDataMessage.fromProto(proto.getAddDataMessage(), this);
            case REMOVE_DATA_MESSAGE:
                return RemoveDataMessage.fromProto(proto.getRemoveDataMessage(), this);
            case REMOVE_MAILBOX_DATA_MESSAGE:
                return RemoveMailboxDataMessage.fromProto(proto.getRemoveMailboxDataMessage(), this);

            case CLOSE_CONNECTION_MESSAGE:
                return CloseConnectionMessage.fromProto(proto.getCloseConnectionMessage());
            case PREFIXED_SEALED_AND_SIGNED_MESSAGE:
                return PrefixedSealedAndSignedMessage.fromProto(proto.getPrefixedSealedAndSignedMessage());

            case PAY_DEPOSIT_REQUEST:
                return PayDepositRequest.fromProto(proto.getPayDepositRequest(), this);
            case DEPOSIT_TX_PUBLISHED_MESSAGE:
                return DepositTxPublishedMessage.fromProto(proto.getDepositTxPublishedMessage());
            case PUBLISH_DEPOSIT_TX_REQUEST:
                return PublishDepositTxRequest.fromProto(proto.getPublishDepositTxRequest(), this);
            case FIAT_TRANSFER_STARTED_MESSAGE:
                return FiatTransferStartedMessage.fromProto(proto.getFiatTransferStartedMessage());
            case FINALIZE_PAYOUT_TX_REQUEST:
                return FinalizePayoutTxRequest.fromProto(proto.getFinalizePayoutTxRequest());
            case PAYOUT_TX_PUBLISHED_MESSAGE:
                return PayoutTxPublishedMessage.fromProto(proto.getPayoutTxPublishedMessage());

            case OPEN_NEW_DISPUTE_MESSAGE:
                return OpenNewDisputeMessage.fromProto(proto.getOpenNewDisputeMessage(), this);
            case PEER_OPENED_DISPUTE_MESSAGE:
                return PeerOpenedDisputeMessage.fromProto(proto.getPeerOpenedDisputeMessage(), this);
            case DISPUTE_COMMUNICATION_MESSAGE:
                return DisputeCommunicationMessage.fromProto(proto.getDisputeCommunicationMessage());
            case DISPUTE_RESULT_MESSAGE:
                return DisputeResultMessage.fromProto(proto.getDisputeResultMessage());
            case PEER_PUBLISHED_PAYOUT_TX_MESSAGE:
                return PeerPublishedPayoutTxMessage.fromProto(proto.getPeerPublishedPayoutTxMessage());

            case PRIVATE_NOTIFICATION_MESSAGE:
                return PrivateNotificationMessage.fromProto(proto.getPrivateNotificationMessage());

            case GET_BSQ_BLOCKS_REQUEST:
                return GetBsqBlocksRequest.fromProto(proto.getGetBsqBlocksRequest());
            case GET_BSQ_BLOCKS_RESPONSE:
                return GetBsqBlocksResponse.fromProto(proto.getGetBsqBlocksResponse());
            case NEW_BSQ_BLOCK_BROADCAST_MESSAGE:
                return NewBsqBlockBroadcastMessage.fromProto(proto.getNewBsqBlockBroadcastMessage());

            default:
                throw new ProtobufferException("Unknown proto message case. messageCase=" + proto.getMessageCase());
        }
    }

    public NetworkPayload fromProto(PB.StorageEntryWrapper proto) {
        switch (proto.getMessageCase()) {
            case PROTECTED_MAILBOX_STORAGE_ENTRY:
                return ProtectedMailboxStorageEntry.fromProto(proto.getProtectedMailboxStorageEntry(), this);
            case PROTECTED_STORAGE_ENTRY:
                return ProtectedStorageEntry.fromProto(proto.getProtectedStorageEntry(), this);
            default:
                throw new ProtobufferException("Unknown proto message case. messageCase=" + proto.getMessageCase());
        }
    }

    public NetworkPayload fromProto(PB.StoragePayload proto) {
        switch (proto.getMessageCase()) {
            case ALERT:
                return Alert.fromProto(proto.getAlert());
            case ARBITRATOR:
                return Arbitrator.fromProto(proto.getArbitrator());
            case MEDIATOR:
                return Mediator.fromProto(proto.getMediator());
            case FILTER:
                return Filter.fromProto(proto.getFilter());
            case COMPENSATION_REQUEST_PAYLOAD:
                return CompensationRequestPayload.fromProto(proto.getCompensationRequestPayload());
            case TRADE_STATISTICS:
                return TradeStatistics.fromProto(proto.getTradeStatistics());
            case MAILBOX_STORAGE_PAYLOAD:
                return MailboxStoragePayload.fromProto(proto.getMailboxStoragePayload());
            case OFFER_PAYLOAD:
                return OfferPayload.fromProto(proto.getOfferPayload());
            default:
                throw new ProtobufferException("Unknown proto message case. messageCase=" + proto.getMessageCase());
        }
    }
}
