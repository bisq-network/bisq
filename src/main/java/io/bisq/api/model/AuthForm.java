package io.bisq.api.model;

import org.hibernate.validator.constraints.NotEmpty;

public class AuthForm {

    @NotEmpty
    public String password;
}
