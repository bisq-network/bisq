package io.bitsquare.msg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SeedNodeAddress
{
    private final String id;
    private final String ip;
    private final int port;

    public SeedNodeAddress(StaticSeedNodeAddresses staticSeedNodeAddresses)
    {
        this(staticSeedNodeAddresses.getId(), staticSeedNodeAddresses.getIp(), staticSeedNodeAddresses.getPort());
    }

    public SeedNodeAddress(String id, String ip, int port)
    {
        this.id = id;
        this.ip = ip;
        this.port = port;
    }

    public String getId()
    {
        return id;
    }

    public String getIp()
    {
        return ip;
    }

    public int getPort()
    {
        return port;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum StaticSeedNodeAddresses
    {
        LOCALHOST("localhost", "127.0.0.1", 5001),
        DIGITAL_OCEAN("digitalocean.bitsquare.io", "188.226.179.109", 5000);

        private final String id;
        private final String ip;
        private final int port;

        StaticSeedNodeAddresses(String id, String ip, int port)
        {
            this.id = id;
            this.ip = ip;
            this.port = port;
        }

        public static List<StaticSeedNodeAddresses> getAllSeedNodeAddresses()
        {
            return new ArrayList<>(Arrays.asList(StaticSeedNodeAddresses.values()));
        }

        public String getId()
        {
            return id;
        }

        public String getIp()
        {
            return ip;
        }

        public int getPort()
        {
            return port;
        }
    }
}
