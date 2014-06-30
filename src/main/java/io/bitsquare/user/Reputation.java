package io.bitsquare.user;

import java.io.Serializable;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Reputation implements Serializable
{
    private static final long serialVersionUID = -3073174320050879490L;

    private static final Logger log = LoggerFactory.getLogger(Reputation.class);

    //TODO
    public Reputation()
    {
    }

    @NotNull
    @Override
    public String toString()
    {
        return "4 positive ratings in 5 cases";
    }

}
