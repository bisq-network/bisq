package io.nucleo.scheduler.model;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PropertyProviderModel
{
    private static final Logger log = LoggerFactory.getLogger(PropertyProviderModel.class);

    public PropertyProviderModel()
    {
    }

    public boolean areAllDependenciesAvailable(List<String> propertyNames)
    {
        Predicate<String> isPropertyNotNull = (propertyName) -> read(propertyName) != null;
        return propertyNames.stream().allMatch(isPropertyNotNull);
    }

    public Object read(String key)
    {
        try
        {
            return getField(key).get(this);
        } catch (IllegalAccessException e)
        {
            e.printStackTrace();
            return null;
        }
    }


    // -------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------

    private Field getField(String key)
    {
        Class clazz = this.getClass();
        try
        {
            Field field = clazz.getDeclaredField(key);
            field.setAccessible(true);     // make sure a private field is accessible for reflection
            return field;
        } catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }
}
