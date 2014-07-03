package io.bitsquare.trade.payment.taker.tasks;

import io.bitsquare.trade.payment.taker.TakerAsSellerProtocol;
import io.nucleo.scheduler.tasks.AbstractTask;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTakerAsSellerTask extends AbstractTask
{
    private static final Logger log = LoggerFactory.getLogger(AbstractTakerAsSellerTask.class);

    protected TakerAsSellerProtocol sharedModel;

    public AbstractTakerAsSellerTask(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        addResultHandlers(resultHandler);
        addFaultHandlers(faultHandler);
    }

    @Override
    public void setModel(Object model)
    {
        sharedModel = (TakerAsSellerProtocol) model;
        super.setModel(model);
    }

}
