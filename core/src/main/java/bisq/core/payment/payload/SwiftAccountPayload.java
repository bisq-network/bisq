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

package bisq.core.payment.payload;

import bisq.core.locale.Res;

import com.google.protobuf.Message;

import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@ToString
@Setter
@Getter
@Slf4j
public final class SwiftAccountPayload extends PaymentAccountPayload {
    // payload data elements
    private String bankSwiftCode = "";
    private String bankCountryCode = "";
    private String bankName = "";
    private String bankBranch = "";
    private String bankAddress = "";
    private String beneficiaryName = "";
    private String beneficiaryAccountNr = "";
    private String beneficiaryAddress = "";
    private String beneficiaryCity = "";
    private String beneficiaryPhone = "";
    private String specialInstructions = "";
    private String intermediarySwiftCode = "";
    private String intermediaryCountryCode = "";
    private String intermediaryName = "";
    private String intermediaryBranch = "";
    private String intermediaryAddress = "";

    // constants
    public static final String BANKPOSTFIX = ".bank";
    public static final String INTERMEDIARYPOSTFIX = ".intermediary";
    public static final String BENEFICIARYPOSTFIX = ".beneficiary";
    public static final String SWIFT_CODE = "payment.swift.swiftCode";
    public static final String COUNTRY = "payment.swift.country";
    public static final String SWIFT_ACCOUNT = "payment.swift.account";
    public static final String SNAME = "payment.swift.name";
    public static final String BRANCH = "payment.swift.branch";
    public static final String ADDRESS = "payment.swift.address";
    public static final String PHONE = "payment.swift.phone";

    public SwiftAccountPayload(String paymentMethod, String id) {
        super(paymentMethod, id);
    }

    private SwiftAccountPayload(String paymentMethod,
                                String id,
                                String bankSwiftCode,
                                String bankCountryCode,
                                String bankName,
                                String bankBranch,
                                String bankAddress,
                                String beneficiaryName,
                                String beneficiaryAccountNr,
                                String beneficiaryAddress,
                                String beneficiaryCity,
                                String beneficiaryPhone,
                                String specialInstructions,
                                String intermediarySwiftCode,
                                String intermediaryCountryCode,
                                String intermediaryName,
                                String intermediaryBranch,
                                String intermediaryAddress,
                                long maxTradePeriod,
                                Map<String, String> excludeFromJsonDataMap) {
        super(paymentMethod,
                id,
                maxTradePeriod,
                excludeFromJsonDataMap);

        this.bankSwiftCode = bankSwiftCode;
        this.bankCountryCode = bankCountryCode;
        this.bankName = bankName;
        this.bankBranch = bankBranch;
        this.bankAddress = bankAddress;
        this.beneficiaryName = beneficiaryName;
        this.beneficiaryAccountNr = beneficiaryAccountNr;
        this.beneficiaryAddress = beneficiaryAddress;
        this.beneficiaryCity = beneficiaryCity;
        this.beneficiaryPhone = beneficiaryPhone;
        this.specialInstructions = specialInstructions;
        this.intermediarySwiftCode = intermediarySwiftCode;
        this.intermediaryCountryCode = intermediaryCountryCode;
        this.intermediaryName = intermediaryName;
        this.intermediaryBranch = intermediaryBranch;
        this.intermediaryAddress = intermediaryAddress;
    }

    @Override
    public Message toProtoMessage() {
        return getPaymentAccountPayloadBuilder()
                .setSwiftAccountPayload(protobuf.SwiftAccountPayload.newBuilder()
                        .setBankSwiftCode(bankSwiftCode)
                        .setBankCountryCode(bankCountryCode)
                        .setBankName(bankName)
                        .setBankBranch(bankBranch)
                        .setBankAddress(bankAddress)
                        .setBeneficiaryName(beneficiaryName)
                        .setBeneficiaryAccountNr(beneficiaryAccountNr)
                        .setBeneficiaryAddress(beneficiaryAddress)
                        .setBeneficiaryCity(beneficiaryCity)
                        .setBeneficiaryPhone(beneficiaryPhone)
                        .setSpecialInstructions(specialInstructions)
                        .setIntermediarySwiftCode(intermediarySwiftCode)
                        .setIntermediaryCountryCode(intermediaryCountryCode)
                        .setIntermediaryName(intermediaryName)
                        .setIntermediaryBranch(intermediaryBranch)
                        .setIntermediaryAddress(intermediaryAddress)
                )
                .build();
    }

    public static SwiftAccountPayload fromProto(protobuf.PaymentAccountPayload proto) {
        protobuf.SwiftAccountPayload x = proto.getSwiftAccountPayload();
        return new SwiftAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                x.getBankSwiftCode(),
                x.getBankCountryCode(),
                x.getBankName(),
                x.getBankBranch(),
                x.getBankAddress(),
                x.getBeneficiaryName(),
                x.getBeneficiaryAccountNr(),
                x.getBeneficiaryAddress(),
                x.getBeneficiaryCity(),
                x.getBeneficiaryPhone(),
                x.getSpecialInstructions(),
                x.getIntermediarySwiftCode(),
                x.getIntermediaryCountryCode(),
                x.getIntermediaryName(),
                x.getIntermediaryBranch(),
                x.getIntermediaryAddress(),
                proto.getMaxTradePeriod(),
                new HashMap<>(proto.getExcludeFromJsonDataMap()));
    }

    @Override
    public String getPaymentDetails() {
        return Res.get(paymentMethodId) + " - " + beneficiaryName;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return getPaymentDetails();
    }

    @Override
    public byte[] getAgeWitnessInputData() {
        return super.getAgeWitnessInputData(beneficiaryAccountNr.getBytes(StandardCharsets.UTF_8));
    }

    public boolean usesIntermediaryBank() {
        return (intermediarySwiftCode != null && intermediarySwiftCode.length() > 0);
    }
}
