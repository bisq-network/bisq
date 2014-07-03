package io.bitsquare.trade.payment.offerer.tasks;

import io.bitsquare.trade.payment.offerer.OffererAsBuyerProtocol;
import io.nucleo.scheduler.tasks.AbstractTask;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractOffererAsBuyerTask extends AbstractTask
{
    private static final Logger log = LoggerFactory.getLogger(AbstractOffererAsBuyerTask.class);

    protected OffererAsBuyerProtocol sharedModel;

    public AbstractOffererAsBuyerTask(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        addResultHandlers(resultHandler);
        addFaultHandlers(faultHandler);
    }

    @Override
    public void setModel(Object model)
    {
        sharedModel = (OffererAsBuyerProtocol) model;
        super.setModel(model);
    }

}
