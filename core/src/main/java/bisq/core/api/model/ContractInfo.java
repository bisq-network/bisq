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

package bisq.core.api.model;

import bisq.common.Payload;

import java.util.function.Supplier;

import lombok.Getter;

import static bisq.core.api.model.PaymentAccountPayloadInfo.emptyPaymentAccountPayload;

/**
 * A lightweight Trade Contract constructed from a trade's json contract.
 * Many fields in the core Contract are ignored, but can be added as needed.
 */
@Getter
public class ContractInfo implements Payload {

    private final String buyerNodeAddress;
    private final String sellerNodeAddress;
    private final String mediatorNodeAddress;
    private final String refundAgentNodeAddress;
    private final boolean isBuyerMakerAndSellerTaker;
    private final String makerAccountId;
    private final String takerAccountId;
    private final PaymentAccountPayloadInfo makerPaymentAccountPayload;
    private final PaymentAccountPayloadInfo takerPaymentAccountPayload;
    private final String makerPayoutAddressString;
    private final String takerPayoutAddressString;
    private final long lockTime;

    public ContractInfo(String buyerNodeAddress,
                        String sellerNodeAddress,
                        String mediatorNodeAddress,
                        String refundAgentNodeAddress,
                        boolean isBuyerMakerAndSellerTaker,
                        String makerAccountId,
                        String takerAccountId,
                        PaymentAccountPayloadInfo makerPaymentAccountPayload,
                        PaymentAccountPayloadInfo takerPaymentAccountPayload,
                        String makerPayoutAddressString,
                        String takerPayoutAddressString,
                        long lockTime) {
        this.buyerNodeAddress = buyerNodeAddress;
        this.sellerNodeAddress = sellerNodeAddress;
        this.mediatorNodeAddress = mediatorNodeAddress;
        this.refundAgentNodeAddress = refundAgentNodeAddress;
        this.isBuyerMakerAndSellerTaker = isBuyerMakerAndSellerTaker;
        this.makerAccountId = makerAccountId;
        this.takerAccountId = takerAccountId;
        this.makerPaymentAccountPayload = makerPaymentAccountPayload;
        this.takerPaymentAccountPayload = takerPaymentAccountPayload;
        this.makerPayoutAddressString = makerPayoutAddressString;
        this.takerPayoutAddressString = takerPayoutAddressString;
        this.lockTime = lockTime;
    }


    // For transmitting TradeInfo messages when no contract is available.
    // TODO Is this necessary as protobuf will send a DEFAULT_INSTANCE.
    public static Supplier<ContractInfo> emptyContract = () ->
            new ContractInfo("",
                    "",
                    "",
                    "",
                    false,
                    "",
                    "",
                    emptyPaymentAccountPayload.get(),
                    emptyPaymentAccountPayload.get(),
                    "",
                    "",
                    0);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static ContractInfo fromProto(bisq.proto.grpc.ContractInfo proto) {
        return new ContractInfo(proto.getBuyerNodeAddress(),
                proto.getSellerNodeAddress(),
                proto.getMediatorNodeAddress(),
                proto.getRefundAgentNodeAddress(),
                proto.getIsBuyerMakerAndSellerTaker(),
                proto.getMakerAccountId(),
                proto.getTakerAccountId(),
                PaymentAccountPayloadInfo.fromProto(proto.getMakerPaymentAccountPayload()),
                PaymentAccountPayloadInfo.fromProto(proto.getTakerPaymentAccountPayload()),
                proto.getMakerPayoutAddressString(),
                proto.getTakerPayoutAddressString(),
                proto.getLockTime());
    }

    @Override
    public bisq.proto.grpc.ContractInfo toProtoMessage() {
        return bisq.proto.grpc.ContractInfo.newBuilder()
                .setBuyerNodeAddress(buyerNodeAddress)
                .setSellerNodeAddress(sellerNodeAddress)
                .setMediatorNodeAddress(mediatorNodeAddress)
                .setRefundAgentNodeAddress(refundAgentNodeAddress)
                .setIsBuyerMakerAndSellerTaker(isBuyerMakerAndSellerTaker)
                .setMakerAccountId(makerAccountId)
                .setTakerAccountId(takerAccountId)
                .setMakerPaymentAccountPayload(makerPaymentAccountPayload.toProtoMessage())
                .setTakerPaymentAccountPayload(takerPaymentAccountPayload.toProtoMessage())
                .setMakerPayoutAddressString(makerPayoutAddressString)
                .setTakerPayoutAddressString(takerPayoutAddressString)
                .setLockTime(lockTime)
                .build();
    }
}
