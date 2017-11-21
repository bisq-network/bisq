package io.bisq.core.proto;

import io.bisq.common.proto.ProtoResolver;
import io.bisq.common.proto.ProtobufferException;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.core.payment.AccountAgeWitness;
import io.bisq.core.payment.payload.*;
import io.bisq.core.trade.statistics.TradeStatistics2;
import io.bisq.generated.protobuffer.PB;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoreProtoResolver implements ProtoResolver {
    @Override
    public PaymentAccountPayload fromProto(PB.PaymentAccountPayload proto) {
        if (proto != null) {
            final PB.PaymentAccountPayload.MessageCase messageCase = proto.getMessageCase();
            switch (messageCase) {
                case ALI_PAY_ACCOUNT_PAYLOAD:
                    return AliPayAccountPayload.fromProto(proto);
                case CHASE_QUICK_PAY_ACCOUNT_PAYLOAD:
                    return ChaseQuickPayAccountPayload.fromProto(proto);
                case CLEAR_XCHANGE_ACCOUNT_PAYLOAD:
                    return ClearXchangeAccountPayload.fromProto(proto);
                case COUNTRY_BASED_PAYMENT_ACCOUNT_PAYLOAD:
                    final PB.CountryBasedPaymentAccountPayload.MessageCase messageCaseCountry = proto.getCountryBasedPaymentAccountPayload().getMessageCase();
                    switch (messageCaseCountry) {
                        case BANK_ACCOUNT_PAYLOAD:
                            final PB.BankAccountPayload.MessageCase messageCaseBank = proto.getCountryBasedPaymentAccountPayload().getBankAccountPayload().getMessageCase();
                            switch (messageCaseBank) {
                                case NATIONAL_BANK_ACCOUNT_PAYLOAD:
                                    return NationalBankAccountPayload.fromProto(proto);
                                case SAME_BANK_ACCONT_PAYLOAD:
                                    return SameBankAccountPayload.fromProto(proto);
                                case SPECIFIC_BANKS_ACCOUNT_PAYLOAD:
                                    return SpecificBanksAccountPayload.fromProto(proto);
                                default:
                                    throw new ProtobufferException("Unknown proto message case" +
                                            "(PB.PaymentAccountPayload.CountryBasedPaymentAccountPayload.BankAccountPayload). " +
                                            "messageCase=" + messageCaseBank);
                            }
                        case WESTERN_UNION_ACCOUNT_PAYLOAD:
                            return WesternUnionAccountPayload.fromProto(proto);
                        case CASH_DEPOSIT_ACCOUNT_PAYLOAD:
                            return CashDepositAccountPayload.fromProto(proto);
                        case SEPA_ACCOUNT_PAYLOAD:
                            return SepaAccountPayload.fromProto(proto);
                        default:
                            throw new ProtobufferException("Unknown proto message case" +
                                    "(PB.PaymentAccountPayload.CountryBasedPaymentAccountPayload)." +
                                    " messageCase=" + messageCaseCountry);
                    }
                case CRYPTO_CURRENCY_ACCOUNT_PAYLOAD:
                    return CryptoCurrencyAccountPayload.fromProto(proto);
                case FASTER_PAYMENTS_ACCOUNT_PAYLOAD:
                    return FasterPaymentsAccountPayload.fromProto(proto);
                case INTERAC_E_TRANSFER_ACCOUNT_PAYLOAD:
                    return InteracETransferAccountPayload.fromProto(proto);
                case O_K_PAY_ACCOUNT_PAYLOAD:
                    return OKPayAccountPayload.fromProto(proto);
                case PERFECT_MONEY_ACCOUNT_PAYLOAD:
                    return PerfectMoneyAccountPayload.fromProto(proto);
                case SWISH_ACCOUNT_PAYLOAD:
                    return SwishAccountPayload.fromProto(proto);
                case U_S_POSTAL_MONEY_ORDER_ACCOUNT_PAYLOAD:
                    return USPostalMoneyOrderAccountPayload.fromProto(proto);
                default:
                    throw new ProtobufferException("Unknown proto message case(PB.PaymentAccountPayload). messageCase=" + messageCase);
            }
        } else {
            log.error("PersistableEnvelope.fromProto: PB.PaymentAccountPayload is null");
            throw new ProtobufferException("PB.PaymentAccountPayload is null");
        }
    }

    @Override
    public PersistableEnvelope fromProto(PB.PersistableNetworkPayload proto) {
        if (proto != null) {
            switch (proto.getMessageCase()) {
                case ACCOUNT_AGE_WITNESS:
                    return AccountAgeWitness.fromProto(proto.getAccountAgeWitness());
                case TRADE_STATISTICS2:
                    return TradeStatistics2.fromProto(proto.getTradeStatistics2());
                default:
                    throw new ProtobufferException("Unknown proto message case (PB.PersistableNetworkPayload). messageCase=" + proto.getMessageCase());
            }
        } else {
            log.error("PB.PersistableNetworkPayload is null");
            throw new ProtobufferException("PB.PersistableNetworkPayload is null");
        }
    }
}
