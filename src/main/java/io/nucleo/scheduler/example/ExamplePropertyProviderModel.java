package io.nucleo.scheduler.example;

import io.nucleo.scheduler.model.PropertyProviderModel;

public class ExamplePropertyProviderModel extends PropertyProviderModel
{
    public final Object flashVars;
    public Object user;

    public ExamplePropertyProviderModel(Object flashVars)
    {
        this.flashVars = flashVars;
    }

}
