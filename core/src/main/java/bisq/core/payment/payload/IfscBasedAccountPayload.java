package bisq.core.payment.payload;


import bisq.core.locale.BankUtil;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.Res;

import java.nio.charset.StandardCharsets;

import java.util.Map;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@Setter
@Getter
@ToString
@Slf4j
public abstract class IfscBasedAccountPayload extends CountryBasedPaymentAccountPayload implements PayloadWithHolderName {
    protected String holderName = "";
    protected String ifsc = "";
    protected String accountNr = "";

    protected IfscBasedAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected IfscBasedAccountPayload(String paymentMethodName,
                                 String id,
                                 String countryCode,
                                 String holderName,
                                 String accountNr,
                                 String ifsc,
                                 long maxTradePeriod,
                                 Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethodName,
                id,
                countryCode,
                maxTradePeriod,
                excludeFromJsonDataMap);

        this.holderName = holderName;
        this.accountNr = accountNr;
        this.ifsc = ifsc;
    }

    @Override
    public protobuf.PaymentAccountPayload.Builder getPaymentAccountPayloadBuilder() {
        protobuf.IfscBasedAccountPayload.Builder builder =
                protobuf.IfscBasedAccountPayload.newBuilder()
                        .setHolderName(holderName);
        Optional.ofNullable(ifsc).ifPresent(builder::setIfsc);
        Optional.ofNullable(accountNr).ifPresent(builder::setAccountNr);
        final protobuf.CountryBasedPaymentAccountPayload.Builder countryBasedPaymentAccountPayloadBuilder =
                super.getPaymentAccountPayloadBuilder()
                .getCountryBasedPaymentAccountPayloadBuilder()
                .setIfscBasedAccountPayload(builder);
        return super.getPaymentAccountPayloadBuilder()
                .setCountryBasedPaymentAccountPayload(countryBasedPaymentAccountPayloadBuilder);
    }

    @Override
    public String getPaymentDetails() {
        return "Ifsc account transfer - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return Res.getWithCol("payment.account.owner") + " " + holderName + "\n" +
                BankUtil.getAccountNrLabel(countryCode) + ": " + accountNr + "\n" +
                BankUtil.getBankIdLabel(countryCode) + ": " + ifsc + "\n" +
                Res.getWithCol("payment.bank.country") + " " + CountryUtil.getNameByCode(countryCode);
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        // We don't add holderName because we don't want to break age validation if the user recreates an account with
        // slight changes in holder name (e.g. add or remove middle name)
        String all = accountNr + ifsc;
        return super.getAgeWitnessInputData(all.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getOwnerId() {
        return holderName;
    }
}
