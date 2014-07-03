package io.nucleo.scheduler.tasks;

import java.util.Map;

public class SyncWorker2 extends AbstractTask
{
    public static String ERR_MSG = "Failure message";
    public static String STATE = "ok";
    private boolean letItFail;

    public SyncWorker2(boolean letItFail)
    {
        this.letItFail = letItFail;
    }

    @Override
    public void execute()
    {
        System.out.println("execute " + this);
        if (model != null) ((Map<String, String>) model).put("worker2State", STATE);
        if (letItFail)
            failed(new Exception(ERR_MSG));
        else
            complete();
    }

}
