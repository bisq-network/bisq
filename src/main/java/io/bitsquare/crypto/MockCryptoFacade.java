package io.bitsquare.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockCryptoFacade implements ICryptoFacade
{
    private static final Logger log = LoggerFactory.getLogger(MockCryptoFacade.class);

    @Override
    public String sign(String data)
    {
        log.info("sign data: " + data);
        return "signed contract data";
    }
}
