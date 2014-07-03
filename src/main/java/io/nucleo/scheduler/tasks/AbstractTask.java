package io.nucleo.scheduler.tasks;

import io.nucleo.scheduler.worker.AbstractWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The base class for all tasks.
 */
public abstract class AbstractTask extends AbstractWorker
{
    private static final Logger log = LoggerFactory.getLogger(AbstractTask.class);
}
