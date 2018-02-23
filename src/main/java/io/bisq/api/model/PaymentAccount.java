package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "paymentMethod", visible = true)
public class PaymentAccount {

    public String paymentMethod;

}
