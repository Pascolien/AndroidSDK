package it.trade.android.exampleapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import java.util.List;

import it.trade.android.sdk.model.TradeItLinkedBroker;

import static it.trade.android.exampleapp.MainActivity.LINKED_BROKERS_PARAMETER;

public class LinkedBrokersActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_linked_brokers);
        final TextView textView = (TextView) this.findViewById(R.id.linked_brokers_textview);
        textView.setMovementMethod(new ScrollingMovementMethod());
        Intent intent = getIntent();
        List<TradeItLinkedBroker> linkedBrokers = intent.getParcelableArrayListExtra(LINKED_BROKERS_PARAMETER);

        if (linkedBrokers.isEmpty()) {
            textView.setText("No linked brokers!");
        } else {
            textView.setText("# of linkedBrokers: " + linkedBrokers.size() + " : " + linkedBrokers.toString());
        }
    }
}
