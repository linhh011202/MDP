package com.linh.mdp.activities;

import android.bluetooth.BluetoothDevice;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.linh.mdp.R;
import com.linh.mdp.bluetooth.BluetoothConnectionListener;
import com.linh.mdp.bluetooth.BluetoothHelper;
import com.linh.mdp.utils.BluetoothConstants;

public class StartActivity extends AppCompatActivity implements BluetoothConnectionListener {

    private static final String TAG = "StartActivity";

    // Shared Bluetooth instance
    private static BluetoothHelper sharedBluetoothHelper;
    private BluetoothHelper bluetoothHelper;

    // UI Components
    private MaterialButton startButton;

    // Current device name
    private String connectedDeviceName = "Unknown Device";

    // Reconnect grace period management (60s)
    private final Handler disconnectHandler = new Handler(Looper.getMainLooper());
    private Runnable delayedFinishRunnable;
    private boolean isWaitingForReconnect = false;
    private boolean manualDisconnectInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        // Get device name from intent
        connectedDeviceName = getIntent().getStringExtra(BluetoothConstants.EXTRA_DEVICE_NAME);
        if (connectedDeviceName == null) {
            connectedDeviceName = "Unknown Device";
        }

        initializeViews();
        setupBluetooth();
        setupBackPressedHandler();
    }

    private void initializeViews() {
        startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(v -> {
            if (bluetoothHelper != null && bluetoothHelper.isConnected()) {
                // Change button appearance
                changeButtonToWhiteStyle();

                // Send data
                bluetoothHelper.sendData("start");
                Toast.makeText(this, "Sent: start", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Sent message: start");

                // Revert button appearance after 1 second
                new Handler(Looper.getMainLooper()).postDelayed(this::revertButtonToOriginalStyle, 1000);
            } else {
                Toast.makeText(this, "Not connected to any device", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Cannot send message - not connected");
            }
        });
    }

    private void changeButtonToWhiteStyle() {
        // Change to white background with blue border and blue text
        startButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(android.R.color.white, null)));
        startButton.setTextColor(getResources().getColor(android.R.color.holo_blue_dark, null));
        startButton.setStrokeColor(ColorStateList.valueOf(getResources().getColor(android.R.color.holo_blue_dark, null)));
        startButton.setStrokeWidth(8);
    }

    private void revertButtonToOriginalStyle() {
        // Revert to original blue background with white text
        startButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(android.R.color.holo_blue_dark, null)));
        startButton.setTextColor(getResources().getColor(android.R.color.white, null));
        startButton.setStrokeWidth(0);
    }

    private void setupBluetooth() {
        // Use the shared BluetoothHelper instance from BluetoothActivity
        if (sharedBluetoothHelper != null) {
            bluetoothHelper = sharedBluetoothHelper;
            bluetoothHelper.setConnectionListener(this);
            Log.d(TAG, "Using shared BluetoothHelper instance");
        } else {
            // Fallback: create new instance (shouldn't happen in normal flow)
            bluetoothHelper = new BluetoothHelper(this);
            bluetoothHelper.setConnectionListener(this);
            Log.w(TAG, "Created new BluetoothHelper instance as fallback");
        }

        // Check if we still have an active connection
        if (!bluetoothHelper.isConnected()) {
            Toast.makeText(this, "Connection lost. Returning to scan.", Toast.LENGTH_LONG).show();
            finish();
        } else {
            Log.d(TAG, "Bluetooth connection is active");
        }
    }

    private void setupBackPressedHandler() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                disconnect();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void disconnect() {
        if (bluetoothHelper != null) {
            manualDisconnectInProgress = true;
            bluetoothHelper.disconnect();
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    // BluetoothConnectionListener implementation
    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        // Cancel any pending delayed finish if we were waiting for reconnection
        if (isWaitingForReconnect) {
            isWaitingForReconnect = false;
            if (delayedFinishRunnable != null) {
                disconnectHandler.removeCallbacks(delayedFinishRunnable);
                delayedFinishRunnable = null;
            }
            Toast.makeText(this, "Reconnected", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDeviceDisconnected() {
        // If this is a manual disconnect flow, we've already finished
        if (manualDisconnectInProgress) return;

        runOnUiThread(() -> {
            // Start a 60-second grace period to allow auto-reconnect
            if (!isWaitingForReconnect) {
                isWaitingForReconnect = true;
                Toast.makeText(this, "Device disconnected. Waiting 1 minute for reconnection...", Toast.LENGTH_LONG).show();
                delayedFinishRunnable = () -> {
                    // If still disconnected after 60s, leave this screen
                    if (bluetoothHelper == null || !bluetoothHelper.isConnected()) {
                        Toast.makeText(this, "No reconnection. Returning to scan.", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        // Reconnected within grace period; stay on screen
                        isWaitingForReconnect = false;
                    }
                };
                disconnectHandler.postDelayed(delayedFinishRunnable, 60_000);
            }
        });
    }

    @Override
    public void onDataReceived(String data) {
        runOnUiThread(() -> {
            // Handle received data if needed
            Log.d(TAG, "Data received: " + data);
        });
    }

    @Override
    public void onConnectionFailed(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Connection failed: " + error, Toast.LENGTH_LONG).show();
            finish();
        });
    }

    @Override
    public void onDeviceDiscovered(BluetoothDevice device) {
        // Not used in this activity
    }

    @Override
    public void onWaitingForConnection(BluetoothDevice device) {
        // Not used in this activity
    }

    @Override
    public void onConnectionTimeout() {
        // Not used in this activity
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up any pending handlers
        if (delayedFinishRunnable != null) {
            disconnectHandler.removeCallbacks(delayedFinishRunnable);
        }
        // Note: Do NOT call bluetoothHelper.cleanup() here
        // The BluetoothHelper is shared and may still be in use
        Log.d(TAG, "Activity destroyed");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bluetoothHelper != null) {
            bluetoothHelper.setConnectionListener(this);
            // Check connection status
            if (!bluetoothHelper.isConnected() && !isWaitingForReconnect) {
                Toast.makeText(this, "Connection lost", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    public static void setBluetoothHelper(BluetoothHelper helper) {
        sharedBluetoothHelper = helper;
    }
}
