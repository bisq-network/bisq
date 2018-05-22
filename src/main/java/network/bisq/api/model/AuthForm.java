package network.bisq.api.model;

import org.hibernate.validator.constraints.NotEmpty;

public class AuthForm {

    @NotEmpty
    public String password;

    public AuthForm() {
    }

    public AuthForm(String password) {
        this.password = password;
    }
}
