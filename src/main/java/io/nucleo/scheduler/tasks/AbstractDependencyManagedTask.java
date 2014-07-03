package io.nucleo.scheduler.tasks;

import io.nucleo.scheduler.model.PropertyProviderModel;
import java.util.ArrayList;
import java.util.List;

/**
 * The base class for all tasks using a IPropertyProviderModel instance as a shared model.
 */
public abstract class AbstractDependencyManagedTask extends AbstractTask
{
    private PropertyProviderModel propertyProviderModel;
    private List<String> readDependencyKeys = new ArrayList<>();

    public AbstractDependencyManagedTask()
    {

    }


    // -------------------------------------------------------------------
    // IRunnable implementation
    // -------------------------------------------------------------------

    @Override
    public void setModel(Object model)
    {
        propertyProviderModel = (PropertyProviderModel) model;

        super.setModel(model);

        initReadDependencies();
    }


    // -------------------------------------------------------------------
    // Abstract Methods
    // -------------------------------------------------------------------

    /**
     * To be overwritten in subclasses
     * Used to read the needed data objects for the task.
     * Typically stored as instance variable in the task.
     */
    protected void initReadDependencies()
    {
        // user = read(IUser);
        // channel = read("channel");
    }


    // -------------------------------------------------------------------
    // Final protected
    // -------------------------------------------------------------------

    final protected Object read(String key)
    {
        readDependencyKeys.add(key);

        return propertyProviderModel.read(key);
    }

    public boolean areAllDependenciesAvailable()
    {
        return propertyProviderModel.areAllDependenciesAvailable(readDependencyKeys);
    }

}
