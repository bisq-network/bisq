/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;
import org.junit.Test;

public class Cash2Test extends AbstractAssetTest {

    public Cash2Test() {
        super(new Cash2());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("21mCcygjJivUzYW4TMTeHbEfv3Fq9NnMGEeChYcVNa3cUcRrVvy3K6mD6oMZ99fv6DQq53hbWB8Td36oVbipR7Yk6bovyL7");
        assertValidAddress("22nnHUyz7DScf3QLL27NsuAxFiuVnfDWUaDF38a35aa3CEPp2zDgcEGLfBkCdu2ohzKt7mVNfNa2NDNZHSoFWa5j3kY9os6");
        assertValidAddress("232Vo5FGYWRHhKmJ3Vz8CRCTy25RJyLJQ8wQo8mUDtZJiGLqQqPgzPJSivKR1ux9GNizSSRh6ACMR74i4qREuQrRK6KF3XH");
        assertValidAddress("247r4orbN2jcXtnSSEBxxdJgRNxexjHZRUiE3aPU7ZL4AoRF6eVh9eY3GQTi2rNuw4PSbZzttoaJPWfJnbUnJ4ZSL8tYppR");
        assertValidAddress("25vJ3RnYBveVqavx1eZshSYc5Rn9YaFWcJ2q2WouT17DMouujdDiAT3MnE7C49hdmF84zbv1TG8mTNcchuTx6L2sBXkjFgw");
        assertValidAddress("26UfQDRNs5X7FFoTrfZXFv5Phze2aotTkGPB5iwDpaM3iXQrhX87e5eUGRbiSzVbb53yuA1jpHG5xiieVkYkrhnSBCJCWHU");
        assertValidAddress("27yDdygjMcLHPRh6iiEQDqZcb6dXYWDpiRhUYZbo3ztGTtEKG72FH7mUtRevHn4rdCX51MHLJMc2afycbSrouoXoGJkA8KE");
        assertValidAddress("28t4qvTKmt34kscL3raEx9EBFjBF9t4JadFpL7vq4GsTj4PSt1mEXW36ENBZgJfW3FRJoBGP47yhj7S9CRSCXEPdVrTBG4m");
        assertValidAddress("295wF4wHgFMGsP67t3te2e2ihruA1V5Bu9KBtrVrMRky9Wwt1mZhwFANpUTCiwuxHAV7cnWhx4y9bMN4esfZXAFJ59YrG9U");
        assertValidAddress("2AZqafQ7tXmgui7ReiGdqsCqKnWVPC4uJ4RDag7pspk5jCA5dQ7ysoNeMGTQss8D4jQhp2ySvvD7XZ8JeNNgHTgULErC5BA");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("09s5NiYva6XS9bhhVc6jKYgXsH9wuHhZhWsqyAoPoWPUEiwEo9AZCDNbksvknyvZk73zwhHWSiXdgcDGLyhk5teEM7yxTgk");
        assertInvalidAddress("15a2NPZy7Xe2WZt3nKMZBsBpgNdevnKfy6PLk99eCjYw5fWQ5nu4uM6LerRBvVKTJpdGp2acZ25X3QDPHc7ocTxz1WfN2Za");
        assertInvalidAddress("34B8imA1UH29uR6PHiGpcz9MYdnL3rku27gGeLosn5XuSedFC7jPBmzaB9DoxmDA5VUZa5hPv6PSe3tJH2MJhBktMEJXaZ8");
        assertInvalidAddress("45Ghz2JRTTrLh8Z4bm6QhzZxVbq7LPiKbgjjhHPNDvVXZAJLop91zRu9A7wJMjyrU89uF7QpZB5kHhniuGZ88MJv7jRZXNi");
        assertInvalidAddress("58FFmFEGcS52mTWmhAskaKSSiX1BnHo8YcDjuhPdYBpWT9Q6ZCDz54k6cs3jPF2nk6desb1T6vRfHLfthiNf561qPct2SY1");
        assertInvalidAddress("67rMF5ve4nt2wTHYJ1pZ6j3o2YP5KDBnE7GDxnr6bpem9WcqeHzw9yKWXvtxYdpDXCBbLiX9nm97r4aEtnXq8YNb9WPn15f");
        assertInvalidAddress("798Qr9sWTprQ2sH2y5PGpfV3RAnFxUsJYY2a2VA9GjZ3MiyScD8VEh8ifWk4toYRCcbLZmRJw2dSsJBJAJ1Ava8WBzW7J12");
        assertInvalidAddress("85CQSLDNNKR4HGHwhtsxhm8jheYEvk6ngf44AhqCRWDV2XsaTHr6ittDuyfCjinAP1SzBqnVJfqNhYGDJLzxq4Y7FBVofXV");
        assertInvalidAddress("9AeKW87bkao59oadmTXGf8Jv7sMYByPrKahRbnmZEmGzRgoxGRbWqmmXuPDW6jPJSUAdpZRZn6E5B9935LtWD5gHAPpZQAh");
        assertInvalidAddress("AATHHjFhvpWXksjxJri6yaRkjTAGML2wQ7B2srLFSFXCfQy4C4UdLx5gMLBaxtfvjLe54ZfdSyRDyH94gH9Z17WpSeoBnG6");
        assertInvalidAddress("B1NiHMasw7bQsTyGLYGWh3RyUtvfJtzPyKj7NoGxMr9nJwZ4Un7vzM69EV2xpduUYEf3YMFPF58QvBmttLrUoJYDJzdVWXY");
        assertInvalidAddress("C4ak4b51DGLhGm9sPCXzHe88r9J6bWbJo5LzG4jBVjfBKhqmiQseAUcPkeSwNeNZWtVxSHuTNAk8tRZCbpZcY1rZGvGrEqZ");
        assertInvalidAddress("D8NnxEjgt5LcKLHEydcB7eUhQ1RUMSaHwN6f4w7rYcNH35ti897AAbtAk5Yon72oUee5t45ByaM651ytVVYhDtAFGfuhPBL");
        assertInvalidAddress("E3LDMVt5dzdW9NfnUT7cmUBWjeTuKiYD6Uuq6LB1ETVaJLFwrEZekxLAtLhbcSwPQg2kPeTYG4gZJMK5qmSqTUoDKUvaR7N");
        assertInvalidAddress("F3Jj7vhhZSrWRJirVT3tVSQroPzFdFxB2ChN5276kXEP93We6KJ532ZMQAj136yrrG4exJYtcYVfNZQNGNnV2rkh2bwetrP");
        assertInvalidAddress("G4Lo9KzkK9LUpKL1n6StdrYG2oBKuTqaUEyHekA9jxy7T3VZj4og91CKtfBbuiPVgzYYhL7vpDsk4fDFpyrQ8k18LovPrw7");
        assertInvalidAddress("H1Sv8KX7bnxEXmo6fh4x2y9pfiftbxnsx1QM6bhBjWgwaEqNBeyPdqi1mQKB6JSZqU1u4oEQcsUFY8yyTeP9Jh3s2jZ6qVc");
        assertInvalidAddress("I5wP79cd9oz4Pw2Gdj5Youi5J2Jft1QRwBdtRdx7bFe782Gxacw9hCpQ3jbyqs5U18EWiuzpJaAgiGrwD3aC17He7vWRxQM");
        assertInvalidAddress("J7icdECeckdDAofi9aBLwMZDvLnABZiT6XHFTu115E2pe3GqqBkFPL9dtbRUMrL5ZicQ6JMs2VamZNYyCWNt9jkEJvE4Fir");
        assertInvalidAddress("K7vZECGxgqcgtLTcbyVH9d4z68Z3SYgJFD3jiyrimiiR87qqVQUXdoTciVr6JQCMSY3qNxk1SDqVFAmv7dFbhWA6AJBtQq1");
        assertInvalidAddress("L5zCC9MAJVqXEogZ8YZFAHFN3Pp9J47vi3bXKV3tvP69FoSNzpsCgBHATYpJY7Aho958RyvbwxMDYFNqK5UiFe6WKy8xxQC");
        assertInvalidAddress("M6kpYUXiyT7TBraQqtbxuZRtAzb9Px8vz7FWtBtM2UwqP68jYGoKENeDs8u6SGK7msXevM97AaB3ZYM9pk55uXSf9dbu8BD");
        assertInvalidAddress("N8FMot8AeSzBNeNGzAwXb8eN3rNmb8neUWWL7epoVE6mSJdQ87p1byWcs6NTLQkJUkgUx7t51WhfKSQcJFrZq9EBPcWsQmP");
        assertInvalidAddress("O4c4hCkAWf7RbVXsiZu4v9S9BQ5KdWZWreykUpHdMAFxYerg4xsg4KPCoR5bq9z9ahMKb3sHuHW627zCdcBRPLe8Ft2qvDp");
        assertInvalidAddress("P6PhpdMqmcgAwV8iRjcoboFprEHgrPgYEYLzjaeU5HvRJAXu1yzWM1Q3D9zxvju6bwEv54Sccdp2S33HPx1s86uGSsZ9K8Q");
        assertInvalidAddress("Q8DP7okc13sjL7Hens8obs3mgpKyXzteccUHpuRhDyRsfxyFx8KNeBdGUNHYxWcc4pVk4gdUQq9hiZhw5K8m4pxC5rtRXo1");
        assertInvalidAddress("R9xFn1dKj6Rdsp6ZwCkpfDC2n98HHLa6M7DmV47qkYhm9vbeer1bbPoJopG4DYhspgNmTpbwe2nco3o7AsaMrT4D6MQaVxg");
        assertInvalidAddress("S89QzLjkGJiFaYdb83wrQEEtMNBizmaiz3kHYUmwZURm5mbDcGLEnBpGrqSuJVxQjHE4cpiqFF1A6Gk2ZB2uwDUb1nms8Dg");
        assertInvalidAddress("T7iiGq9NExdRwgUV6WcT58DPWA8SL8VyDdBEqY6vG8mwHedpWzU1e1Z7k5wc8DL7nyWfCYFsZKn2KcP5DXwYcvwRB9Xy4zG");
        assertInvalidAddress("U2Ec4PmgrMuVahrYGAkS5jhisb1w8b63ra33t5eoY2e5V9syeyhqU8xUqSLe22WrxMYk4gve6isGb7EpU7RLrRGzANDVUtz");
        assertInvalidAddress("V4U3d86FzU3bKvgjNNs4fuGDNRYQFoLx3XAKKtq4SiwbPLwmTpo9P2jMbwvby4YZmPCkTu6RgAdv1XispG1qtnT64Vbkn6W");
        assertInvalidAddress("W1JeT4tzGAw4b9EiLsH1pC3dWLzTSGGaaVAGux1z8PuqCH8ziEmEMDZeEhcnxgjz8n27bS7oUvb1UcUYbYTHt5jaKwicC1x");
        assertInvalidAddress("X5EAA2wJRhC3QomYEgu6NMg7AW77LfjcxhhK7YHT94XnRwGsEWmNW71Ct3UiNLbs3ab1xiWVfu4ymF3enZ19hgfM93Xp2dz");
        assertInvalidAddress("Y3YWhEp25gw1gpVtdWxdyd9Fgj5quPGuy6hC9h8knpC58YPtB4TxpZEfhb9cRgXezxCXnq1GTmQ49TCQ3CsJ8gFW3q4WN95");
        assertInvalidAddress("Z6q1WzvdPpZhiCEqzugGSPM5b39S6cdn64DtF6FcvtFg4d9hNaxXevvbkEYM8GwEH4ihW98pd6SVkR8meDu21oDbPgncEz9");
        assertInvalidAddress("a6GQ2ZBomH5WojBELhHSmQXmgNDecv65ebzhhMMkmxZVhiJxRixh29ZJ7oQK4Yg8FF4qjYMv1ykA4JA7n6KoWy18DJPaUVs");
        assertInvalidAddress("b4jmgQskiHLW8PtdX3X662eMLN2hnAnTzJsu4PLbggomVuhVFuGaUWEDntQSzpcKXgReLZAeYGEaYWPpFsTgmwEpLhEfN4m");
        assertInvalidAddress("c9Nc2eUQXBnR2PpcRrKqbA2A4DSEeLDt7LhYy4aKmvCr6JCRGtXZxrePTJpbMC83MyR8W2KCCfVwfhygpLa437DBC2NQpYH");
        assertInvalidAddress("dA5bw23DkMoXpYXbX8aPncW8ZgHKyPLngJkJS7tGPegjZycvquRKktFFf46n3VFxfrLT2UnrCkBE4LrqbWnDfRUW6Nfut6g");
        assertInvalidAddress("e49CqpKzaDG8yV6rNRuQ2VVrZi8FCTVJ4b2C5AsVkqNtGFGKCBAjTeDFhWkGJvLfFTNDUkxsgQ9bP41Uhx5G3gdCB4k6oVJ");
        assertInvalidAddress("f6x8ajQFtUReSoNYvCc7RjXpzZRngNaUU6xTAc6YvFsw8uTzwL4WDuM71cuBAW5oNAQrBjmPQmBZV8DYav6bAEVQQR7mLh2");
        assertInvalidAddress("g1Pdnp4qJ4qYPu11EEBeUhTU7Bb3b7k1Rg8hAxAqJf2XDncBqrePPrNGsgqaLeRdpBFfsZRCHTabpHvQWMzUsiLNJGFvGnR");
        assertInvalidAddress("h3DzqGR97GwWe7G3YC7D4mgCA7b68zMQmAq4r559JtcAjbG1GeXvjedTnHys9aS9vL9iG8djvVeZMdVi4S8Kc6oX2Zovt8r");
        assertInvalidAddress("iALCRLmNc8WSzbyjri4dgw9iaL6a2Uf3QYsUwsXseJfb7FCQnUgGja2ATbYpLREQTxc5VLvExQpwqFsmvhdvpx5VEVAgmyJ");
        assertInvalidAddress("j3bKBtupDeZcauKPMvzBLWHnHFn5q6ZVxDCZdvmvXJ7sgT5cQKQ8iJgARzJQ1SpzSKLwb6Kx8EVebbv4d9Y4o8AcKSQgWyx");
        assertInvalidAddress("k1u7EQDv25yGG67XtWkCSDYsbSpHFwTKESVpAxM8XSPqFrYDWPQV8KWReVmPR4piLtByikCpTgvWW5tXaxrfSuRXKo8DwWh");
        assertInvalidAddress("l6kW6xriMLxTTJBHdErywLZHQp6HchZwmZLXM4GyTfV3GFsZ7rF9JzrLx7ykNe75bqjii4b3kVX7SU98AEZmfyExNQFnZTm");
        assertInvalidAddress("m5hY6yRHGDKbjYbZiveah4DntcKbGAaqnLR5QWnF8hFP7NDouKXcFPKfWX7q8jaXdKKTJsi3Aec36Lbz4rapaxB7PKtM4mw");
        assertInvalidAddress("n9kXzy9NJhS5txFKQzh88XFFx7vi8XoLGc1UMXp7uFdK5NoaABPvemWRGm4zb6VNqgh786Q9ch1muayCQnszr8YzRYrxwWE");
        assertInvalidAddress("o7bZkG5wPryY1Ld3TPhcjECcDFeP63neFD5cA1iXC8ZPJtBsAwXFFsdbyR9eV7oKp8bB26TKWyjU86ibaJVg9RtoR5Hr18N");
        assertInvalidAddress("p37iEL6AWJaZm357fSqgQPQLzFigpuzTT9wTmUaGM7VfEzf9qmcZjP76y8bnN51bjqBKsMYPF7XLxA87vMhGAiAe5jdBhtX");
        assertInvalidAddress("q6TkiMru8ELSdXeX1DWquJ9WH7yxjFC3QdGovpUZYG7v2nCDhCxyDdz8CcqpRaqzf55xg8suNHbVAgNaUVCeCv1yETsaFJq");
        assertInvalidAddress("r9HCvadnFDSjDUAqhzorvaZmPeRqKgGG5GUnr3ph2zQteQ3TgGEvq6oiK2H967UUoUJKCH9yXmfuX3oDiyHNm2xaP7ZcPqa");
        assertInvalidAddress("s7XmwW4Q2hdN4VqEcES8KnXGTaE7Dq2w4gsjzDfBAQKWZ3S45DGBSpwb32o7Y1STZvQDtxPKVXtgbScjWizvpbC8SNYcabo");
        assertInvalidAddress("t5ZmMnkKCU8h44srsPYr5JVkvDhHBZG44QQYE2gEbUhTN4orqDgiKnMTyQYPj3XC1M5bST42rNUfR9LuWpGvkJmoCZaFvYJ");
        assertInvalidAddress("uARGu2zWWtu9k9pb1TK6zmS1ASnNVX1Kj9H3nRq6MGGhLcNshUHML4gid6grswb3aaVq47t5qqre41isaExKFDGRD9LCUHG");
        assertInvalidAddress("v1tYtfLchdzUrJow8RqMbGcH1ZAtWzSUmJHaQnrG1EypRkktGDo3tqXdk5yby64rgJjBgmMjJJ6NkRWsukHi9RTQ3XQBbqB");
        assertInvalidAddress("w4Q6hafxurmZWPSHpmp8R2GSxYiAw5DJQ3fcS9irCF3zKRnYarXLxfQPd5t24rEyYRCDAUSV1DL7CTY5guRE6dk4FgXrw5H");
        assertInvalidAddress("x7o5bwgPr1JMLMZQX8RoDT6JYfD7GwFanQa3QcMsUNbaj3siUp5i5okZxF237s5MhjWmWxWyVDxNvTq1c9MXSnQJHpNKq4d");
        assertInvalidAddress("yAENYbDcrf49FUHHDdSbtCF4EEG6LxxjvE7CsusYoi6bQUoUCfqH9yqjbuTP8i8e5vPYzWqLj1VpdMSZ4DQdhhUB8MtdvcL");
        assertInvalidAddress("z2kefw5QxZ9CmzdPCnFD766oaJ1yU1NXr5WwD2xZTpTFAGL8HRjzUzmXkdo2fqiiZyiTVAYMfxMtfJvo5QLEkHUnJEPmps4");
    }
}
