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

package io.bisq.core.user;

import com.google.protobuf.Message;
import io.bisq.common.persistence.Persistable;
import io.bisq.common.proto.ProtoHelper;
import io.bisq.core.alert.Alert;
import io.bisq.core.arbitration.Arbitrator;
import io.bisq.core.arbitration.Mediator;
import io.bisq.core.filter.Filter;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.proto.ProtoUtil;
import io.bisq.generated.protobuffer.PB;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.print.attribute.standard.Media;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Data
@AllArgsConstructor
public class UserVO implements Persistable {
    // Persisted fields
    private String accountID;
    private Set<PaymentAccount> paymentAccounts = new HashSet<>();
    private PaymentAccount currentPaymentAccount;
    private List<String> acceptedLanguageLocaleCodes = new ArrayList<>();
    @Nullable
    private Alert developersAlert;
    @Nullable
    private Alert displayedAlert;
    @Nullable
    private Filter developersFilter;
    @Nullable
    private Arbitrator registeredArbitrator;
    @Nullable
    private Mediator registeredMediator;

    private List<Arbitrator> acceptedArbitrators = new ArrayList<>();
    private List<Mediator> acceptedMediators = new ArrayList<>();

    public UserVO() {

    }

    @Override
    public Message toProto() {
        PB.User.Builder builder = PB.User.newBuilder()
                .setAccountId(accountID)
                .addAllPaymentAccounts(ProtoHelper.collectionToProto(paymentAccounts))
                .setCurrentPaymentAccount(currentPaymentAccount.toProto())
                .addAllAcceptedLanguageLocaleCodes(acceptedLanguageLocaleCodes)
                .addAllAcceptedArbitrators(ProtoHelper.collectionToProto(acceptedArbitrators))
                .addAllAcceptedMediators(ProtoHelper.collectionToProto(acceptedMediators));
        Optional.ofNullable(developersAlert)
                .ifPresent(developersAlert -> builder.setDevelopersAlert(developersAlert.toProto().getAlert()));
        Optional.ofNullable(displayedAlert)
                .ifPresent(displayedAlert -> builder.setDisplayedAlert(displayedAlert.toProto().getAlert()));
        Optional.ofNullable(developersFilter)
                .ifPresent(developersFilter -> builder.setDevelopersFilter(developersFilter.toProto().getFilter()));
        Optional.ofNullable(registeredArbitrator)
                .ifPresent(registeredArbitrator -> builder.setRegisteredArbitrator(registeredArbitrator.toProto().getArbitrator()));
        Optional.ofNullable(registeredMediator)
                .ifPresent(developersAlert -> builder.setDevelopersAlert(developersAlert.toProto().getAlert()));
        return PB.DiskEnvelope.newBuilder().setUser(builder).build();
    }

    public static UserVO fromProto(PB.User user) {
        Set<PaymentAccount> collect = user.getPaymentAccountsList().stream().map(paymentAccount -> ProtoUtil.getPaymentAccount(paymentAccount)).collect(Collectors.toSet());
        UserVO vo = new UserVO(user.getAccountId(),
                collect,
                ProtoUtil.getPaymentAccount(user.getCurrentPaymentAccount()),
                user.getAcceptedLanguageLocaleCodesList(),
                Alert.fromProto(user.getDevelopersAlert()),
                Alert.fromProto(user.getDisplayedAlert()),
                Filter.fromProto(user.getDevelopersFilter()),
                Arbitrator.fromProto(user.getRegisteredArbitrator()),
                Mediator.fromProto(user.getRegisteredMediator()),
                user.getAcceptedArbitratorsList().stream().map(Arbitrator::fromProto).collect(Collectors.toList()),
                user.getAcceptedMediatorsList().stream().map(Mediator::fromProto).collect(Collectors.toList())
                );
        return vo;
    }

}
