package bisq.core.trade.protocol.bisq_v1.tasks;

import bisq.core.filter.FilterManager;
import bisq.core.trade.bisq_v1.TradeUtil;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.common.taskrunner.TaskRunner;

public class EnforceFilterVersion extends TradeTask {
    public EnforceFilterVersion(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            FilterManager filterManager = processModel.getFilterManager();
            TradeUtil.enforceFilterVersion(filterManager, this::complete, this::failed);
        } catch (Throwable t) {
            failed(t);
        }
    }
}
