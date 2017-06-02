package com.example.dell.stripepaymentgateway.Activity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import com.example.dell.stripepaymentgateway.Controller.PollingDialogController;
import com.example.dell.stripepaymentgateway.Controller.ProgressDialogFragment;
import com.example.dell.stripepaymentgateway.R;
import com.example.dell.stripepaymentgateway.Response.GetResponse;
import com.example.dell.stripepaymentgateway.Server.RetrofitFactory;
import com.example.dell.stripepaymentgateway.service.StoreUtils;
import com.example.dell.stripepaymentgateway.service.StripeService;
import com.jakewharton.rxbinding.view.RxView;
import com.stripe.android.Stripe;
import com.stripe.android.model.Card;
import com.stripe.android.model.Source;
import com.stripe.android.model.SourceCardData;
import com.stripe.android.model.SourceParams;
import com.stripe.android.view.CardInputWidget;

import java.util.concurrent.Callable;

import retrofit2.Response;
import retrofit2.Retrofit;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class PaymentActivity extends AppCompatActivity {

    private static final String TOTAL_LABEL = "Total:";
   
    private static final String RETURN_SCHEMA = "stripe://";
    private static final String RETURN_HOST_SYNC = "payment";
    private static final String FUNCTIONAL_SOURCE_PUBLISHABLE_KEY ="YOUR_KEY";
	private static final boolean PAY_NOW=true;
    static final int PURCHASE_REQUEST = 37;

    private static final String EXTRA_PRICE_PAID = "EXTRA_PRICE_PAID",PAID="PAY";
    private CardInputWidget mCardInputWidget;
    private CompositeSubscription mCompositeSubscription;
    private ProgressDialogFragment mProgressDialogFragment;
    private Stripe mStripe;
    private Source mPollingSource;
    private String balance_id,client_tnx_id,status_id,amount_id,currency_id,seller_messege_id;
    private StoreUtils storeUtils;

    private PollingDialogController mPollingDialogController;
    private String client_id;
    private String id;

    private static final String QUERY_CLIENT_SECRET = "client_secret";
    private static final String QUERY_SOURCE_ID = "source";
    private Button mConfirmPaymentButton;
    private EditText edit_pay;
    private String price;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);
      
	  Toolbar myToolBar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolBar);
     mPollingDialogController=new PollingDialogController(this);


     storeUtils=new StoreUtils();

        mCompositeSubscription = new CompositeSubscription();

        mCardInputWidget = (CardInputWidget) findViewById(R.id.card_input_widget);
        mProgressDialogFragment =
                ProgressDialogFragment.newInstance(R.string.completing_purchase);

 

        mConfirmPaymentButton = (Button) findViewById(R.id.btn_purchase);
        edit_pay=(EditText) findViewById(R.id.edit_pay);
        RxView.clicks(mConfirmPaymentButton)
                .subscribe(new Action1<Void>() {
                    @Override
                    public void call(Void aVoid) {
                        attemptPurchase();
                    }
                });

        mStripe = new Stripe(this);
    }




    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        if (intent.getData() != null && intent.getData().getQuery() != null) {
            // The client secret and source ID found here is identical to
            // that of the source used to get the redirect URL.

            String host = intent.getData().getHost();
            String clientSecret = intent.getData().getQueryParameter(QUERY_CLIENT_SECRET);
            String sourceId = intent.getData().getQueryParameter(QUERY_SOURCE_ID);

            if (clientSecret != null
                    && sourceId != null
                    && clientSecret.equals(mPollingSource.getClientSecret())
                    && sourceId.equals(mPollingSource.getId())){
                if (RETURN_HOST_SYNC.equals(host))
                {
               Log.e("get","true");
                    completePurchase(sourceId);
                }
            }
            mPollingDialogController.dismissDialog();
        }
    }

   
    /*
     * Cleaning up all Rx subscriptions in onDestroy.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCompositeSubscription != null) {
            mCompositeSubscription.unsubscribe();
            mCompositeSubscription = null;
        }
    }

    private void attemptPurchase()
    {
        Card card = mCardInputWidget.getCard();
         price=edit_pay.getText().toString();
        price=price +"00";

        //price=storeUtils.getPriceString(Long.parseLong(price),Currency.getInstance("USD"));
        if(price.length()==0)
        {
            displayError("Enter a amount");
            return;
        }

        if (card == null) {
            displayError("Card Input Error");
            return;
        }


        dismissKeyboard();

        SourceParams cardParams=null;
            cardParams = SourceParams.createCardParams(card);

        final SourceParams finalCardParams = cardParams;
        Observable<Source> cardSourceObservable =
                Observable.fromCallable(new Callable<Source>() {
                    @Override
                    public Source call() throws Exception {
                        assert false;
                        
                        return mStripe.createSourceSynchronous(
                                finalCardParams,
                                FUNCTIONAL_SOURCE_PUBLISHABLE_KEY);                    }
                });

        final FragmentManager fragmentManager = this.getSupportFragmentManager();
        mCompositeSubscription.add(cardSourceObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(
                        new Action0() {
                            @Override
                            public void call() {
                                mProgressDialogFragment.show(fragmentManager, "progress");
                            }
                        })
                .subscribe(
                        new Action1<Source>() {
                            @Override
                            public void call(Source source) {
                                proceedWithPurchaseIf3DSCheckIsNotNecessary(source);
                            }
                        },
                        new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                if (mProgressDialogFragment != null) {
                                    mProgressDialogFragment.dismiss();
                                }
                                displayError(throwable.getLocalizedMessage());
                            }
                        }));
    }

    private void proceedWithPurchaseIf3DSCheckIsNotNecessary(Source source)
    {
        if (source == null || !Source.CARD.equals(source.getType())) {
            displayError("Something went wrong - this should be rare");
            return;
        }

        client_id=source.getClientSecret();
        id=source.getId();
        SourceCardData cardData = (SourceCardData) source.getSourceTypeModel();
        if (SourceCardData.REQUIRED.equals(cardData.getThreeDSecureStatus()))
        {
            // In this case, you would need to ask the user to verify the purchase.
            // You can see an example of how to do this in the 3DS example application.
            // In stripe-android/example.
            createThreeDSecureSource(source.getId());
        } else {
            // If 3DS is not required, you can charge the source.
            completePurchase(source.getId());
        }
    }

    void createThreeDSecureSource(String sourceId)
    {


        Log.e("url",RETURN_SCHEMA+RETURN_HOST_SYNC);
        final SourceParams threeDParams = SourceParams.createThreeDSecureParams(
                Long.parseLong(price),
                "USD",
                RETURN_SCHEMA+RETURN_HOST_SYNC,
                sourceId);

        Observable<Source> threeDSecureObservable = Observable.fromCallable(
                new Callable<Source>() {
                    @Override
                    public Source call() throws Exception {
                        Source val=mStripe.createSourceSynchronous(
                                threeDParams,
								FUNCTIONAL_SOURCE_PUBLISHABLE_KEY
                        );
                        return val;
                    }
                });

        mCompositeSubscription.add(threeDSecureObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        mProgressDialogFragment.dismiss();
                    }
                })
                .subscribe(
                        // Because we've made the mapping above, we're now subscribing
                        // to the result of creating a 3DS Source
                        new Action1<Source>() {
                            @Override
                            public void call(Source source) {
                                // Once a 3DS Source is created, that is used
                                // to initiate the third-party verification
                                showDialog(source);
                            }
                        },
                        new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                displayError(throwable.getMessage());
                            }
                        }
                ));
    }

    private void showDialog(Source source) {
        mPollingSource = source;
        mPollingDialogController.showDialog(source.getRedirect().getUrl());
    }


    private void completePurchase(String sourceId)
    {

        Retrofit retrofit = RetrofitFactory.getInstance();

        StripeService stripeService = retrofit.create(StripeService.class);



        if (price == null) {
            // This should be rare, and only occur if there is somehow a mix of currencies in
            // the CartManager (only possible if those are put in as LineItem objects manually).
            // If this is the case, you can put in a cart total price manually by calling
            // CartManager.setTotalPrice.
            return;
        }
        Log.e("Source",sourceId);
        final Observable<Response<GetResponse>> stripeResponse = stripeService.createQueryCharge(price, sourceId);
        Log.e("Response",stripeResponse.toString());

        final FragmentManager fragmentManager = getSupportFragmentManager();

        mCompositeSubscription.add(stripeResponse
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(
                        new Action0() {
                            @Override
                            public void call() {
                                if (mProgressDialogFragment != null &&
                                        !mProgressDialogFragment.isAdded())
                                    mProgressDialogFragment.show(fragmentManager, "progress");
                            }
                        })
                .doOnUnsubscribe(
                        new Action0() {
                            @Override
                            public void call() {
                                if (mProgressDialogFragment != null
                                        && mProgressDialogFragment.isVisible()) {
                                    mProgressDialogFragment.dismiss();
                                }
                            }
                        })
                .subscribe(
                        new Action1<Response<GetResponse>>()
                        {
                            @Override
                            public void call(Response<GetResponse> getResponseResponse)
                            {
                                amount_id=getResponseResponse.body().getAmount();
                                balance_id=getResponseResponse.body().getBalance_transaction();
                                status_id=getResponseResponse.body().getStatus();
                                client_tnx_id=getResponseResponse.body().getId();
                                currency_id=getResponseResponse.body().getCurrency();
                             
							 displayPurchase();
                            }
                        },
                        new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                mProgressDialogFragment.dismiss();
                                displayError(throwable.getLocalizedMessage());
                            }
                        }));
    }

    private void displayError(String errorMessage) {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Error");
        alertDialog.setMessage(errorMessage);
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

    
/*	 public static Intent createPurchaseCompleteIntent(long amount, boolean com_purchse, String amount_i,
                                                      String balance_i, String status_i,
                                                      String client_tnx_i, String currency_i) {
       
        Intent returnIntent = new Intent();
        if(com_purchse) {
            returnIntent.putExtra(EXTRA_PRICE_PAID, amount);
            returnIntent.putExtra(PAID, com_purchse);
            returnIntent.putExtra("amount",amount_i);
            returnIntent.putExtra("balance",balance_i);
            returnIntent.putExtra("status",status_i);
            returnIntent.putExtra("client",client_tnx_i);
            returnIntent.putExtra("curenncy",currency_i);
        }else
        {
            returnIntent.putExtra(EXTRA_PRICE_PAID, amount);
            returnIntent.putExtra(PAID,com_purchse);
        }
        return returnIntent;
    }
	
	
	
	  @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.e("CheckActivityResult",requestCode+" ,"+requestCode);
        if (requestCode == PURCHASE_REQUEST && resultCode == RESULT_OK) {
            long price = data.getExtras().getLong(EXTRA_PRICE_PAID, -1L);
            boolean com=data.getBooleanExtra(PAID,false);
            if (price != -1L && com) {
                displayPurchase(price,
				data.getExtras().getString("client"),
                data.getExtras().getString("balance"),
                data.getExtras().getString("status"),
                data.getExtras().getString("currency")
                        )
            }
           
        }
    }
*/


    private void displayPurchase() {
        mProgressDialogFragment.dismiss();
    AlertDialog.Builder builder = new AlertDialog.Builder(PaymentActivity.this);
                builder.setMessage("Client id: "+client_tnx_id
                        +"\r\n\nBalance id: "+balance_id
                        +"\r\n\nPrice: "+amount_id
                        +"\r\n\nStatus: "+status_id
                        +"\r\n\nCurrency: "+currency_id
                        ).setTitle("Payment Summary");
                AlertDialog alert = builder.create();
                alert.show();
    }

   

    private void dismissKeyboard() {
        InputMethodManager inputManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.toggleSoftInput(0, 0);
    }

}
