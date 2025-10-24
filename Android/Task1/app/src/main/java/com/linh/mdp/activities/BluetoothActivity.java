package com.linh.mdp.activities;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.linh.mdp.R;
import com.linh.mdp.bluetooth.BluetoothConnectionListener;
import com.linh.mdp.bluetooth.BluetoothHelper;
import com.linh.mdp.utils.BluetoothConstants;
import com.linh.mdp.utils.PermissionUtils;

import java.util.ArrayList;
import java.util.List;

public class BluetoothActivity extends AppCompatActivity implements BluetoothConnectionListener {

    private static final String TAG = "BluetoothActivity";

    private BluetoothHelper bluetoothHelper;
    private TextView statusText;
    private Button scanButton;
    private ArrayAdapter<String> devicesAdapter;
    private List<BluetoothDevice> devices;

    private enum RoleMode { CLIENT, SERVER }
    private RoleMode currentMode = RoleMode.CLIENT;
    private Button modeToggleButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        initializeViews();
        setupBluetooth();
    }

    private void initializeViews() {
        statusText = findViewById(R.id.statusText);
        scanButton = findViewById(R.id.scanButton);
        ListView devicesList = findViewById(R.id.devicesList);
        modeToggleButton = findViewById(R.id.modeToggleButton);

        devices = new ArrayList<>();
        devicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        devicesList.setAdapter(devicesAdapter);

        scanButton.setOnClickListener(v -> scanForDevices());
        modeToggleButton.setOnClickListener(v -> toggleMode());

        devicesList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < devices.size()) {
                BluetoothDevice device = devices.get(position);
                if (PermissionUtils.checkAndRequestBluetoothPermissions(this)) {
                    Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (currentMode == RoleMode.CLIENT) {
                    startClientConnection(device);
                } else {
                    startServerWait(device);
                }
            }
        });

        devicesList.setOnItemLongClickListener(null);
    }

    private void toggleMode() {
        if (bluetoothHelper != null && bluetoothHelper.isConnected()) {
            Toast.makeText(this, "Disconnect first to change mode", Toast.LENGTH_SHORT).show();
            return;
        }
        currentMode = (currentMode == RoleMode.CLIENT) ? RoleMode.SERVER : RoleMode.CLIENT;
        updateModeUI();
    }

    private void updateModeUI() {
        if (modeToggleButton == null) return;
        if (currentMode == RoleMode.CLIENT) {
            modeToggleButton.setText("Mode: Client");
            if (!bluetoothHelper.isConnected() && !bluetoothHelper.isWaitingForConnection()) {
                statusText.setText("Client mode - tap device to connect");
            }
        } else {
            modeToggleButton.setText("Mode: Server");
            if (!bluetoothHelper.isConnected() && !bluetoothHelper.isWaitingForConnection()) {
                statusText.setText("Server mode - tap device to wait for incoming connection");
            }
        }
    }

    private void setupBluetooth() {
        bluetoothHelper = new BluetoothHelper(this);
        bluetoothHelper.setConnectionListener(this);

        if (!bluetoothHelper.isBluetoothSupported()) {
            statusText.setText("Bluetooth not supported on this device");
            scanButton.setEnabled(false);
            return;
        }

        updateUI();
        updateModeUI();
    }

    private void updateUI() {
        boolean isEnabled = bluetoothHelper.isBluetoothEnabled();
        boolean isConnected = bluetoothHelper.isConnected();
        boolean isWaiting = bluetoothHelper.isWaitingForConnection();

        scanButton.setEnabled(!isConnected && !isWaiting);

        if (isConnected) {
            statusText.setText("Connected to Bluetooth device - Ready to send/receive data");
        } else if (isWaiting) {
            statusText.setText("Waiting for laptop to connect...");
        } else if (isEnabled) {
            statusText.setText("Bluetooth enabled - Ready to scan");
        } else {
            statusText.setText("Bluetooth disabled - Please enable Bluetooth manually");
        }
    }

    private void scanForDevices() {
        Log.d(TAG, "scanForDevices() called");

        if (PermissionUtils.checkAndRequestBluetoothPermissions(this)) {
            statusText.setText("Please grant Bluetooth permissions");
            return;
        }

        if (!bluetoothHelper.isBluetoothEnabled()) {
            statusText.setText("Please enable Bluetooth manually to scan for devices");
            return;
        }

        statusText.setText("Scanning for devices...");
        devices.clear();
        devicesAdapter.clear();
        devicesAdapter.notifyDataSetChanged();

        List<BluetoothDevice> pairedDevices = bluetoothHelper.getPairedDevices();
        Log.d(TAG, "Found " + pairedDevices.size() + " paired devices");

        if (!pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                devices.add(device);
                String deviceName = getDeviceName(device);
                String deviceAddress = getDeviceAddress(device);
                devicesAdapter.add(deviceName + " (Paired)\n" + deviceAddress);
                Log.d(TAG, "Added paired device: " + deviceName + " - " + deviceAddress);
            }
            devicesAdapter.notifyDataSetChanged();
        } else {
            Log.w(TAG, "No paired devices found!");
        }

        statusText.setText("Found " + pairedDevices.size() + " paired devices. Searching for more...");

        bluetoothHelper.startDiscovery();

        new android.os.Handler().postDelayed(() -> {
            bluetoothHelper.stopDiscovery();
            int totalDevices = devices.size();
            statusText.setText("Scan completed. Found " + totalDevices + " devices total");
            Log.d(TAG, "Discovery completed. Total devices found: " + totalDevices);

            if (totalDevices == 0) {
                Log.w(TAG, "No devices found at all - this indicates a permission or Bluetooth issue");
                statusText.setText("No devices found. Check Bluetooth permissions and ensure devices are discoverable.");
            }
        }, BluetoothConstants.DISCOVERY_TIMEOUT_MS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == BluetoothConstants.REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Log.d(TAG, "All permissions granted by user");
                Toast.makeText(this, "Permissions granted. You can now scan for devices.", Toast.LENGTH_SHORT).show();
                updateUI();
            } else {
                Log.w(TAG, "Some permissions denied by user");
                Toast.makeText(this, "Bluetooth permissions are required for device discovery", Toast.LENGTH_LONG).show();
                statusText.setText("Permissions required - Please grant Bluetooth permissions");
            }
        }
    }

    private void startClientConnection(BluetoothDevice device) {
        if (bluetoothHelper != null) bluetoothHelper.cleanup();
        bluetoothHelper = new BluetoothHelper(this);
        bluetoothHelper.setConnectionListener(this);
        String deviceName = getDeviceName(device);
        statusText.setText("Connecting to " + deviceName + " as client...");
        bluetoothHelper.connectToDevice(device);
    }

    private void startServerWait(BluetoothDevice device) {
        if (bluetoothHelper != null) bluetoothHelper.cleanup();
        bluetoothHelper = new BluetoothHelper(this);
        bluetoothHelper.setConnectionListener(this);
        String deviceName = getDeviceName(device);
        statusText.setText("Waiting for connection from " + deviceName + " (server mode)...");
        bluetoothHelper.waitForConnectionFromDevice(device);
    }

    private String getDeviceName(BluetoothDevice device) {
        if (!PermissionUtils.checkBluetoothPermissions(this)) {
            return "Unknown Device";
        }

        try {
            String name = device.getName();
            return name != null ? name : "Unknown Device";
        } catch (SecurityException e) {
            return "Unknown Device";
        }
    }

    private String getDeviceAddress(BluetoothDevice device) {
        try {
            return device.getAddress();
        } catch (SecurityException e) {
            return "Unknown Address";
        }
    }

    // BluetoothConnectionListener implementation
    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        runOnUiThread(() -> {
            String deviceName = getDeviceName(device);
            statusText.setText("Connected to " + deviceName);
            Toast.makeText(this, "Connected to " + deviceName, Toast.LENGTH_SHORT).show();

            DataCommunicationActivity.setBluetoothHelper(bluetoothHelper);

            Intent intent = new Intent(BluetoothActivity.this, DataCommunicationActivity.class);
            intent.putExtra(BluetoothConstants.EXTRA_DEVICE_NAME, deviceName);
            startActivity(intent);
        });
    }

    @Override
    public void onWaitingForConnection(BluetoothDevice device) {
        runOnUiThread(() -> {
            String deviceName = getDeviceName(device);
            if (currentMode == RoleMode.SERVER) {
                statusText.setText("Server mode: waiting for " + deviceName + "... (timeout 60s)");
            } else {
                statusText.setText("(Unexpected) Waiting for " + deviceName + "..." );
            }
            Toast.makeText(this, "Waiting for " + deviceName, Toast.LENGTH_LONG).show();
            updateUI();
        });
    }

    @Override
    public void onConnectionTimeout() {
        runOnUiThread(() -> {
            statusText.setText("Connection timeout - No incoming connection received");
            Toast.makeText(this, "Connection timeout. Try again.", Toast.LENGTH_LONG).show();
            updateUI();
        });
    }

    @Override
    public void onDeviceDisconnected() {
        runOnUiThread(() -> {
            statusText.setText("Device disconnected");
            updateUI();
        });
    }

    @Override
    public void onDataReceived(String data) {
        // Data receiving is now handled in DataCommunicationActivity
    }

    @Override
    public void onConnectionFailed(String error) {
        runOnUiThread(() -> {
            statusText.setText("Connection failed: " + error);
            Toast.makeText(this, "Connection failed: " + error, Toast.LENGTH_LONG).show();
            updateUI();
        });
    }

    @Override
    public void onDeviceDiscovered(BluetoothDevice device) {
        runOnUiThread(() -> {
            if (!devices.contains(device)) {
                devices.add(device);
                String deviceName = getDeviceName(device);
                String deviceAddress = getDeviceAddress(device);
                devicesAdapter.add(deviceName + "\n" + deviceAddress);
                devicesAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothHelper != null) {
            bluetoothHelper.cleanup();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bluetoothHelper != null) {
            bluetoothHelper.setConnectionListener(this);
            updateUI();
            updateModeUI();
        }
    }
}
