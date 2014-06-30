package io.bitsquare.locale;

import java.io.Serializable;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Region implements Serializable
{
    private static final long serialVersionUID = -5930294199097793187L;

    @NotNull
    private final String code;
    @NotNull
    private final String name;

    public Region(@NotNull String code, @NotNull String name)
    {
        this.code = code;
        this.name = name;
    }

    public int hashCode()
    {
        return Objects.hashCode(code);
    }

    public boolean equals(@Nullable Object obj)
    {
        if (!(obj instanceof Region))
            return false;
        if (obj == this)
            return true;

        @NotNull Region other = (Region) obj;
        return code.equals(other.getCode());
    }

    @NotNull
    String getCode()
    {
        return code;
    }

    @NotNull
    public String getName()
    {
        return name;
    }

    @NotNull
    @Override
    public String toString()
    {
        return "regionCode='" + code + '\'' +
                ", continentName='" + name;
    }
}
