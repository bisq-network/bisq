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

package io.bisq.core.payment.payload;

import io.bisq.common.locale.CountryUtil;
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.core.proto.ProtoCoreUtil;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Getter
@EqualsAndHashCode
@ToString
@Slf4j
public abstract class PaymentAccountPayload implements NetworkPayload {

    protected final String paymentMethodId;
    protected final String id;
    protected final long maxTradePeriod;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    PaymentAccountPayload(String paymentMethodId, String id, long maxTradePeriod) {
        this.paymentMethodId = paymentMethodId;
        this.id = id;
        this.maxTradePeriod = maxTradePeriod;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract public String getPaymentDetails();

    abstract public String getPaymentDetailsForTradePopup();

    public static PaymentAccountPayload fromProto(PB.PaymentAccountPayload proto) {
        PaymentAccountPayload result = null;
        switch (proto.getMessageCase()) {
            case ALI_PAY_ACCOUNT_PAYLOAD:
                result = new AliPayAccountPayload(proto.getPaymentMethodId(), proto.getId(),
                        proto.getMaxTradePeriod(), proto.getAliPayAccountPayload().getAccountNr());
                break;
            case CHASE_QUICK_PAY_ACCOUNT_PAYLOAD:
                result = new ChaseQuickPayAccountPayload(proto.getPaymentMethodId(), proto.getId(),
                        proto.getMaxTradePeriod(), proto.getChaseQuickPayAccountPayload().getEmail(),
                        proto.getChaseQuickPayAccountPayload().getHolderName());
                break;
            case CLEAR_XCHANGE_ACCOUNT_PAYLOAD:
                result = new ClearXchangeAccountPayload(proto.getPaymentMethodId(), proto.getId(),
                        proto.getMaxTradePeriod(), proto.getClearXchangeAccountPayload().getHolderName(),
                        proto.getClearXchangeAccountPayloadOrBuilder().getEmailOrMobileNr());
                break;
            case COUNTRY_BASED_PAYMENT_ACCOUNT_PAYLOAD:
                switch (proto.getCountryBasedPaymentAccountPayload().getMessageCase()) {
                    case BANK_ACCOUNT_PAYLOAD:
                        switch (proto.getCountryBasedPaymentAccountPayload().getBankAccountPayload().getMessageCase()) {
                            case NATIONAL_BANK_ACCOUNT_PAYLOAD:
                                NationalBankAccountPayload nationalBankAccountPayload = new NationalBankAccountPayload(proto.getPaymentMethodId(), proto.getId(),
                                        proto.getMaxTradePeriod());
                                ProtoCoreUtil.fillInBankAccountPayload(proto, nationalBankAccountPayload);
                                ProtoCoreUtil.fillInCountryBasedPaymentAccountPayload(proto, nationalBankAccountPayload);
                                result = nationalBankAccountPayload;
                                break;
                            case SAME_BANK_ACCONT_PAYLOAD:
                                SameBankAccountPayload sameBankAccountPayload = new SameBankAccountPayload(proto.getPaymentMethodId(), proto.getId(),
                                        proto.getMaxTradePeriod());
                                ProtoCoreUtil.fillInBankAccountPayload(proto, sameBankAccountPayload);
                                ProtoCoreUtil.fillInCountryBasedPaymentAccountPayload(proto, sameBankAccountPayload);
                                result = sameBankAccountPayload;
                                break;
                            case SPECIFIC_BANKS_ACCOUNT_PAYLOAD:
                                SpecificBanksAccountPayload specificBanksAccountPayload = new SpecificBanksAccountPayload(proto.getPaymentMethodId(), proto.getId(),
                                        proto.getMaxTradePeriod());
                                ProtoCoreUtil.fillInBankAccountPayload(proto, specificBanksAccountPayload);
                                ProtoCoreUtil.fillInCountryBasedPaymentAccountPayload(proto, specificBanksAccountPayload);
                                result = specificBanksAccountPayload;
                                break;
                        }
                        break;
                    case CASH_DEPOSIT_ACCOUNT_PAYLOAD:
                        CashDepositAccountPayload cashDepositAccountPayload = new CashDepositAccountPayload(proto.getPaymentMethodId(), proto.getId(),
                                proto.getMaxTradePeriod());
                        ProtoCoreUtil.fillInCountryBasedPaymentAccountPayload(proto, cashDepositAccountPayload);
                        result = cashDepositAccountPayload;
                        break;
                    case SEPA_ACCOUNT_PAYLOAD:
                        SepaAccountPayload sepaAccountPayload = new SepaAccountPayload(proto.getPaymentMethodId(), proto.getId(),
                                proto.getMaxTradePeriod(), CountryUtil.getAllSepaCountries());
                        ProtoCoreUtil.fillInCountryBasedPaymentAccountPayload(proto, sepaAccountPayload);
                        result = sepaAccountPayload;
                        break;
                }
                break;
            case CRYPTO_CURRENCY_ACCOUNT_PAYLOAD:
                result = new CryptoCurrencyAccountPayload(proto.getPaymentMethodId(), proto.getId(),
                        proto.getMaxTradePeriod(), proto.getCryptoCurrencyAccountPayload().getAddress());
                break;
            case FASTER_PAYMENTS_ACCOUNT_PAYLOAD:
                result = new FasterPaymentsAccountPayload(proto.getPaymentMethodId(), proto.getId(),
                        proto.getMaxTradePeriod(), proto.getFasterPaymentsAccountPayload().getSortCode(),
                        proto.getFasterPaymentsAccountPayload().getAccountNr());
                break;
            case INTERAC_E_TRANSFER_ACCOUNT_PAYLOAD:
                PB.InteracETransferAccountPayload interacETransferAccountPayload =
                        proto.getInteracETransferAccountPayload();
                result = new InteracETransferAccountPayload(proto.getPaymentMethodId(), proto.getId(),
                        proto.getMaxTradePeriod(), interacETransferAccountPayload.getEmail(),
                        interacETransferAccountPayload.getHolderName(),
                        interacETransferAccountPayload.getQuestion(),
                        interacETransferAccountPayload.getAnswer());
                break;
            case O_K_PAY_ACCOUNT_PAYLOAD:
                result = getOkPayAccountPayload(proto);
                break;
            case PERFECT_MONEY_ACCOUNT_PAYLOAD:
                result = new PerfectMoneyAccountPayload(proto.getPaymentMethodId(), proto.getId(),
                        proto.getMaxTradePeriod(), proto.getPerfectMoneyAccountPayload().getAccountNr());
                break;
            case SWISH_ACCOUNT_PAYLOAD:
                result = new SwishAccountPayload(proto.getPaymentMethodId(), proto.getId(),
                        proto.getMaxTradePeriod(), proto.getSwishAccountPayload().getMobileNr(),
                        proto.getSwishAccountPayload().getHolderName());
                break;
            case U_S_POSTAL_MONEY_ORDER_ACCOUNT_PAYLOAD:
                result = new USPostalMoneyOrderAccountPayload(proto.getPaymentMethodId(), proto.getId(),
                        proto.getMaxTradePeriod(), proto.getUSPostalMoneyOrderAccountPayload().getPostalAddress(),
                        proto.getUSPostalMoneyOrderAccountPayload().getHolderName());
                break;
            default:
                log.error("Unknown paymentaccountcontractdata:{}", proto.getMessageCase());
        }
        return result;
    }

    @NotNull
    static OKPayAccountPayload getOkPayAccountPayload(PB.PaymentAccountPayload protoEntry) {
        OKPayAccountPayload okPayAccountPayload = new OKPayAccountPayload(protoEntry.getPaymentMethodId(), protoEntry.getId(),
                protoEntry.getMaxTradePeriod(), protoEntry.getOKPayAccountPayload().getAccountNr());
        okPayAccountPayload.setAccountNr(protoEntry.getOKPayAccountPayload().getAccountNr());
        return okPayAccountPayload;
    }

}
