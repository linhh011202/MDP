package com.linh.mdp.activities;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.linh.mdp.R;
import com.linh.mdp.bluetooth.BluetoothConnectionListener;
import com.linh.mdp.bluetooth.BluetoothHelper;
import com.linh.mdp.utils.BluetoothConstants;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataCommunicationActivity extends AppCompatActivity implements BluetoothConnectionListener {

    private static final String TAG = "DataCommunicationActivity";

    private static BluetoothHelper sharedBluetoothHelper; // Shared instance
    private BluetoothHelper bluetoothHelper;
    private TextView connectionStatusText;
    private TextView receivedDataText;
    private TextView messageCountText;
    private EditText messageInput;
    private Button sendButton;
    private Button disconnectButton;
    private StringBuilder receivedDataBuffer;
    private int messageCount = 0;
    private String connectedDeviceName = "Unknown Device";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_communication);

        // Get device name from intent
        connectedDeviceName = getIntent().getStringExtra(BluetoothConstants.EXTRA_DEVICE_NAME);
        if (connectedDeviceName == null) {
            connectedDeviceName = "Unknown Device";
        }

        initializeViews();
        setupBluetooth();
        setupBackPressedHandler();
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

    private void initializeViews() {
        connectionStatusText = findViewById(R.id.connectionStatusText);
        receivedDataText = findViewById(R.id.receivedDataText);
        messageCountText = findViewById(R.id.messageCountText);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        disconnectButton = findViewById(R.id.disconnectButton);

        Button clearButton = findViewById(R.id.clearButton);
        Button quickSend1 = findViewById(R.id.quickSend1);
        Button quickSend2 = findViewById(R.id.quickSend2);
        Button quickSend3 = findViewById(R.id.quickSend3);

        receivedDataBuffer = new StringBuilder();

        // Set initial status
        connectionStatusText.setText("Connected to " + connectedDeviceName);
        updateMessageCount();

        // Set up click listeners
        sendButton.setOnClickListener(v -> sendMessage());
        disconnectButton.setOnClickListener(v -> disconnect());
        clearButton.setOnClickListener(v -> clearReceivedData());

        quickSend1.setOnClickListener(v -> sendQuickMessage("Hello"));
        quickSend2.setOnClickListener(v -> sendQuickMessage("OK"));
        quickSend3.setOnClickListener(v -> sendQuickMessage("Test"));
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

    private void sendMessage() {
        String message = messageInput.getText().toString().trim();
        if (!message.isEmpty()) {
            if (bluetoothHelper.isConnected()) {
                bluetoothHelper.sendData(message);
                messageInput.setText("");

                // Add sent message to display with timestamp
                String timestamp = getCurrentTimestamp();
                String formattedMessage = "[" + timestamp + "] SENT: " + message + "\n";
                receivedDataBuffer.append(formattedMessage);
                receivedDataText.setText(receivedDataBuffer.toString());

                Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Connection lost!", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, "Enter a message to send", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendQuickMessage(String message) {
        if (bluetoothHelper.isConnected()) {
            bluetoothHelper.sendData(message);

            // Add sent message to display with timestamp
            String timestamp = getCurrentTimestamp();
            String formattedMessage = "[" + timestamp + "] SENT: " + message + "\n";
            receivedDataBuffer.append(formattedMessage);
            receivedDataText.setText(receivedDataBuffer.toString());

            Toast.makeText(this, "Sent: " + message, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Connection lost!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void disconnect() {
        if (bluetoothHelper != null) {
            bluetoothHelper.disconnect();
            // Clear the shared instance to ensure clean reconnection
            sharedBluetoothHelper = null;
        }
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void clearReceivedData() {
        receivedDataBuffer.setLength(0);
        receivedDataText.setText("No messages received yet...");
        messageCount = 0;
        updateMessageCount();
    }

    private void updateMessageCount() {
        messageCountText.setText("Messages: " + messageCount);
    }

    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

    // BluetoothConnectionListener implementation
    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        // Already connected when this activity starts
    }

    @Override
    public void onDeviceDisconnected() {
        runOnUiThread(() -> {
            Toast.makeText(this, "Device disconnected", Toast.LENGTH_LONG).show();
            finish();
        });
    }

    @Override
    public void onDataReceived(String data) {
        runOnUiThread(() -> {
            String timestamp = getCurrentTimestamp();
            String formattedMessage = "[" + timestamp + "] RECEIVED: " + data + "\n";
            receivedDataBuffer.append(formattedMessage);
            receivedDataText.setText(receivedDataBuffer.toString());

            messageCount++;
            updateMessageCount();

            Toast.makeText(this, "Data received", Toast.LENGTH_SHORT).show();

            // Auto-scroll to bottom
            receivedDataText.post(() -> {
                int scrollY = receivedDataText.getLayout().getLineTop(receivedDataText.getLineCount()) - receivedDataText.getHeight();
                if (scrollY > 0) {
                    receivedDataText.scrollTo(0, scrollY);
                }
            });
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
        // Not used in this activity - only used in BluetoothActivity
    }

    @Override
    public void onConnectionTimeout() {
        // Not used in this activity - only used in BluetoothActivity
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothHelper != null) {
            bluetoothHelper.cleanup();
            // Clear the shared instance when activity is destroyed
            sharedBluetoothHelper = null;
        }
    }

    public static void setBluetoothHelper(BluetoothHelper helper) {
        sharedBluetoothHelper = helper;
    }
}
