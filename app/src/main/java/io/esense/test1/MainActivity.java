package io.esense.test1;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.esense.esenselib.*;

import static io.esense.test1.R.*;
import static java.lang.String.*;

// import Polar SDK packages
import io.reactivex.CompletableObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.errors.PolarInvalidArgument;
import polar.com.sdk.api.model.PolarAccelerometerData;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarEcgData;
import polar.com.sdk.api.model.PolarExerciseData;
import polar.com.sdk.api.model.PolarExerciseEntry;
import polar.com.sdk.api.model.PolarHrBroadcastData;
import polar.com.sdk.api.model.PolarHrData;
import polar.com.sdk.api.model.PolarOhrPPGData;
import polar.com.sdk.api.model.PolarOhrPPIData;
import polar.com.sdk.api.model.PolarSensorSetting;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final static String TAG = MainActivity.class.getSimpleName(); // Polar SDK

    private Handler handler_UI;
    private Button btnConnecteSense;
    private Button btnConnectPolar;
    private Button btnRecord;
    private TextView status_text;
    private TextView stage_text;
    private EditText editText;
    private TextWatcher textWatcher;

    private ESenseConnectionListenerImp listener_eSense;
    private ESenseSensorListenerImp sensorListener_eSense;
    //private ESenseManager manager_r;              // if the right earbud has to be used, activate this and replace all uses of manager_l by manager_r
    private ESenseManager manager_eSense;
    private int connect_timeout = 5000;
    private int stage;
    private MediaRecorder audioRecorder;
    private File audioOutputFile;
    private String audioOutputFileStr;
    private FileOutputStream polarHRout;

    private String eSense_ID;

    private Boolean connected_eSense;
    private Boolean connected_Polar;
    private Boolean recording_active;
    private Boolean recording_Polar_enabled;
    private Boolean audio_recording_enabled;

    // Handler message
    public static final int CONNECTED_ESENSE = 100;
    public static final int DISCONNECTED_ESENSE = -150;
    public static final int ESENSE_FOUND = 300;
    public static final int ESENSE_NOT_FOUND = -350;
    public static final int CONNECTED_POLAR = 200;
    public static final int DISCONNECTED_POLAR = -250;


    //private BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

    // Polar SDK variables
    PolarBleApi api;
    Disposable broadcastDisposable;
    Disposable ecgDisposable;
    Disposable accDisposable;
    Disposable ppgDisposable;
    Disposable ppiDisposable;
    Disposable scanDisposable;
    String POLAR_ID = "14C46027"; // or Bluetooth address like F5:A7:B8:EF:7A:D1
    Disposable autoConnectDisposable;
    PolarExerciseEntry exerciseEntry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.activity_main);

        // Starting implementation here

        connected_eSense = false;
        connected_Polar = false;
        recording_active = false;
        audio_recording_enabled = true;

        status_text = findViewById(id.stage_text);
        stage_text = findViewById(id.stage_text);

        // Set listeners for the buttons

        btnConnecteSense = findViewById(id.btnConnect);
        btnConnecteSense.setOnClickListener(this);

        btnConnectPolar = findViewById(id.btnConnect2);
        btnConnectPolar.setOnClickListener(this);

        btnRecord = findViewById(id.btnStart);
        btnRecord.setOnClickListener(this);

        editText = findViewById(id.editText);
        editText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                //
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                eSense_ID = charSequence.toString();
                Log.d(TAG, "Entered new eSense device ID: " + eSense_ID);
            }

            @Override
            public void afterTextChanged(Editable editable) {
                //
            }
        });


        // Define a UI handler
        handler_UI = new Handler() {

            @Override
            public void handleMessage(Message inputMessage) {
                super.handleMessage(inputMessage);

                if (inputMessage.what == CONNECTED_ESENSE) {
                    onConnect_eSense();
                    Log.d(TAG, "UI handler message received: CONNECTED_ESENSE");
                } else if (inputMessage.what == CONNECTED_POLAR) {
                    onConnect_Polar();
                    Log.d(TAG, "UI handler message received: CONNECTED_POLAR");
                } else if (inputMessage.what == DISCONNECTED_ESENSE) {
                    onDisconnect_eSense();
                    Log.d(TAG, "UI handler message received: DISCONNECTED_ESENSE");
                } else if (inputMessage.what == DISCONNECTED_POLAR) {
                    onDisconnect_Polar();
                    Log.d(TAG, "UI handler message received: DISCONNECTED_POLAR");
                } else if (inputMessage.what == ESENSE_FOUND) {
                    Log.d(TAG, "UI handler message received: ESENSE_FOUND");
                    setStatus_text("eSense device found! Establishing connection...");
                } else if (inputMessage.what == ESENSE_NOT_FOUND) {
                    setStatus_text("eSense device (" + eSense_ID + ") not found.");
                    Log.d(TAG, "UI handler message received: ESENSE_NOT_FOUND");
                }

            }
        };

        stage = 0;
        status_text.setVisibility(View.VISIBLE);
        btnConnecteSense.setVisibility(View.VISIBLE);
        btnConnectPolar.setVisibility(View.VISIBLE);
        btnRecord.setVisibility(View.VISIBLE);
        stage_text.setVisibility(View.VISIBLE);

        setStatus_text("eSense device not connected.");
        updateStage_text();
        //setButton_text("Connect");

        // Ask for permissions (Bluetooth, storage etc.) if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        // callback is invoked after granted or denied permissions


        // Prepare connections
        Log.d(TAG, "Preparing connections...");
        listener_eSense = new ESenseConnectionListenerImp();
        listener_eSense.instantiate(this, handler_UI);
        //manager_eSense = new ESenseManager(eSense_ID, MainActivity.this.getApplicationContext(), listener_eSense);

        // Polar SDK

        api = PolarBleApiDefaultImpl.defaultImplementation(this, PolarBleApi.ALL_FEATURES);
        api.setPolarFilter(false);
        api.setApiLogger(new PolarBleApi.PolarBleApiLogger() {
            @Override
            public void message(String s) {
                Log.d(TAG,s);
            }
        });
        Log.d(TAG,"version: " + PolarBleApiDefaultImpl.versionInfo());

        api.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean powered) {
                Log.d(TAG,"BLE power: " + powered);
            }

            @Override
            public void deviceConnected(PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG,"CONNECTED: " + polarDeviceInfo.deviceId);
                POLAR_ID = polarDeviceInfo.deviceId;

                connected_Polar = true;
                handler_UI.sendEmptyMessage(CONNECTED_POLAR);
            }

            @Override
            public void deviceConnecting(PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG,"CONNECTING: " + polarDeviceInfo.deviceId);
                POLAR_ID = polarDeviceInfo.deviceId;
            }

            @Override
            public void deviceDisconnected(PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG,"DISCONNECTED: " + polarDeviceInfo.deviceId);
                ecgDisposable = null;
                accDisposable = null;
                ppgDisposable = null;
                ppiDisposable = null;

                connected_Polar = false;
                handler_UI.sendEmptyMessage(DISCONNECTED_POLAR);
            }

            @Override
            public void ecgFeatureReady(String identifier) {
                Log.d(TAG,"ECG READY: " + identifier);
                // ecg streaming can be started now if needed
            }

            @Override
            public void accelerometerFeatureReady(String identifier) {
                Log.d(TAG,"ACC READY: " + identifier);
                // acc streaming can be started now if needed
            }

            @Override
            public void ppgFeatureReady(String identifier) {
                Log.d(TAG,"PPG READY: " + identifier);
                // ohr ppg can be started
            }

            @Override
            public void ppiFeatureReady(String identifier) {
                Log.d(TAG,"PPI READY: " + identifier);
                // ohr ppi can be started
            }

            @Override
            public void biozFeatureReady(String identifier) {
                Log.d(TAG,"BIOZ READY: " + identifier);
                // ohr ppi can be started
            }

            @Override
            public void hrFeatureReady(String identifier) {
                Log.d(TAG,"HR READY: " + identifier);
                // hr notifications are about to start
            }

            @Override
            public void disInformationReceived(String identifier, UUID uuid, String value) {
                Log.d(TAG,"uuid: " + uuid + " value: " + value);

            }

            @Override
            public void batteryLevelReceived(String identifier, int level) {
                Log.d(TAG,"BATTERY LEVEL: " + level);

            }

            @Override
            public void hrNotificationReceived(String identifier, PolarHrData data) {
                Log.d(TAG,"HR value: " + data.hr + " rrsMs: " + data.rrsMs + " rr: " + data.rrs + " contact: " + data.contactStatus + "," + data.contactStatusSupported);
                if (isRecording()) {
                    if (recording_Polar_enabled) {
                        writeNewPolarData(System.currentTimeMillis(), data.hr, data.rrsMs, data.rrs, polarHRout);
                    }
                }
            }

            @Override
            public void polarFtpFeatureReady(String s) {
                Log.d(TAG,"FTP ready");
            }
        });

        setStatus_text("Ready to connect.");
    }

    // Polar SDK functions

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if(requestCode == 1) {
            Log.d(TAG,"Bluetooth ready");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        api.backgroundEntered();
    }

    @Override
    public void onResume() {
        super.onResume();
        api.foregroundEntered();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        api.shutDown();
    }

    @Override
    public void onClick(View view) {


        switch (view.getId()) {

            case id.btnConnect:         // Button "Connect to eSense" clicked

                // Check if recording is currently active
                if (isRecording()) {
                    setStatus_text("Currently recording already! --- LIVE");
                    break;
                }

                // Check if eSense device is already connected
                if (isConnected_eSense()) {
                    setStatus_text("Disconnecting from eSense device...");
                    disconnect_from_eSense();
                    break;
                }

                /*// Check if stage code is correct and consistent
                if (!((stage == 0) || (stage == 2))) {
                    Log.d(TAG, "Stage code should be 0 or 2 at this point, but is " + stage);
                }*/

                // Start connection procedure and notify user
                setStatus_text("Connecting to eSense...");
                connect_to_eSense();

                break;


            case id.btnConnect2:        // Button "Connect to Polar HR" clicked

                // Check if recording is currently active
                if (isRecording()) {
                    setStatus_text("Currently recording already! --- LIVE");
                }

                // Check if Polar HR monitor is already connected
                if (isConnected_Polar()) {
                    setStatus_text("Disconnecting from Polar HR device...");
                    disconnect_from_Polar();
                    break;
                }

                /*// Check if stage code is correct and consistent
                if (!((stage == 0) || (stage == 1))) {
                    Log.d(TAG, "Stage code should be 0 or 1 at this point, but is " + stage);
                }*/

                // Start connection procedure and notify user
                setStatus_text("Connecting to Polar HR monitor...");
                connect_to_Polar();

                break;

            case id.btnStart:           // Button "Start recording" or "Stop recording" clicked

                // Branch according to recording status

                if (!isRecording()) {   // Button is START RECORDING

                    // Check established connections

                    if (!((isConnected_eSense() || (isConnected_Polar())))) {           // None of the devices is connected. Connect first
                        setStatus_text("No devices connected! Please connect first.");
                        break;
                    }
                    else {          // at least one device is connected

                        if (audio_recording_enabled) {
                            start_recording_audio();
                        }

                        if (isConnected_eSense()) {
                            start_recording_eSense();
                        }

                        if (isConnected_Polar()) {
                            start_recording_Polar();
                        }

                        // Update recording status
                        recording_active = true;

                        // Update button text and color
                        btnRecord.setText(R.string.button_stop_recording);
                        btnRecord.setBackgroundColor(getResources().getColor(R.color.colorBtnStop, null));

                        // Update status text
                        setStatus_text("Recording active! --- LIVE");
                        Log.d(TAG, "Recording active! LIVE ...");

                        break;
                    }
                }

                // ----- ----- ----- ----- ----- ----- ********* ******** ******* ----- ----- ----- ----- ----- -----

                else {          // Button is STOP RECORDING

                    if (audio_recording_enabled) {
                        stop_recording_audio();
                    }
                    if (isConnected_eSense()) {
                        stop_recording_eSense();
                    }

                    if (isConnected_Polar()) {
                        stop_recording_Polar();
                    }

                    // Update recording status
                    recording_active = false;

                    // Update button text and color
                    btnRecord.setText(R.string.button_start_recording);
                    btnRecord.setBackgroundColor(getResources().getColor(R.color.colorBtnStart, null)); // check the null theme TODO

                    // Update status text
                    setStatus_text("Recording stopped.");

                    break;
                }

            /*case id.btnDisconnect:

                // Check if connection is currently active
                if (!isConnected_eSense()) {
                    setStatus_text("eSense device is not connected.");
                    break;
                }
                else {
                    setStatus_text("Disconnecting from eSense device...");
                    disconnect_from_eSense();
                    break;
                }

            case id.btnDisconnect2:

                // Check if connection is currently active
                if (!isConnected_Polar()) {
                    setStatus_text("Polar HR monitor is not connected.");
                    break;
                }
                else {
                    setStatus_text("Disconnecting from Polar HR device...");
                    disconnect_from_Polar();
                    break;
                }*/


            default:
                // Probably nothing here. Just in case something else than one of the buttons gets clicked
                break;

        }

    }

    public void setStatus_text(String text) {
        status_text.setText(text);
    }

    // ----
    // Notification functions

    public void onConnect_eSense() {
        connected_eSense = true;
        setStatus_text("Connected to eSense device!");

        // Update buttons
        //btnConnecteSense.setText("Disconnect from eSense");
        update_button_text(btnConnecteSense, getResources().getString(R.string.button_disconnect_esense));
        btnConnecteSense.setBackgroundColor(getResources().getColor(color.colorBtnConn));
        btnRecord.setBackgroundColor(getResources().getColor(color.colorBtnStart));
    }

    public void onConnect_Polar() {
        connected_Polar = true;
        setStatus_text("Connected to Polar HR device!");

        // Update buttons
        //btnConnectPolar.setText("Disconnect from Polar HR");
        update_button_text(btnConnectPolar, getResources().getString(R.string.button_disconnect_polar));
        btnConnectPolar.setBackgroundColor(getResources().getColor(color.colorBtnConn));
        btnRecord.setBackgroundColor(getResources().getColor(color.colorBtnStart));
    }

    public void onDisconnect_eSense() {
        connected_eSense = false;
        setStatus_text("Disconnected from eSense device.");

        // Update buttons
        //btnConnecteSense.setText("Connect to eSense");
        update_button_text(btnConnecteSense, getResources().getString(R.string.button_connect_esense));
        btnConnecteSense.setBackgroundColor(getResources().getColor(color.colorBtnDisc));

        if (!isConnected_Polar()) {
            btnRecord.setBackgroundColor(getResources().getColor(color.colorIdle));
        }
    }

    public void onDisconnect_Polar() {
        connected_Polar = false;
        setStatus_text("Disconnected from Polar HR monitor.");

        // Update buttons
        //btnConnectPolar.setText("Connect to Polar HR");
        update_button_text(btnConnectPolar, getResources().getString(R.string.button_connect_polar));
        btnConnectPolar.setBackgroundColor(getResources().getColor(color.colorBtnDisc));

        if (!isConnected_eSense()) {
            btnRecord.setBackgroundColor(getResources().getColor(color.colorIdle));
        }
    }

    // -----

    private void update_button_text(Button btn, String txt) {
        btn.setText(txt);
    }

    public void updateStage_text() {
        stage_text.setText("Stage: " + stage);
    }

    public void setStage(int new_stage_number) {
        stage = new_stage_number;
    }

    public Boolean isConnected_eSense() {
        return connected_eSense;
    }

    public Boolean isConnected_Polar() {
        return connected_Polar;
    }

    public Boolean isRecording() {
        return recording_active;
    }


    /* **************** ******************* ******************** ****************** */


    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Get the directory for the storage of the logged sensor data */
    public File getPublicDocStorageDir() {
        /*File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "esense");*/

        File dir = new File(getExternalFilesDir("EsenseLogs"), "");

        /*if (!dir.exists()) {
            Log.e("Error", "Directory not created.");
            dir.mkdirs();
        }*/

        try {
            if (!dir.exists()) {
                dir.mkdirs();
                Log.d(TAG, "Directory 'EsenseLogs' successfully created.");
            }
        } catch (Exception e) {
            Log.w("Error creating dir.", e.toString());
        }

        String filepath = "esense_log_" + valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())) + ".csv";
        File file = new File(getExternalFilesDir("eSenseLogs"), filepath);

        return file;
    }

    public File getPolarHRFile() {
        File dir = new File(getExternalFilesDir("EsenseLogs"), "");

        try {
            if (!dir.exists()) {
                dir.mkdirs();
                Log.d(TAG, "Directory 'eSenseLogs' successfully created.");
            }
        } catch (Exception e) {
            Log.w("Error creating dir.", e.getLocalizedMessage());
        }

        String filepath = "hr_log_" + valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())) + ".csv";
        File polarHRoutput = new File(getExternalFilesDir("eSenseLogs"), filepath);

        return polarHRoutput;
    }

    private void writeNewPolarData(long time, int hr, List<Integer> rrsMs, List<Integer> rr, FileOutputStream target_output) {

        try {
            target_output.write((System.getProperty("line.separator") + time + ";" + hr + ";" + rrsMs + ";" + rr).getBytes());
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }

    }

    // Main functions (of the buttons)

    private void connect_to_eSense() {

        // Prepare connection
        manager_eSense = new ESenseManager(eSense_ID, MainActivity.this.getApplicationContext(), listener_eSense);

        Log.d(TAG, "Trying to connect now...");
        setStatus_text("Connecting...");
        try {
            manager_eSense.connect(connect_timeout);             // connect to left earbud
        } catch (Exception e) {
            setStatus_text("Connection failed (exception thrown)! " + e.getLocalizedMessage());
        }
        //setStatus_text("Stage: " + stage);
        //setButton_text("Start scanning");


        // Update stage
        //updateStage_text();

    }

    private void connect_to_Polar() {

        try {
            api.connectToDevice(POLAR_ID);
            //api.searchForDevice();        // user has to select device from list
            //api.autoConnectToDevice(-50, null, ).subscribe();
            // TODO
            /* if(scanDisposable == null) {
                scanDisposable = api.searchForDevice().observeOn(AndroidSchedulers.mainThread()).subscribe(
                        new Consumer<PolarDeviceInfo>() {
                            @Override
                            public void accept(PolarDeviceInfo polarDeviceInfo) throws Exception {
                                Log.d(TAG, "Polar device found ID: " + polarDeviceInfo.deviceId + " address: " + polarDeviceInfo.address + " rssi: " + polarDeviceInfo.rssi + " name: " + polarDeviceInfo.name + " isConnectable: " + polarDeviceInfo.isConnectable);
                            }
                        },
                        new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) throws Exception {
                                Log.d(TAG, "" + throwable.getLocalizedMessage());
                            }
                        },
                        new Action() {
                            @Override
                            public void run() throws Exception {
                                Log.d(TAG, "complete");
                            }
                        }
                );
            }else{
                scanDisposable.dispose();
                scanDisposable = null;
            }*/
        } catch (Exception e) {
            Log.e(TAG, "Error: Connection to Polar HR device failed with error: " + e.getLocalizedMessage());
        }

    }

    private void start_recording_eSense() {

        // Set up sensor listener
        Log.d(TAG, "Setting up sensor listener...");
        setStatus_text("Setting up sensor listener...");
        sensorListener_eSense = new ESenseSensorListenerImp();
        sensorListener_eSense.instantiate(getPublicDocStorageDir(), this.getApplicationContext());
        manager_eSense.registerSensorListener(sensorListener_eSense, 100);
        Log.d(TAG, "Sensor listener registered and set up.");
        setStatus_text("Sensor listener registered and set up.");

    }

    private void start_recording_audio() {

        // Prepare microphone output file
        audioOutputFile = new File(getExternalFilesDir("eSenseLogs"), "");
        audioOutputFileStr = audioOutputFile.getAbsolutePath() + "/audio" + valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())) + ".3gp";

        // *** Audio recording settings. Adjust to needs of python tool.
        // Set up MediaRecorder
        audioRecorder = new MediaRecorder();
        Log.d(TAG, "MediaRecorder object set up.");
        audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        Log.d(TAG, "Audio source (mic) set.");
        audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        Log.d(TAG, "Output format (OGG) set.");
        audioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        Log.d(TAG, "Audio encoding (AAC) set.");
        // If necessary, insert code for sampling rate (should be 44,1 kHz)
        audioRecorder.setAudioSamplingRate(44100);
        Log.d(TAG, "Audio sampling rate (44,1 kHz) set.");
        audioRecorder.setOutputFile(audioOutputFileStr);
        Log.d(TAG, "Output file settings set.");
        //*** */

        // Set up microphone audio recorder

        try {
            audioRecorder.prepare();
            audioRecorder.start();
            Log.d(TAG, "Audio recorder active.");
        }
        catch (Exception e) {
            // handle exception
            Log.e(TAG, "Error: Audio recorder not active! " + e.getLocalizedMessage());
        }
    }

    private void start_recording_Polar() {


        File polarHRoutput_src = getPolarHRFile();

        // Prepare log file for Polar HR data

        try {
            polarHRout = new FileOutputStream(polarHRoutput_src.getAbsoluteFile().toString(), false);
            polarHRout.write(("time;HR;rrsMs;rr").getBytes());
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }

        // Enable functions to write data into file
        recording_Polar_enabled = true;

        /*api.startRecording(POLAR_ID,"TEST_APP_ID", PolarBleApi.RecordingInterval.INTERVAL_1S, PolarBleApi.SampleType.RR).subscribe(
                new Action() {
                    @Override
                    public void run() throws Exception {
                        Log.d(TAG,"Polar HR monitor recording started.");
                    }
                },
                new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.e(TAG,"Polar HR recording start failed: " + throwable.getLocalizedMessage());
                    }
                }
        );*/

        // Broadcast

        if(broadcastDisposable == null) {
            broadcastDisposable = api.startListenForPolarHrBroadcasts(null).subscribe(
                    new Consumer<PolarHrBroadcastData>() {
                        @Override
                        public void accept(PolarHrBroadcastData polarBroadcastData) throws Exception {
                            Log.d(TAG,"HR BROADCAST " +
                                    polarBroadcastData.polarDeviceInfo.deviceId + " HR: " +
                                    polarBroadcastData.hr + " batt: " +
                                    polarBroadcastData.batteryStatus);
                        }
                    },
                    new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) throws Exception {
                            Log.e(TAG,""+throwable.getLocalizedMessage());
                        }
                    },
                    new Action() {
                        @Override
                        public void run() throws Exception {
                            Log.d(TAG,"complete");
                        }
                    }
            );
        }else{
            broadcastDisposable.dispose();
            broadcastDisposable = null;
        }

    }

    private void stop_recording_eSense() {

        // Close sensor output stream
        sensorListener_eSense.close_output();              // Close the file stream for the log file
        Log.d(TAG, "Unregistering sensor listener...");
        manager_eSense.unregisterSensorListener();       // unregister the sensor listener
        Log.d(TAG, "Sensor listener unregistered");

    }

    private void stop_recording_audio() {

        // Close audio recording stream
        audioRecorder.stop();
        audioRecorder.release();
        audioRecorder = null;
    }

    private void stop_recording_Polar() {

        recording_Polar_enabled = false;

        api.stopRecording(POLAR_ID).subscribe(
                new Action() {
                    @Override
                    public void run() throws Exception {
                        Log.d(TAG,"Polar HR recording stopped.");
                    }
                },
                new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.e(TAG,"Polar HR recording stop failed: " + throwable.getLocalizedMessage());
                    }
                }
        );

    }

    private void disconnect_from_eSense() {

        manager_eSense.disconnect();         // disconnect from eSense device

    }

    private void disconnect_from_Polar() {

        try {
            api.disconnectFromDevice(POLAR_ID);
        } catch (PolarInvalidArgument polarInvalidArgument) {
            polarInvalidArgument.printStackTrace();
        }

    }
}