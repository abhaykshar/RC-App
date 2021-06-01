/*
 * Name: Abhay Sharma
 * Date: May 30, 2019
 * Description: App that acts as a remote control for the stepper motor and dc motor connected to an arduino.
 *              Uses accelerometer to get data about how the phone is tilted and sends the value to an ip address which is where the raspberry pi is hosting the server
 *              Has a button that sends data to the button as long as the button is being pressed. The raspberry pi will take that data and send it to the arduino with
 *              a dc motor attached and the longer that the button is pressed, the faster the motor will run
 *              Has a brake button that sends data to the raspberry pi indicating that the motor should stop
 *
 * Extra documentation:
 * When a value of 120 is sent through the url, the arduino uses it as a signal to stop the DC motor
 * When a value of 240 is sent through the url, the arduino uses it as a signal to keep increasing the speed of the DC motor
 * Values of 20 to 100 are used by the arduino for turning the stepper motor
 * with 20 being completely left, 100 being completely right, and 60 is straight
 */

package com.androidapp.wirelesspi;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.androidapp.wirelesspi.R;

public class TiltSensor extends Activity implements SensorEventListener {

    // declares variable that is the sensor manager to handle sensor data
    private SensorManager sensorManager;
    // declares variable of type Sensor which will handle the accelerometer
    private Sensor sensor;

    // declares variable of type textview to use it to change the text it displays
    private TextView tiltText;

    // creates a handler that will be used to constantly send data to the raspberry pi
    private Handler repeatUpdateHandler = new Handler();
    // autoIncrement variable that will be on when the accelerate button is being pressed
    private boolean autoIncrement = false;


    // create a request queue to handle requests
    RequestQueue queue;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // attach the activitysensor layout
        setContentView(R.layout.activitysensor);
        // keep the screen locked in landscape view
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        //declaring Sensor Manager and sensor type
        sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // set queue to a Volley queue (pre made)
        queue = Volley.newRequestQueue(this);
        // set up textView that will display the stepper position being sent to the raspberry pi
        tiltText = (TextView)findViewById(R.id.tiltText);

        // set btnSpeed and btnBrake to their respective button ids
        Button btnSpeed = findViewById(R.id.btnSpeed);
        Button btnBrake = findViewById(R.id.btnBrake);

        /* class that runs the increment() function every 50 milliseconds if autoIncrement is true (which is only true
           when the accelerate is being held down) so that there is constant data being sent to the raspberry pi
           that tells it to speed up the motor */
        class RepetitiveUpdater implements Runnable {
            @Override
            public void run() {
                if (autoIncrement) {
                    increment();
                    repeatUpdateHandler.postDelayed(new RepetitiveUpdater(), 50);
                }
            }

        }

        // set listener that will send the value of 120 through the url that is the server of the raspberry pi, indicating the dc motor to stop
        btnBrake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // send request through url to the raspberry pi server of value 120
                StringRequest tReq = new StringRequest(Request.Method.GET, "http://10.145.152.165/api?val=120", new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // logs response when server is accessed
                        Log.d("turn", response);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // logs error when the server could not be accessed
                        Log.d("X", "error");
                    }
                });
                queue.add(tReq);

            }
        });

        // onclick listener for accelerate button that calls increment when button clicked
        btnSpeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                increment();
            }
        });

        /* onlongclick listener for accelerate button that sets the autoIncrement to true as the button is pressed and then
           creates a new RepetitiveUpdater so the increment() function is called every 50 milliseconds as long as autoincrement is true*/
        btnSpeed.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                autoIncrement = true;
                repeatUpdateHandler.post(new RepetitiveUpdater());
                return false;
            }
        });

        // onTouch listener for the accelerate button so when the button is not being pressed and autoIncrement is true, autoIncrement is false and increment() is not called
        btnSpeed.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP && autoIncrement) {
                    autoIncrement = false;
                }
                return false;
            }
        });

    }

    // function that must be present as the class extends from SensorEventListener
    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
    }

    // function that runs when the sensor is changed
    @Override
    public void onSensorChanged(SensorEvent event) {

        // store event.values[1] which is a float that represents how much a phone is tilted
        float y = event.values[1];

        // since we do not want the stepper to turn a lot, we make y be no more than 5 and no less than -5
        if (y < -5)
            y = -5;
        if (y > 5)
            y = 5;
        // set steps equal to the function 8y + 60 such that y = -5 corresponds to steps=20, y=0 corresponds to steps=60, and y=5 corresponds to steps=100
        float steps = (y * 8) + 60;
        // set the text of tiltText to the steps variable
        tiltText.setText(Float.toString(Math.round(steps)));


        // send request through url to the raspberry pi server with the value being the number of steps as an integer so that the arduino can make the stepper motor turn
        StringRequest tReq = new StringRequest(Request.Method.GET, "http://10.145.152.165/api?val=" + Integer.toString((int)Math.round(steps)) , new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                // logs response when the server is accessed
                Log.d("turn", response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // logs error when the server could not be accessed
                Log.d("X", "error");
            }
        });

        queue.add(tReq);
    }

    // starts listening to the sensor when function is resumed, listens every 200 milliseconds
    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    // onPause function that unregisters the listening to the sensor when paused
    @Override
    protected void onPause() {
        super.onPause();
        //unregister Sensor listener
        sensorManager.unregisterListener(this);
    }


    public void increment() {
        // pre:  function will only be called when autoIncrement is true or on initial click of accelerate button
        // post: sends url request raspberry pi server wiht value 240, indicating the arudino to increase the speed of the DC motor

        // send request through url to the raspberry pi server with the value 240, indicating the arudino to increase the speed of the DC motor
        StringRequest sReq = new StringRequest(Request.Method.GET, "http://10.145.152.165/api?val=240", new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                // logs response when the server is accessed
                Log.d("speed", response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // logs error when the server could not be accessed
                Log.d("X", "Error");
            }
        });

        queue.add(sReq);


    }
}

