package bisq.core.offer.placeoffer.bisq_v1.tasks;

import bisq.core.locale.Res;
import bisq.core.offer.placeoffer.bisq_v1.PlaceOfferModel;

import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

public class CheckNumberOfUnconfirmedTransactions extends Task<PlaceOfferModel> {
    public CheckNumberOfUnconfirmedTransactions(TaskRunner<PlaceOfferModel> taskHandler, PlaceOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        if (model.getWalletService().isUnconfirmedTransactionsLimitHit() || model.getBsqWalletService().isUnconfirmedTransactionsLimitHit())
            failed(Res.get("shared.unconfirmedTransactionsLimitReached"));
        complete();
    }
}
