package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "paymentMethod", visible = true)
public class PaymentAccount {

    public String id;

    public String paymentMethod;

    public String selectedTradeCurrency;

    public List<String> tradeCurrencies = new ArrayList<>();

}
