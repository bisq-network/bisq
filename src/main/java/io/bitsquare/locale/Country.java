package io.bitsquare.locale;

import java.io.Serializable;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Country implements Serializable
{
    private static final long serialVersionUID = -5930294199097793187L;

    @NotNull
    private final String code;
    @NotNull
    private final String name;
    @NotNull
    private final Region region;

    public Country(@NotNull String code, @NotNull String name, @NotNull Region region)
    {
        this.code = code;
        this.name = name;
        this.region = region;
    }

    public int hashCode()
    {
        return Objects.hashCode(code);
    }

    public boolean equals(@Nullable Object obj)
    {
        if (!(obj instanceof Country))
            return false;
        if (obj == this)
            return true;

        final Country other = (Country) obj;
        return code.equals(other.getCode());
    }

    @NotNull
    public String getCode()
    {
        return code;
    }

    @NotNull
    public String getName()
    {
        return name;
    }

    @NotNull
    public Region getRegion()
    {
        return region;
    }


    @NotNull
    @Override
    public String toString()
    {
        return "code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", getRegion='" + region;
    }
}
