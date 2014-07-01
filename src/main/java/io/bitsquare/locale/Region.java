package io.bitsquare.locale;

import java.io.Serializable;
import java.util.Objects;

public class Region implements Serializable
{
    private static final long serialVersionUID = -5930294199097793187L;


    private final String code;

    private final String name;

    public Region(String code, String name)
    {
        this.code = code;
        this.name = name;
    }

    public int hashCode()
    {
        return Objects.hashCode(code);
    }

    public boolean equals(Object obj)
    {
        if (!(obj instanceof Region))
            return false;
        if (obj == this)
            return true;

        Region other = (Region) obj;
        return code.equals(other.getCode());
    }


    String getCode()
    {
        return code;
    }


    public String getName()
    {
        return name;
    }


    @Override
    public String toString()
    {
        return "regionCode='" + code + '\'' +
                ", continentName='" + name;
    }
}
