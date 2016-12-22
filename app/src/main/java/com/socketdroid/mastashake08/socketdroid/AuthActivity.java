package com.socketdroid.mastashake08.socketdroid;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

import cz.msebera.android.httpclient.Header;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class AuthActivity extends AppCompatActivity {
    Socket socket;
    SharedPreferences prefs = null;
    final AsyncHttpClient client = new AsyncHttpClient(true, 80, 443);
    Random rnd = new Random();
    int n = 100000 + rnd.nextInt(900000);
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        prefs = getSharedPreferences("com.socketdroid.mastashake08.socketdroid", MODE_PRIVATE);
        setView(n);
        try {
            startWebSocket();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client2 = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    private void startWebSocket() throws URISyntaxException {
        final String id = UUID.randomUUID().toString();
        RequestParams params = new RequestParams();
        //"photos" is Name of the field to identify file on server
        params.put("id", id);
        params.put("code", n);
        //TODO: Reaming body with id "property". prepareJson converts property class to Json string. Replace this with with your own method
        client.post(getApplicationContext(), "https://socketdroid.com/add-device", params, new AsyncHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                Log.i("HTTP", "Failed..");

            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                Log.i("HTTP", "Success..");
                prefs.edit().putString("id", id).commit();
                URI uri = null;
                try {
                    uri = new URI("https://socketdroid.com:6001");
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
                socket = IO.socket(uri);
                socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

                    @Override
                    public void call(Object... args) {
                        Log.i("Socket", "Connected!");
                        socket.emit("test", "Connection Successful");
                    }

                }).on(id, new Emitter.Listener() {

                    @Override
                    public void call(Object... args) {
                        prefs.edit().putBoolean("firstrun", false).commit();
                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                        startActivity(intent);
                    }

                });
                socket.connect();
            }
        });


    }

    private void setView(int n) {

        TextView code = (TextView) findViewById(R.id.code);
        code.setText(Integer.toString(n));
    }

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    public Action getIndexApiAction() {
        Thing object = new Thing.Builder()
                .setName("Auth Page") // TODO: Define a title for the content shown.
                // TODO: Make sure this auto-generated URL is correct.
                .setUrl(Uri.parse("http://[ENTER-YOUR-URL-HERE]"))
                .build();
        return new Action.Builder(Action.TYPE_VIEW)
                .setObject(object)
                .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client2.connect();
        AppIndex.AppIndexApi.start(client2, getIndexApiAction());
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        AppIndex.AppIndexApi.end(client2, getIndexApiAction());
        client2.disconnect();
    }
}
