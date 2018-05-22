package network.bisq.api.model.payment;

import bisq.core.payment.InteracETransferAccount;
import bisq.core.payment.payload.InteracETransferAccountPayload;

public class InteracETransferPaymentAccountConverter extends AbstractPaymentAccountConverter<InteracETransferAccount, InteracETransferAccountPayload, InteracETransferPaymentAccount> {

    @Override
    public InteracETransferAccount toBusinessModel(InteracETransferPaymentAccount rest) {
        final InteracETransferAccount business = new InteracETransferAccount();
        business.init();
        business.setHolderName(rest.holderName);
        business.setEmail(rest.emailOrMobileNr);
        business.setQuestion(rest.question);
        business.setAnswer(rest.answer);
        toBusinessModel(business, rest);
        return business;
    }

    @Override
    public InteracETransferPaymentAccount toRestModel(InteracETransferAccount business) {
        final InteracETransferPaymentAccount rest = toRestModel((InteracETransferAccountPayload) business.getPaymentAccountPayload());
        toRestModel(rest, business);
        return rest;
    }

    @Override
    public InteracETransferPaymentAccount toRestModel(InteracETransferAccountPayload business) {
        final InteracETransferPaymentAccount rest = new InteracETransferPaymentAccount();
        rest.answer = business.getAnswer();
        rest.question = business.getQuestion();
        rest.holderName = business.getHolderName();
        rest.emailOrMobileNr = business.getEmail();
        toRestModel(rest, business);
        return rest;
    }

}
