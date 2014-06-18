package io.bitsquare.locale;

import java.io.Serializable;
import java.util.Objects;

public class Country implements Serializable
{
    private static final long serialVersionUID = -5930294199097793187L;

    private String code;
    private String name;
    private Region region;

    public Country(String code, String name, Region region)
    {
        this.code = code;
        this.name = name;
        this.region = region;
    }

    public int hashCode()
    {
        return Objects.hashCode(code);
    }

    public boolean equals(Object obj)
    {
        if (!(obj instanceof Country))
            return false;
        if (obj == this)
            return true;

        Country other = (Country) obj;
        return other.getCode().equals(code);
    }

    public String getCode()
    {
        return code;
    }

    public String getName()
    {
        return name;
    }

    public Region getRegion()
    {
        return region;
    }


    @Override
    public String toString()
    {
        return "code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", getRegion='" + region;
    }
}
