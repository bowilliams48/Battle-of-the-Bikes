/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

//import com.example.bluetooth.le.BluetoothLeService;
//import com.example.bluetooth.le.SampleGattAttributes;

/**
 * For a given BLE device, this Activity provides the user interface to connect,
 * display data, and display GATT services and characteristics supported by the
 * device. The Activity communicates with {@code BluetoothLeService}, which in
 * turn interacts with the Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class
            .getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private ArrayAdapter<String> mConversationArrayAdapter;
    private ListView mConversationView;

    // blechat - characteristics for HM-10 serial
    private BluetoothGattCharacteristic characteristicTX;
    private BluetoothGattCharacteristic characteristicRX;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    EditText distanceInput, weightInput;
    int distance, weight, numRotations, otherRotations;
    char rot[] = new char[2];
    char dis;
    double distTravelled, speed, otherDistTravelled, otherSpeed;
    ArrayList<Integer> aveSpeed = new ArrayList<>();
    ArrayList<Integer> otherAveSpeed = new ArrayList<>();
    TextView speedText, distText, otherSpeedText, otherDistText;
    Chronometer timer;
    int distInFeet, powerUp, atCheckpoint;
    HashMap<Integer, Integer> MET = new HashMap<>();
    long begin, endTime, powerUpBeginTime;
    boolean running, win, connectedToBlue, bothReady, started, winAlreadySet;
    public static String bleModuleName;
    Random rand = new Random();
    ImageView inFirst, inSecond;
    Button powerupButton;
    int checkpoints[] = new int[4];


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service)
                    .getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up
            // initialization.
            mBluetoothLeService.connect(mDeviceAddress);

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device. This can be a
    // result of read
    // or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);

                invalidateOptionsMenu();

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED
                    .equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED
                    .equals(action)) {
                // Show all the supported services and characteristics on the
                // user interface.

                // blechat
                // set serial characteristics
                setupSerial();

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent
                        .getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.
    // This sample
    // demonstrates 'Read' and 'Notify' features. See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for
    // the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner = new ExpandableListView.OnChildClickListener() {
        @Override
        public boolean onChildClick(ExpandableListView parent, View v,
                                    int groupPosition, int childPosition, long id) {
            if (mGattCharacteristics != null) {
                final BluetoothGattCharacteristic characteristic = mGattCharacteristics
                        .get(groupPosition).get(childPosition);
                final int charaProp = characteristic.getProperties();
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    // If there is an active notification on a characteristic,
                    // clear
                    // it first so it doesn't update the data field on the user
                    // interface.
                    if (mNotifyCharacteristic != null) {
                        mBluetoothLeService.setCharacteristicNotification(
                                mNotifyCharacteristic, false);
                        mNotifyCharacteristic = null;
                    }
                    mBluetoothLeService.readCharacteristic(characteristic);
                }
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mNotifyCharacteristic = characteristic;
                    mBluetoothLeService.setCharacteristicNotification(
                            characteristic, true);
                }

                return true;
            }
            return false;
        }
    };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        distance = 0;

    }


    // setupSerial
    //
    // set serial characteristics
    //
    private void setupSerial() {

        // blechat - set serial characteristics
        String uuid;
        String unknownServiceString = getResources().getString(
                R.string.unknown_service);

        for (BluetoothGattService gattService : mBluetoothLeService
                .getSupportedGattServices()) {
            uuid = gattService.getUuid().toString();

            // If the service exists for HM 10 Serial, say so.
            if (SampleGattAttributes.lookup(uuid, unknownServiceString) == "HM 10 Serial") {

                // get characteristic when UUID matches RX/TX UUID
                characteristicTX = gattService
                        .getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
                characteristicRX = gattService
                        .getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);

                mBluetoothLeService.setCharacteristicNotification(
                        characteristicRX, true);

                break;

            } // if

        } // for


    }

    // blechat
    @Override
    public void onStart() {
        super.onStart();

        setContentView(R.layout.home_screen);
        Button beginButton = (Button) findViewById(R.id.beginButton);

        for(int i = 0; i <= 9; i++) {
            MET.put(i, 4);
        }
        for(int i = 10; i < 12; i++) {
            MET.put(i, 6);
        }
        for(int i = 12; i < 14; i++) {
            MET.put(i, 8);
        }
        for(int i = 14; i < 16; i++) {
            MET.put(i, 10);
        }
        for(int i = 16; i < 20; i++) {
            MET.put(i, 12);
        }
        for(int i = 20; i < 30; i++) {
            MET.put(i, 16);
        }

        beginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toInput();
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null && running) {
//            mDataField.setText(data);

            rot[0] = data.charAt(0);
            rot[1] = data.charAt(3);

            Log.d(TAG, "displayData: " + data);

            int nlIdx = data.indexOf('\n');  // index of newline

            Log.d(TAG, "nlidx: " + nlIdx);

            if(data.charAt(0) == 'D' && data.charAt(7) == 'E') {
                dis = data.charAt(8);
                distance = dis - '0';

                distInFeet = distance * 5280;

                checkpoints[0] = distInFeet/5;
                checkpoints[1] = distInFeet*2/5;
                checkpoints[2] = distInFeet*3/5;
                checkpoints[3] = distInFeet*4/5;

                bothReady = true;
                begin = SystemClock.elapsedRealtime();
                timer.setBase(begin);
                timer.setFormat("Time: %s");
                timer.start();
            }
            else if((nlIdx == 10 || nlIdx == 6) && !bothReady) {
                bothReady = true;
                begin = SystemClock.elapsedRealtime();
                timer.setBase(begin);
                timer.setFormat("Time: %s");
                timer.start();
            }

            // blechat
            // add received data to screen

            // if there is a newline
            else if( nlIdx == 4 ) {
                numRotations = (rot[0] - '0');
                otherRotations = (rot[1] - '0');

                distTravelled += numRotations*4.45*1.25;
                otherDistTravelled += otherRotations*4.45*1.25;

                if(atCheckpoint < 4 && distTravelled >= checkpoints[atCheckpoint]) {
                    atCheckpoint++;
                    powerUp++;
                    powerupButton.setVisibility(View.VISIBLE);
                    if(atCheckpoint == 1)
                        increaseTension();
                    else if(atCheckpoint == 2)
                        decreaseTension();
                    else if(atCheckpoint == 3) {
                        decreaseTension();
                    }
                    else
                        increaseTension();
                }

                if(otherDistTravelled >= distInFeet/5 && otherDistTravelled < distInFeet*2/5) {
                    powerupButton.setVisibility(View.INVISIBLE);
                }
                else if(distTravelled > distInFeet/5 && powerUp > 0) {
                    powerupButton.setVisibility(View.VISIBLE);
                }

                if((SystemClock.elapsedRealtime() - powerUpBeginTime)/1000 < 15) {
                    powerupButton.setVisibility(View.INVISIBLE);
                }
                else if((SystemClock.elapsedRealtime() - powerUpBeginTime)/1000 >= 15 && powerUp > 0) {
                    powerupButton.setVisibility(View.VISIBLE);
                }



                if(aveSpeed.size() == 5) {
                    aveSpeed.remove(0);
                    aveSpeed.add(numRotations);
                }
                else {
                    aveSpeed.add(numRotations);
                }

                if(otherAveSpeed.size() == 5) {
                    otherAveSpeed.remove(0);
                    otherAveSpeed.add(otherRotations);
                }
                else {
                    otherAveSpeed.add(otherRotations);
                }

            }

            if(distTravelled >= otherDistTravelled) {
                inSecond.setVisibility(View.INVISIBLE);
                inFirst.setVisibility(View.VISIBLE);
            }
            else {
                inFirst.setVisibility(View.VISIBLE);
                inSecond.setVisibility(View.VISIBLE);
            }

            if(aveSpeed.size() == 5) {
                int total = 0;
                for(int i = 0; i < 5; i++) {
                    total += aveSpeed.get(i);
                }
                speed = total / 5;
                speed = speed * 4.45 * 3600 / 5280;
                Double speedShown = BigDecimal.valueOf(speed)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                double convDist = distTravelled/5280;
                Double distShown = BigDecimal.valueOf(convDist)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                speedText.setText("Current Speed: " + speedShown + " MPH");
                distText.setText("Current Distance: " + distShown + " / " + distance + " Mi");

            }
            else if(aveSpeed.size() == 4) {
                int total = 0;
                for(int i = 0; i < 4; i++) {
                    total += aveSpeed.get(i);
                }
                speed = total / 4;
                speed = speed * 4.45 * 3600 / 5280;
                Double speedShown = BigDecimal.valueOf(speed)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                double convDist = distTravelled/5280;
                Double distShown = BigDecimal.valueOf(convDist)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                speedText.setText("Current Speed: " + speedShown + " MPH");
                distText.setText("Current Distance: " + distShown + " / " + distance + " Mi");
            }
            else if(aveSpeed.size() == 3) {
                int total = 0;
                for(int i = 0; i < 3; i++) {
                    total += aveSpeed.get(i);
                }
                speed = total / 3;
                speed = speed * 4.45 * 3600 / 5280;
                Double speedShown = BigDecimal.valueOf(speed)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                double convDist = distTravelled/5280;
                Double distShown = BigDecimal.valueOf(convDist)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                speedText.setText("Current Speed: " + speedShown + " MPH");
                distText.setText("Current Distance: " + distShown + " / " + distance + " Mi");
            }
            else if(aveSpeed.size() == 2) {
                int total = 0;
                for(int i = 0; i < 2; i++) {
                    total += aveSpeed.get(i);
                }
                speed = total / 2;
                speed = speed * 4.45 * 3600 / 5280;
                Double speedShown = BigDecimal.valueOf(speed)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                double convDist = distTravelled/5280;
                Double distShown = BigDecimal.valueOf(convDist)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                speedText.setText("Current Speed: " + speedShown + " MPH");
                distText.setText("Current Distance: " + distShown + " / " + distance + " Mi");
            }
            else if(aveSpeed.size() == 1){
                int total = 0;
                for(int i = 0; i < 1; i++) {
                    total += aveSpeed.get(i);
                }
                speed = total / 1;
                speed = speed * 4.45 * 3600 / 5280;
                Double speedShown = BigDecimal.valueOf(speed)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                double convDist = distTravelled/5280;
                Double distShown = BigDecimal.valueOf(convDist)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                speedText.setText("Current Speed: " + speedShown + " MPH");
                distText.setText("Current Distance: " + distShown + " / " + distance + " Mi");
            }
            if(otherAveSpeed.size() == 5) {
                int otherTotal = 0;
                for(int i = 0; i < 5; i++) {
                    otherTotal += otherAveSpeed.get(i);
                }
                otherSpeed = otherTotal / 5;
                otherSpeed = otherSpeed * 4.45 * 3600 / 5280;
                Double otherSpeedShown = BigDecimal.valueOf(otherSpeed)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                double otherConvDist;
                if(otherDistTravelled > distance*5280) {
                    otherConvDist = distance;
                }
                else {
                    otherConvDist = otherDistTravelled/5280;
                }
                Double otherDistShown = BigDecimal.valueOf(otherConvDist)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                otherSpeedText.setText("Current Speed: " + otherSpeedShown + " MPH");
                otherDistText.setText("Current Distance: " + otherDistShown + " / " + distance + " Mi");

            }
            else if(otherAveSpeed.size() == 4) {
                int otherTotal = 0;
                for(int i = 0; i < 4; i++) {
                    otherTotal += otherAveSpeed.get(i);
                }
                otherSpeed = otherTotal / 4;
                otherSpeed = otherSpeed * 4.45 * 3600 / 5280;
                Double otherSpeedShown = BigDecimal.valueOf(otherSpeed)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                double otherConvDist;
                if(otherDistTravelled > distance*5280) {
                    otherConvDist = distance;
                }
                else {
                    otherConvDist = otherDistTravelled/5280;
                }
                Double otherDistShown = BigDecimal.valueOf(otherConvDist)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                otherSpeedText.setText("Current Speed: " + otherSpeedShown + " MPH");
                otherDistText.setText("Current Distance: " + otherDistShown + " / " + distance + " Mi");

            }
            else if(otherAveSpeed.size() == 3) {
                int otherTotal = 0;
                for(int i = 0; i < 3; i++) {
                    otherTotal += otherAveSpeed.get(i);
                }
                otherSpeed = otherTotal / 3;
                otherSpeed = otherSpeed * 4.45 * 3600 / 5280;
                Double otherSpeedShown = BigDecimal.valueOf(otherSpeed)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                double otherConvDist;
                if(otherDistTravelled > distance*5280) {
                    otherConvDist = distance;
                }
                else {
                    otherConvDist = otherDistTravelled/5280;
                }
                Double otherDistShown = BigDecimal.valueOf(otherConvDist)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                otherSpeedText.setText("Current Speed: " + otherSpeedShown + " MPH");
                otherDistText.setText("Current Distance: " + otherDistShown + " / " + distance + " Mi");

            }
            else if(otherAveSpeed.size() == 2) {
                int otherTotal = 0;
                for(int i = 0; i < 2; i++) {
                    otherTotal += otherAveSpeed.get(i);
                }
                otherSpeed = otherTotal / 2;
                otherSpeed = otherSpeed * 4.45 * 3 * 3600 / 5280;
                Double otherSpeedShown = BigDecimal.valueOf(otherSpeed)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                double otherConvDist;
                if(otherDistTravelled > distance*5280) {
                    otherConvDist = distance;
                }
                else {
                    otherConvDist = otherDistTravelled/5280;
                }
                Double otherDistShown = BigDecimal.valueOf(otherConvDist)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                otherSpeedText.setText("Current Speed: " + otherSpeedShown + " MPH");
                otherDistText.setText("Current Distance: " + otherDistShown + " / " + distance + " Mi");

            }
            else if(otherAveSpeed.size() == 1){
                int otherTotal = 0;
                for(int i = 0; i < 1; i++) {
                    otherTotal += otherAveSpeed.get(i);
                }
                otherSpeed = otherTotal / 1;
                otherSpeed = otherSpeed * 4.45 * 3 * 3600 / 5280;
                Double otherSpeedShown = BigDecimal.valueOf(otherSpeed)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                double otherConvDist;
                if(otherDistTravelled > distance*5280) {
                    otherConvDist = distance;
                }
                else {
                    otherConvDist = otherDistTravelled/5280;
                }
                Double otherDistShown = BigDecimal.valueOf(otherConvDist)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                otherSpeedText.setText("Current Speed: " + otherSpeedShown + " MPH");
                otherDistText.setText("Current Distance: " + otherDistShown + " / " + distance + " Mi");

            }
            if(distTravelled >= distInFeet) {
                Log.d(TAG, "Go to end game");
                if(distTravelled > otherDistTravelled) {
                    if(!winAlreadySet) {
                        win = true;
                        winAlreadySet = true;
                    }
                }
                running = false;
                gameOver();
            }
            if(otherDistTravelled >= distInFeet) {
//                    Log.d(TAG, "Go to end game");
                if(!winAlreadySet) {
                    win = false;
                    winAlreadySet = true;
                }
                Log.d(TAG, "You lose");
            }
        }
    }

    // Demonstrates how to iterate through the supported GATT
    // Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the
    // ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null)
            return;
        String uuid = null;
        String unknownServiceString = getResources().getString(
                R.string.unknown_service);
        String unknownCharaString = getResources().getString(
                R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(LIST_NAME,
                    SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData = new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService
                    .getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas = new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(LIST_NAME,
                        SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);

        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this, gattServiceData,
                android.R.layout.simple_expandable_list_item_2, new String[] {
                LIST_NAME, LIST_UUID }, new int[] { android.R.id.text1,
                android.R.id.text2 }, gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2, new String[] {
                LIST_NAME, LIST_UUID }, new int[] { android.R.id.text1,
                android.R.id.text2 });
        mGattServicesList.setAdapter(gattServiceAdapter);

    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter
                .addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    // blechat

    // btnClick
    //
    // Click handler for Send button
    //
    public void btnClick(View view) {
        sendSerial();
    }

    // blechat
    //
    // sendSerial
    //
    // Send string io out field
    private void sendSerial() {
        TextView view = (TextView) findViewById(R.id.edit_text_out);
        String message = view.getText().toString();

        Log.d(TAG, "Sending: " + message);
        final byte[] tx = message.getBytes();
        if (mConnected) {
            characteristicTX.setValue(tx);
            mBluetoothLeService.writeCharacteristic(characteristicTX);
        } // if

    }

    // Send powerup signal
    private void sendPowerup() {
        String message = "~";
        powerUp--;

        if(powerUp == 0) {
            powerupButton.setVisibility(View.INVISIBLE);
        }

        Log.d(TAG, "Sending: " + message);
        final byte[] tx = message.getBytes();
        if (mConnected) {
            characteristicTX.setValue(tx);
            mBluetoothLeService.writeCharacteristic(characteristicTX);
        }
    }

    // Increase the tension
    private void increaseTension() {
        String message = "^";

        Log.d(TAG, "Sending: " + message);
        final byte[] tx = message.getBytes();
        if (mConnected) {
            characteristicTX.setValue(tx);
            mBluetoothLeService.writeCharacteristic(characteristicTX);
        }
    }

    // Decrease the tension
    private void decreaseTension() {
        String message = "{";

        Log.d(TAG, "Sending: " + message);
        final byte[] tx = message.getBytes();
        if (mConnected) {
            characteristicTX.setValue(tx);
            mBluetoothLeService.writeCharacteristic(characteristicTX);
        }
    }

    // Send inputs signal
    private void sendInputs() {
        if(connectedToBlue) {
            String distIn = Integer.toString(distance);
            String weightIn = Integer.toString(weight);

            String message = distIn + ", " + weightIn;

            Log.d(TAG, "String to tx: " + message);

            Log.d(TAG, "Sending: Inputs");

            final byte[] tx = message.getBytes();

            if (mConnected) {
                try {
                    characteristicTX.setValue(tx);
                }
                catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "Reselect bluetooth module and guarantee bluetooth connection.", Toast.LENGTH_SHORT).show();
                    return;
                }
                mBluetoothLeService.writeCharacteristic(characteristicTX);
            }
            distTravelled = 0;
        }
        else {
            String weightIn = Integer.toString(weight);

            String message = "" + weightIn;

            Log.d(TAG, "String to tx: " + message);

            Log.d(TAG, "Sending: Inputs");

            final byte[] tx = message.getBytes();

            if (mConnected) {
                try {
                    characteristicTX.setValue(tx);
                }
                catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "Reselect bluetooth module and guarantee bluetooth connection.", Toast.LENGTH_SHORT).show();
                    return;
                }
                mBluetoothLeService.writeCharacteristic(characteristicTX);
            }
            distTravelled = 0;
        }

        if(connectedToBlue) {
            setContentView(R.layout.blue_bike_during_use);
            inFirst = (ImageView) findViewById(R.id.blueInFirst);
            inSecond = (ImageView) findViewById(R.id.blueInSecond);
            powerupButton = (Button) findViewById(R.id.bluePowerupButton);
            powerupButton.setVisibility(View.INVISIBLE);
            speedText = (TextView) findViewById(R.id.blue_blueSpeed);
            distText = (TextView) findViewById(R.id.blue_blueDistance);
            otherSpeedText = (TextView) findViewById(R.id.blue_redSpeed);
            otherDistText = (TextView) findViewById(R.id.blue_redDistance);
            timer = (Chronometer) findViewById(R.id.blueTimer);
        }
        else {
            setContentView(R.layout.red_bike_during_use);
            inFirst = (ImageView) findViewById(R.id.redInFirst);
            inSecond = (ImageView) findViewById(R.id.redInSecond);
            powerupButton = (Button) findViewById(R.id.redPowerupButton);
            powerupButton.setVisibility(View.INVISIBLE);
            speedText = (TextView) findViewById(R.id.red_redSpeed);
            distText = (TextView) findViewById(R.id.red_redDistance);
            otherSpeedText = (TextView) findViewById(R.id.red_blueSpeed);
            otherDistText = (TextView) findViewById(R.id.red_blueDistance);
            timer = (Chronometer) findViewById(R.id.redTimer);
        }

        running = true;

        powerupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendPowerup();
                powerUpBeginTime = SystemClock.elapsedRealtime();
            }

        });

    }

    private void sendEndSignal() {
        String message = "!";

        Log.d(TAG, "Sending: " + message);
        final byte[] tx = message.getBytes();
        if(mConnected) {
            characteristicTX.setValue(tx);
            mBluetoothLeService.writeCharacteristic(characteristicTX);
        }
    }

    // Bring up input screen
    private void toInput() {

        Log.d(TAG, "Connected to: " + bleModuleName);

        if(bleModuleName.compareTo("BlueBike") == 0) {
            connectedToBlue = true;
        }

        if(connectedToBlue) {
            setContentView(R.layout.activity_input_screen);

            Button submitButton = (Button) findViewById(R.id.submitButton);

            distanceInput = findViewById(R.id.distanceInput);
            weightInput = findViewById(R.id.weightInput);

            submitButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    String distString = distanceInput.getText().toString();
                    String weightString = weightInput.getText().toString();

                    try {
                        distance = Integer.valueOf(distString);
                        weight = Integer.valueOf(weightString);

                        distInFeet = distance*5280;

                        checkpoints[0] = distInFeet/5;
                        checkpoints[1] = distInFeet*2/5;
                        checkpoints[2] = distInFeet*3/5;
                        checkpoints[3] = distInFeet*4/5;

                        atCheckpoint = 0;

                        Log.d(TAG, "Dist in feet: " + distInFeet);
                        Log.d(TAG, "Weight: " + weight);

                        if(distance <= 0 || weight <= 0) {
                            Toast.makeText(getApplicationContext(), "Enter valid inputs for distance and weight. Must be positive integer values.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if(distance >= 10) {
                            Toast.makeText(getApplicationContext(), "Please enter a distance less than 10 miles.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        powerUp = 0;
                        sendInputs();
                    } catch (NumberFormatException e) {
                        Toast.makeText(getApplicationContext(), "Enter valid inputs for distance and weight. Must be positive integer values.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            });
        }
        else {
            setContentView(R.layout.red_input);

            Button submitButton = (Button) findViewById(R.id.redSubmitButton);

            weightInput = findViewById(R.id.redWeightInput);

            submitButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    String weightString = weightInput.getText().toString();

                    try {
                        weight = Integer.valueOf(weightString);

                        atCheckpoint = 0;
                        powerUp = 0;

                        Log.d(TAG, "Dist in feet: " + distInFeet);
                        Log.d(TAG, "Weight: " + weight);

                        if(weight <= 0) {
                            Toast.makeText(getApplicationContext(), "Enter valid input for weight. Must be positive integer value.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        sendInputs();
                    } catch (NumberFormatException e) {
                        Toast.makeText(getApplicationContext(), "Enter valid input for weight. Must be positive integer value.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            });
        }

    }

    private void gameOver() {
        setContentView(R.layout.game_over);

        endTime = SystemClock.elapsedRealtime();

        sendEndSignal();

        TextView gameOverDist = (TextView) findViewById(R.id.distTravelled);
        TextView gameOverAveSpeed = (TextView) findViewById(R.id.aveSpeed);
        TextView gameOverCal = (TextView) findViewById(R.id.calBurned);
        TextView timeTaken = (TextView) findViewById(R.id.timeTaken);
        ImageView first = (ImageView) findViewById(R.id.firstPlace);
        ImageView second = (ImageView) findViewById(R.id.secondPlace);

        if(win) {
            first.setVisibility(View.VISIBLE);
        }
        else {
            second.setVisibility(View.VISIBLE);
        }

        gameOverDist.setText(distance + " Mi");

        Log.d(TAG, "Begin time: " + begin);
        Log.d(TAG, "Curr time: " + endTime);

        long elapsedTime = endTime - begin;
        elapsedTime /= 1000;

        double averageSpeed = (double)distance/(double)((double)elapsedTime/3600);
        Double endSpeedShown = BigDecimal.valueOf(averageSpeed)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        gameOverAveSpeed.setText(endSpeedShown + " MPH");

        int hour = (int)(elapsedTime/3600);
        elapsedTime = elapsedTime%3600;
        int min = (int)(elapsedTime/60);
        elapsedTime = elapsedTime%60;
        int sec = (int)(elapsedTime);

        String hourString = Integer.toString(hour);
        String minString;
        String secString;

        if(min > 9) {
            minString = Integer.toString(min);
        }
        else {
            minString = "0" + Integer.toString(min);
        }

        if(sec > 9) {
            secString = Integer.toString(sec);
        }
        else {
            secString = "0" + Integer.toString(sec);
        }

        timeTaken.setText(hourString + ":" + minString + ":" + secString);

        int metVal = MET.get((int)averageSpeed);
//        int metVal = 10;
        int caloriesBurned = (int) (0.0175*metVal*weight/2.2*(min+sec/60));
        gameOverCal.setText("" + caloriesBurned);
    }
}



