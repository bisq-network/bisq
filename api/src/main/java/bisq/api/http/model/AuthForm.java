package bisq.api.http.model;

import org.hibernate.validator.constraints.NotEmpty;

public class AuthForm {

    @NotEmpty
    public String password;

    @SuppressWarnings("unused")
    public AuthForm() {
    }

    public AuthForm(String password) {
        this.password = password;
    }
}
