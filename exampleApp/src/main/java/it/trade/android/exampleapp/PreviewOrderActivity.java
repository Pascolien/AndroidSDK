package it.trade.android.exampleapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import it.trade.android.sdk.model.TradeItCallBackImpl;
import it.trade.android.sdk.model.TradeItErrorResult;
import it.trade.android.sdk.model.TradeItOrder;
import it.trade.tradeitapi.model.TradeItPlaceStockOrEtfOrderResponse;
import it.trade.tradeitapi.model.TradeItPreviewStockOrEtfOrderResponse;

import static it.trade.android.exampleapp.MainActivity.PREVIEW_ORDER_PARAMETER;

public class PreviewOrderActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getName();
    private String orderId;
    private TradeItOrder order;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_order);
        textView = (TextView) this.findViewById(R.id.preview_order_textview);
        textView.setMovementMethod(new ScrollingMovementMethod());
        Intent intent = getIntent();
        order = intent.getParcelableExtra(PREVIEW_ORDER_PARAMETER);
        final Button placeOrderButton = (Button) this.findViewById(R.id.place_trade_button);
        order.previewOrder(new TradeItCallBackImpl<TradeItPreviewStockOrEtfOrderResponse>() {
            @Override
            public void onSuccess(TradeItPreviewStockOrEtfOrderResponse response) {
                textView.setText(response.toString());
                orderId = response.orderId;
                placeOrderButton.setEnabled(true);
            }

            @Override
            public void onError(TradeItErrorResult error) {
                Log.e(TAG, "ERROR previewOrder: " + error);
                textView.setText("ERROR previewOrder: " + error);
            }
        });
    }

    public void placeTrade(View view) {
        order.placeOrder(orderId, new TradeItCallBackImpl<TradeItPlaceStockOrEtfOrderResponse>() {
            @Override
            public void onSuccess(TradeItPlaceStockOrEtfOrderResponse placeOrderResponse) {
                textView.append(placeOrderResponse.toString());
            }

            @Override
            public void onError(TradeItErrorResult error) {
                Log.e(TAG, "ERROR placeOrder: " + error.toString());
                textView.setText("ERROR placeOrder: " + error);
            }
        });
    }
}
