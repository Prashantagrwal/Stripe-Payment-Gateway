package com.example.dell.stripepaymentgateway.Response;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Created by DELL on 02/06/2017.
 */

public class GetResponse implements Serializable
{
    @SerializedName("id")
    private String id;
    @SerializedName("amount")
    private String amount;
    @SerializedName("balance_transaction")
    private String balance_transaction;

    @SerializedName("currency")
    private String currency;

    @SerializedName("status")
    private String status;

    public String getId() {
        return id;
    }

    public String getAmount() {
        return amount;
    }

    public String getBalance_transaction() {
        return balance_transaction;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }
}
