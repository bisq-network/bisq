package org.bitcoinj.core;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Property;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static org.bitcoinj.core.Coin.*;

public class CoinMaker {


    public static final Property<Coin, Long> satoshis = new Property<>();

    public static final Instantiator<Coin> Coin = lookup ->
            valueOf(lookup.valueOf(satoshis, 100000000L));

    public static final Coin oneBitcoin = make(a(Coin));
}
