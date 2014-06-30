package io.bitsquare.locale;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Localisation
{
    private static final Logger log = LoggerFactory.getLogger(Localisation.class);

    @NotNull
    public static ResourceBundle getResourceBundle()
    {
        return ResourceBundle.getBundle("i18n.displayStrings", new UTF8Control());
    }

    @NotNull
    public static String get(@NotNull String key)
    {
        try
        {
            return Localisation.getResourceBundle().getString(key);
        } catch (MissingResourceException e)
        {
            log.error("MissingResourceException for key: " + key);
            return key + " is missing";
        }
    }

    @NotNull
    public static String get(@NotNull String key, String... arguments)
    {
        return MessageFormat.format(Localisation.get(key), arguments);
    }
}

class UTF8Control extends ResourceBundle.Control
{
    @Nullable
    public ResourceBundle newBundle(@NotNull String baseName, @NotNull Locale locale, @NotNull String format, @NotNull ClassLoader loader, boolean reload) throws IllegalAccessException, InstantiationException, IOException
    {
        // The below is a copy of the default implementation.
        final String bundleName = toBundleName(baseName, locale);
        final String resourceName = toResourceName(bundleName, "properties");
        @Nullable ResourceBundle bundle = null;
        @Nullable InputStream stream = null;
        if (reload)
        {
            final URL url = loader.getResource(resourceName);
            if (url != null)
            {
                final URLConnection connection = url.openConnection();
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