/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.proto;

import com.google.protobuf.ByteString;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.monetary.Price;
import io.bisq.core.arbitration.Dispute;
import io.bisq.core.filter.PaymentAccountFilter;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.PaymentAccountFactory;
import io.bisq.core.payment.payload.BankAccountPayload;
import io.bisq.core.payment.payload.CountryBasedPaymentAccountPayload;
import io.bisq.core.payment.payload.PaymentMethod;
import io.bisq.core.trade.Contract;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import org.bitcoinj.core.Coin;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ProtoUtil {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Get Domain objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    static Dispute getDispute(PB.Dispute dispute) {
        return new Dispute(dispute.getTradeId(), dispute.getTraderId(),
                dispute.getDisputeOpenerIsBuyer(), dispute.getDisputeOpenerIsMaker(),
                getPubKeyRing(dispute.getTraderPubKeyRing()), new Date(dispute.getTradeDate()), getContract(dispute.getContract()),
                dispute.getContractHash().toByteArray(), dispute.getDepositTxSerialized().toByteArray(),
                dispute.getPayoutTxSerialized().toByteArray(),
                dispute.getDepositTxId(), dispute.getPayoutTxId(), dispute.getContractAsJson(), dispute.getMakerContractSignature(),
                dispute.getTakerContractSignature(), getPubKeyRing(dispute.getArbitratorPubKeyRing()), dispute.getIsSupportTicket());
    }

    private static Contract getContract(PB.Contract contract) {
        return new Contract(CoreNetworkProtoResolver.getOfferPayload(contract.getOfferPayload()),
                Coin.valueOf(contract.getTradeAmount()),
                Price.valueOf(getCurrencyCode(contract.getOfferPayload()), contract.getTradePrice()),
                contract.getTakerFeeTxId(),
                NodeAddress.fromProto(contract.getBuyerNodeAddress()),
                NodeAddress.fromProto(contract.getSellerNodeAddress()),
                NodeAddress.fromProto(contract.getArbitratorNodeAddress()),
                NodeAddress.fromProto(contract.getMediatorNodeAddress()),
                contract.getIsBuyerMakerAndSellerTaker(),
                contract.getMakerAccountId(),
                contract.getTakerAccountId(),
                CoreNetworkProtoResolver.getPaymentAccountPayload(contract.getMakerPaymentAccountPayload()),
                CoreNetworkProtoResolver.getPaymentAccountPayload(contract.getTakerPaymentAccountPayload()),
                getPubKeyRing(contract.getMakerPubKeyRing()),
                getPubKeyRing(contract.getTakerPubKeyRing()),
                contract.getMakerPayoutAddressString(),
                contract.getTakerPayoutAddressString(),
                contract.getMakerBtcPubKey().toByteArray(),
                contract.getTakerBtcPubKey().toByteArray());
    }

    @NotNull
    public static PubKeyRing getPubKeyRing(PB.PubKeyRing pubKeyRing) {
        return new PubKeyRing(pubKeyRing.getSignaturePubKeyBytes().toByteArray(),
                pubKeyRing.getEncryptionPubKeyBytes().toByteArray(),
                pubKeyRing.getPgpPubKeyAsPem());
    }

    public static PaymentAccountFilter getPaymentAccountFilter(PB.PaymentAccountFilter accountFilter) {
        return new PaymentAccountFilter(accountFilter.getPaymentMethodId(), accountFilter.getGetMethodName(),
                accountFilter.getValue());
    }

    static Set<byte[]> getByteSet(List<ByteString> byteStringList) {
        return new HashSet<>(
                byteStringList
                        .stream()
                        .map(ByteString::toByteArray).collect(Collectors.toList()));
    }

    public static String getCurrencyCode(PB.OfferPayload pbOffer) {
        String currencyCode;
        if (CurrencyUtil.isCryptoCurrency(pbOffer.getBaseCurrencyCode()))
            currencyCode = pbOffer.getBaseCurrencyCode();
        else
            currencyCode = pbOffer.getCounterCurrencyCode();
        return currencyCode;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PaymentAccountPayload Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    static void fillInBankAccountPayload(PB.PaymentAccountPayload protoEntry, BankAccountPayload bankAccountPayload) {
        PB.BankAccountPayload bankProto = protoEntry.getCountryBasedPaymentAccountPayload().getBankAccountPayload();
        bankAccountPayload.setHolderName(bankProto.getHolderName());
        bankAccountPayload.setBankName(bankProto.getBankName());
        bankAccountPayload.setBankId(bankProto.getBankId());
        bankAccountPayload.setBranchId(bankProto.getBranchId());
        bankAccountPayload.setAccountNr(bankProto.getAccountNr());
        bankAccountPayload.setAccountType(bankProto.getAccountType());
    }

    static void fillInCountryBasedPaymentAccountPayload(PB.PaymentAccountPayload protoEntry,
                                                        CountryBasedPaymentAccountPayload countryBasedPaymentAccountPayload) {
        countryBasedPaymentAccountPayload.setCountryCode(protoEntry.getCountryBasedPaymentAccountPayload().getCountryCode());
    }

    public static PaymentAccount getPaymentAccount(PB.PaymentAccount account) {
        return PaymentAccountFactory.getPaymentAccount(PaymentMethod.getPaymentMethodById(account.getPaymentMethod().getId()));
    }
}
