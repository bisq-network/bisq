package bisq.core.trade.protocol.bsq_swap.tasks;

import bisq.core.filter.FilterManager;
import bisq.core.trade.bisq_v1.TradeUtil;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

import bisq.common.taskrunner.TaskRunner;

public class EnforceFilterVersion extends BsqSwapTask {
    public EnforceFilterVersion(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            FilterManager filterManager = protocolModel.getFilterManager();
            TradeUtil.enforceFilterVersion(filterManager, this::complete, this::failed);
        } catch (Throwable t) {
            failed(t);
        }
    }
}
