package io.bisq.core.proto;

import io.bisq.common.proto.ProtoResolver;
import io.bisq.common.proto.ProtobufferException;
import io.bisq.core.payment.payload.*;
import io.bisq.generated.protobuffer.PB;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoreProtoResolver implements ProtoResolver {
    @Override
    public PaymentAccountPayload fromProto(PB.PaymentAccountPayload proto) {
        if (proto != null) {
            switch (proto.getMessageCase()) {
                case ALI_PAY_ACCOUNT_PAYLOAD:
                    return AliPayAccountPayload.fromProto(proto);
                case CHASE_QUICK_PAY_ACCOUNT_PAYLOAD:
                    return ChaseQuickPayAccountPayload.fromProto(proto);
                case CLEAR_XCHANGE_ACCOUNT_PAYLOAD:
                    return ClearXchangeAccountPayload.fromProto(proto);
                case COUNTRY_BASED_PAYMENT_ACCOUNT_PAYLOAD:
                    switch (proto.getCountryBasedPaymentAccountPayload().getMessageCase()) {
                        case BANK_ACCOUNT_PAYLOAD:
                            switch (proto.getCountryBasedPaymentAccountPayload().getBankAccountPayload().getMessageCase()) {
                                case NATIONAL_BANK_ACCOUNT_PAYLOAD:
                                    return NationalBankAccountPayload.fromProto(proto);
                                case SAME_BANK_ACCONT_PAYLOAD:
                                    return SameBankAccountPayload.fromProto(proto);
                                case SPECIFIC_BANKS_ACCOUNT_PAYLOAD:
                                    return SpecificBanksAccountPayload.fromProto(proto);
                                default:
                                    throw new ProtobufferException("Unknown proto message case" +
                                            "(PB.PaymentAccountPayload.CountryBasedPaymentAccountPayload.BankAccountPayload). " +
                                            "messageCase=" + proto.getMessageCase());
                            }
                        case CASH_DEPOSIT_ACCOUNT_PAYLOAD:
                            return CashDepositAccountPayload.fromProto(proto);
                        case SEPA_ACCOUNT_PAYLOAD:
                            return SepaAccountPayload.fromProto(proto);
                        default:
                            throw new ProtobufferException("Unknown proto message case" +
                                    "(PB.PaymentAccountPayload.CountryBasedPaymentAccountPayload)." +
                                    " messageCase=" + proto.getMessageCase());
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
                    throw new ProtobufferException("Unknown proto message case(PB.PaymentAccountPayload). messageCase=" + proto.getMessageCase());
            }
        } else {
            log.error("PersistableEnvelope.fromProto: PB.PaymentAccountPayload is null");
            throw new ProtobufferException("PB.PaymentAccountPayload is null");
        }
    }
}