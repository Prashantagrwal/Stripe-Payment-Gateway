package com.example.dell.stripepaymentgateway.service;


import com.example.dell.stripepaymentgateway.Response.GetResponse;

import retrofit2.Response;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;
import rx.Observable;

/**
 * The {@link retrofit2.Retrofit} interface that creates our API service.
 */
public interface StripeService {

    // For simplicity, we have URL encoded our body data, but your code will likely
    // want a model class send up as JSON
    @FormUrlEncoded
    @POST("stripe.php")
    Observable<Response<GetResponse>> createQueryCharge(
            @Field("amount") String amount,
            @Field("source") String source);
}
