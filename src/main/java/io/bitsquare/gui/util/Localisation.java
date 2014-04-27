package io.bitsquare.gui.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public class Localisation
{
    public static ResourceBundle getResourceBundle()
    {
        return ResourceBundle.getBundle("i18n.displayStrings", new UTF8Control());
    }

    public static String get(String key)
    {
        return Localisation.getResourceBundle().getString(key);
    }

    public static String get(String key, String... arguments)
    {
        return MessageFormat.format(Localisation.get(key), arguments);
    }
}

class UTF8Control extends ResourceBundle.Control
{

    public ResourceBundle newBundle
            (String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
            throws IllegalAccessException, InstantiationException, IOException
    {
        // The below is a copy of the default implementation.
        String bundleName = toBundleName(baseName, locale);
        String resourceName = toResourceName(bundleName, "properties");
        ResourceBundle bundle = null;
        InputStream stream = null;
        if (reload)
        {
            URL url = loader.getResource(resourceName);
            if (url != null)
            {
                URLConnection connection = url.openConnection();
                if (connection != null)
                {
                    connection.setUseCaches(false);
                    stream = connection.getInputStream();
                }
            }
        }
        else
        {
            stream = loader.getResourceAsStream(resourceName);
        }
        if (stream != null)
        {
            try
            {
                // Only this line is changed to make it to read properties files as UTF-8.
                bundle = new PropertyResourceBundle(new InputStreamReader(stream, "UTF-8"));
            } finally
            {
                stream.close();
            }
        }
        return bundle;
    }
}