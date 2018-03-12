package io.bisq.core.user;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Property;
import com.natpryce.makeiteasy.SameValueDonor;
import bisq.common.storage.Storage;
import io.bisq.core.app.BisqEnvironment;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.withNull;

public class PreferenceMakers {

    public static final Property<Preferences, Storage> storage = new Property<>();
    public static final Property<Preferences, BisqEnvironment> bisqEnvironment = new Property<>();
    public static final Property<Preferences, String> btcNodesFromOptions = new Property<>();
    public static final Property<Preferences, String> useTorFlagFromOptions = new Property<>();

    public static final Instantiator<Preferences> Preferences = lookup -> new Preferences(
            lookup.valueOf(storage, new SameValueDonor<Storage>(null)),
            lookup.valueOf(bisqEnvironment, new SameValueDonor<BisqEnvironment>(null)),
            lookup.valueOf(btcNodesFromOptions, new SameValueDonor<String>(null)),
            lookup.valueOf(useTorFlagFromOptions, new SameValueDonor<String>(null)));

    public static final Preferences empty = make(a(Preferences));

}
