package io.bisq.api.model;

import io.bisq.api.model.validation.NotNullItems;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.List;

public class SeedWords {

    @NotNull
    @NotNullItems
    public List<String> mnemonicCode;

    @Pattern(regexp = "\\d\\d\\d\\d-\\d\\d-\\d\\d")
    @NotEmpty
    public String walletCreationDate;

    public SeedWords() {
    }

    public SeedWords(List<String> mnemonicCode, String walletCreationDate) {
        this.mnemonicCode = mnemonicCode;
        this.walletCreationDate = walletCreationDate;
    }
}
