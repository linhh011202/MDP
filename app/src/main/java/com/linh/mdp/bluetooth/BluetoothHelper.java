package com.linh.mdp.bluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.linh.mdp.utils.BluetoothConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BluetoothHelper {
    private static final String TAG = "BluetoothHelper";

    private final BluetoothAdapter bluetoothAdapter;
    private final Activity activity;
    private BluetoothSocket bluetoothSocket;
    private android.bluetooth.BluetoothServerSocket bluetoothServerSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private final List<BluetoothDevice> discoveredDevices;
    private BluetoothConnectionListener connectionListener;
    private boolean isReceiverRegistered = false;
    private boolean isListeningForConnection = false;
    private Thread serverThread;

    // Auto-reconnection fields
    private BluetoothDevice lastConnectedDevice;
    private final boolean autoReconnectEnabled = true;
    private static final int RECONNECTION_DELAY_MS = 60000; // 60 seconds
    private static final int MAX_RECONNECTION_ATTEMPTS = 5;
    private int reconnectionAttempts = 0;
    private final Handler reconnectionHandler = new Handler(Looper.getMainLooper());
    // Suppress onWaitingForConnection UI callback once when we need to delay UI navigation
    private boolean suppressNextWaitingCallback = false;
    // Delayed disconnection notifier to allow a 60s grace period before notifying UI
    private Runnable delayedDisconnectionNotifier;

    // Continuous reconnection (new)
    private final boolean continuousReconnectEnabled = true; // enable continuous attempts by default as requested
    private final long continuousReconnectIntervalMs = 5000;  // attempt every 5 seconds
    private final Runnable continuousReconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (!continuousReconnectEnabled) return;
            if (lastConnectedDevice != null && !isConnected()) {
                Log.d(TAG, "[ContinuousReconnect] Attempting reconnect to " + lastConnectedDevice.getAddress());
                // Use client-style active connect attempts
                connectToDevice(lastConnectedDevice);
                // schedule next attempt only if still not connected after interval
                reconnectionHandler.postDelayed(this, continuousReconnectIntervalMs);
            } else {
                Log.d(TAG, "[ContinuousReconnect] Stopping loop (connected or no device)");
            }
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Broadcast received: " + action);

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    Log.d(TAG, "Device found: " + device.getAddress());

                    boolean alreadyExists = false;
                    for (BluetoothDevice existingDevice : discoveredDevices) {
                        if (existingDevice.getAddress().equals(device.getAddress())) {
                            alreadyExists = true;
                            break;
                        }
                    }

                    if (!alreadyExists) {
                        discoveredDevices.add(device);
                        Log.d(TAG, "Added new device to list. Total devices: " + discoveredDevices.size());

                        if (connectionListener != null) {
                            connectionListener.onDeviceDiscovered(device);
                        }
                    } else {
                        Log.d(TAG, "Device already exists in list");
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Log.d(TAG, "Discovery started");
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "Discovery finished. Total devices found: " + discoveredDevices.size());
            }
        }
    };

    public BluetoothHelper(Activity activity) {
        this.activity = activity;

        BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();

        this.discoveredDevices = new ArrayList<>();
    }

    public void setConnectionListener(BluetoothConnectionListener listener) {
        this.connectionListener = listener;
    }

    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public boolean checkPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        } else {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH);
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN);
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (permissionsNeeded.isEmpty()) {
            Log.d(TAG, "All required permissions granted");
            return true;
        } else {
            Log.w(TAG, "Missing permissions: " + permissionsNeeded);
            return false;
        }
    }

    public List<BluetoothDevice> getPairedDevices() {
        List<BluetoothDevice> pairedDevices = new ArrayList<>();
        Log.d(TAG, "Getting paired devices...");

        if (bluetoothAdapter != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
                        if (bondedDevices != null) {
                            pairedDevices.addAll(bondedDevices);
                            Log.d(TAG, "Found " + pairedDevices.size() + " paired devices (Android 12+)");
                        }
                    } else {
                        Log.w(TAG, "BLUETOOTH_CONNECT permission not granted");
                    }
                } else {
                    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
                        if (bondedDevices != null) {
                            pairedDevices.addAll(bondedDevices);
                            Log.d(TAG, "Found " + pairedDevices.size() + " paired devices (Android 11-)");
                        }
                    } else {
                        Log.w(TAG, "BLUETOOTH permission not granted");
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied when getting paired devices", e);
            }
        } else {
            Log.e(TAG, "Bluetooth adapter is null");
        }

        return pairedDevices;
    }

    public void startDiscovery() {
        Log.d(TAG, "startDiscovery() called");

        if (!checkPermissions()) {
            Log.e(TAG, "Missing permissions for discovery");
            return;
        }

        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled");
            return;
        }

        try {
            discoveredDevices.clear();
            Log.d(TAG, "Cleared discovered devices list");

            if (!isReceiverRegistered) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(BluetoothDevice.ACTION_FOUND);
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                activity.registerReceiver(receiver, filter);
                isReceiverRegistered = true;
                Log.d(TAG, "Broadcast receiver registered");
            }

            boolean canStartDiscovery = false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    canStartDiscovery = true;
                    Log.d(TAG, "BLUETOOTH_SCAN permission granted (Android 12+)");
                } else {
                    Log.e(TAG, "BLUETOOTH_SCAN permission not granted (Android 12+)");
                }
            } else {
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    canStartDiscovery = true;
                    Log.d(TAG, "BLUETOOTH_ADMIN and ACCESS_FINE_LOCATION permissions granted (Android 11-)");
                } else {
                    Log.e(TAG, "Missing BLUETOOTH_ADMIN or ACCESS_FINE_LOCATION permissions (Android 11-)");
                }
            }

            if (canStartDiscovery) {
                if (bluetoothAdapter.isDiscovering()) {
                    Log.d(TAG, "Canceling previous discovery");
                    bluetoothAdapter.cancelDiscovery();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                Log.d(TAG, "Starting Bluetooth discovery");
                boolean discoveryStarted = bluetoothAdapter.startDiscovery();
                Log.d(TAG, "Discovery started result: " + discoveryStarted);

                if (!discoveryStarted) {
                    Log.e(TAG, "Failed to start discovery - bluetoothAdapter.startDiscovery() returned false");
                }
            } else {
                Log.e(TAG, "Cannot start discovery due to missing permissions");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when starting discovery", e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception when starting discovery", e);
        }
    }

    public void stopDiscovery() {
        try {
            if (bluetoothAdapter != null && ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
            }
            if (isReceiverRegistered) {
                activity.unregisterReceiver(receiver);
                isReceiverRegistered = false;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied when stopping discovery", e);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver was not registered", e);
        }
    }

    public void connectToDevice(BluetoothDevice device) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Starting connection to device: " + device.getAddress());

                // Optimized: Reduce discovery stopping delay from 1000ms to 100ms
                stopDiscovery();
                Thread.sleep(100);

                boolean hasPermission = checkBluetoothConnectPermission();

                if (hasPermission) {
                    bluetoothSocket = attemptConnection(device);

                    inputStream = bluetoothSocket.getInputStream();
                    outputStream = bluetoothSocket.getOutputStream();

                    Log.i(TAG, "Bluetooth connection established successfully with streams ready");

                    // Store the connected device and stop any reconnection attempts
                    lastConnectedDevice = device;
                    stopAutoReconnect();
                    reconnectionAttempts = 0;
                    // Cancel any pending delayed disconnect notifier since we've reconnected
                    cancelDelayedDisconnectionNotifier();

                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (connectionListener != null) {
                            connectionListener.onDeviceConnected(device);
                        }
                    });

                    startListening();
                } else {
                    Log.e(TAG, "Missing BLUETOOTH_CONNECT or BLUETOOTH permission");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (connectionListener != null) {
                            connectionListener.onConnectionFailed("Missing Bluetooth permissions");
                        }
                    });
                }

            } catch (IOException e) {
                Log.e(TAG, "Connection failed after all attempts", e);

                if (continuousReconnectEnabled) {
                    // Continuous mode: ensure loop continues (it may already be scheduled by handleConnectionLost)
                    if (lastConnectedDevice == null) lastConnectedDevice = device;
                    // Avoid stacking multiple runnables: remove old then (re)post
                    reconnectionHandler.removeCallbacks(continuousReconnectRunnable);
                    reconnectionHandler.postDelayed(continuousReconnectRunnable, continuousReconnectIntervalMs);
                } else if (autoReconnectEnabled && lastConnectedDevice != null &&
                    device.getAddress().equals(lastConnectedDevice.getAddress()) &&
                    reconnectionAttempts < MAX_RECONNECTION_ATTEMPTS) {

                    reconnectionAttempts++;
                    Log.d(TAG, "Scheduling reconnection attempt " + reconnectionAttempts + "/" + MAX_RECONNECTION_ATTEMPTS);

                    reconnectionHandler.postDelayed(() -> {
                        if (!isConnected()) {
                            connectToDevice(device);
                        }
                    }, RECONNECTION_DELAY_MS);
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (connectionListener != null) {
                            connectionListener.onConnectionFailed("Failed to connect: " + e.getMessage());
                        }
                    });
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied when connecting", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onConnectionFailed("Permission denied: " + e.getMessage());
                    }
                });
            } catch (InterruptedException e) {
                Log.e(TAG, "Connection interrupted", e);
                Thread.currentThread().interrupt();
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onConnectionFailed("Connection interrupted: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    private boolean checkBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private BluetoothSocket attemptConnection(BluetoothDevice device) throws IOException {
        Log.d(TAG, "Attempting optimized connection methods");
        BluetoothSocket socket = null;
        IOException lastException;

        // First try: Standard UUID connection (fastest method)
        try {
            Log.d(TAG, "Trying standard UUID connection");
            socket = device.createRfcommSocketToServiceRecord(BluetoothConstants.MY_UUID);
            // Set socket timeout for faster failure detection
            socket.connect();
            Log.i(TAG, "Standard UUID connection successful");
            return socket;
        } catch (Exception e) {
            Log.w(TAG, "Standard UUID connection failed: " + e.getMessage());
            closeSocketSafely(socket);
            socket = null;
        }

        // Second try: Insecure connection (for compatibility)
        try {
            Log.d(TAG, "Trying insecure UUID connection");
            socket = device.createInsecureRfcommSocketToServiceRecord(BluetoothConstants.MY_UUID);
            socket.connect();
            Log.i(TAG, "Insecure UUID connection successful");
            return socket;
        } catch (Exception e) {
            lastException = e instanceof IOException ? (IOException) e : new IOException("Insecure UUID error", e);
            Log.w(TAG, "Insecure UUID connection failed: " + e.getMessage());
            closeSocketSafely(socket);
            socket = null;
        }

        // Third try: Reflection-based connection with optimized channel order
        // Start with most common channels first
        int[] channels = {1, 2, 3, 4, 5};

        for (int channel : channels) {
            try {
                Log.d(TAG, "Trying reflection-based connection with channel: " + channel);
                Method method = device.getClass().getMethod("createRfcommSocket", int.class);
                socket = (BluetoothSocket) method.invoke(device, channel);
                socket.connect();
                Log.i(TAG, "Reflection-based connection successful with channel: " + channel);
                return socket;
            } catch (Exception e) {
                lastException = e instanceof IOException ? (IOException) e : new IOException("Reflection error", e);
                Log.w(TAG, "Reflection connection failed with channel " + channel + ": " + e.getMessage());
                closeSocketSafely(socket);
                socket = null;
            }
        }

        // Fourth try: Insecure reflection connections as last resort
        for (int channel : channels) {
            try {
                Log.d(TAG, "Trying insecure reflection-based connection with channel: " + channel);
                Method method = device.getClass().getMethod("createInsecureRfcommSocket", int.class);
                socket = (BluetoothSocket) method.invoke(device, channel);
                socket.connect();
                Log.i(TAG, "Insecure reflection-based connection successful with channel: " + channel);
                return socket;
            } catch (Exception e) {
                lastException = e instanceof IOException ? (IOException) e : new IOException("Reflection error", e);
                Log.w(TAG, "Insecure reflection connection failed with channel " + channel + ": " + e.getMessage());
                closeSocketSafely(socket);
                socket = null;
            }
        }

        throw lastException;
    }

    private void closeSocketSafely(BluetoothSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing socket", e);
            }
        }
    }

    public void waitForConnectionFromDevice(BluetoothDevice device) {
        Log.d(TAG, "Setting up to wait for connection from device: " + device.getAddress());

        stopDiscovery();

        if (connectionListener != null && !suppressNextWaitingCallback) {
            connectionListener.onWaitingForConnection(device);
        }
        // Reset suppression after one use
        suppressNextWaitingCallback = false;

        startListeningForConnection(device);
    }

    private void startListeningForConnection(BluetoothDevice targetDevice) {
        if (isListeningForConnection) {
            Log.d(TAG, "Already listening for connection");
            return;
        }

        boolean hasPermission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            hasPermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
        }

        if (!hasPermission) {
            Log.e(TAG, "Missing permissions for listening");
            new Handler(Looper.getMainLooper()).post(() -> {
                if (connectionListener != null) {
                    connectionListener.onConnectionFailed("Missing Bluetooth permissions");
                }
            });
            return;
        }

        serverThread = new Thread(() -> {
            try {
                Log.d(TAG, "Creating Bluetooth server socket");
                isListeningForConnection = true;

                bluetoothServerSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                    "BluetoothChatService", BluetoothConstants.MY_UUID);

                Log.d(TAG, "Server socket created, waiting for connection...");

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isListeningForConnection && bluetoothServerSocket != null) {
                        Log.d(TAG, "Connection timeout reached");
                        stopListening();
                        if (connectionListener != null) {
                            connectionListener.onConnectionTimeout();
                        }
                        // Auto-restart listening if enabled
                        if (autoReconnectEnabled && targetDevice != null) {
                            Log.d(TAG, "Auto-reconnect waiting enabled, restarting listen");
                            reconnectionHandler.postDelayed(() -> startListeningForConnection(targetDevice), RECONNECTION_DELAY_MS);
                        }
                    }
                }, BluetoothConstants.CONNECTION_TIMEOUT_MS);

                bluetoothSocket = bluetoothServerSocket.accept();

                if (bluetoothSocket != null) {
                    Log.i(TAG, "Incoming connection accepted from: " + bluetoothSocket.getRemoteDevice().getAddress());

                    String connectedDeviceAddress = bluetoothSocket.getRemoteDevice().getAddress();
                    String targetDeviceAddress = targetDevice.getAddress();

                    if (connectedDeviceAddress.equals(targetDeviceAddress)) {
                        Log.i(TAG, "Connection from expected device established");

                        stopListening();

                        inputStream = bluetoothSocket.getInputStream();
                        outputStream = bluetoothSocket.getOutputStream();

                        // Record the connected device and cancel any pending delayed disconnect notifier
                        lastConnectedDevice = targetDevice;
                        cancelDelayedDisconnectionNotifier();
                        stopAutoReconnect();
                        reconnectionAttempts = 0;

                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (connectionListener != null) {
                                connectionListener.onDeviceConnected(targetDevice);
                            }
                        });

                        startListening();

                    } else {
                        Log.w(TAG, "Connection from unexpected device: " + connectedDeviceAddress +
                              ", expected: " + targetDeviceAddress);
                        try {
                            bluetoothSocket.close();
                            bluetoothSocket = null;
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing unexpected connection", e);
                        }
                        startListeningForConnection(targetDevice);
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "Error in server socket", e);
                if (isListeningForConnection) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (connectionListener != null) {
                            connectionListener.onConnectionFailed("Failed to listen for connection: " + e.getMessage());
                        }
                    });
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied when creating server socket", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onConnectionFailed("Permission denied: " + e.getMessage());
                    }
                });
            } finally {
                isListeningForConnection = false;
                if (bluetoothServerSocket != null) {
                    try {
                        bluetoothServerSocket.close();
                        bluetoothServerSocket = null;
                    } catch (IOException e) {
                        Log.w(TAG, "Error closing server socket", e);
                    }
                }
            }
        });

        serverThread.start();
    }

    public void stopListening() {
        Log.d(TAG, "Stopping listening for connections");
        isListeningForConnection = false;

        if (bluetoothServerSocket != null) {
            try {
                bluetoothServerSocket.close();
                bluetoothServerSocket = null;
                Log.d(TAG, "Server socket closed");
            } catch (IOException e) {
                Log.w(TAG, "Error closing server socket", e);
            }
        }

        if (serverThread != null) {
            if (serverThread.isAlive()) {
                serverThread.interrupt();
                try {
                    serverThread.join(2000);
                    Log.d(TAG, "Server thread terminated");
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted while waiting for server thread to finish");
                    Thread.currentThread().interrupt();
                }
            }
            serverThread = null;
        }
    }

    public boolean isWaitingForConnection() {
        return isListeningForConnection;
    }

    private void startListening() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;

            while (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                try {
                    bytes = inputStream.read(buffer);
                    if (bytes == -1) {
                        Log.i(TAG, "Input stream closed by remote device");
                        handleConnectionLost("Remote closed stream");
                        break;
                    }
                    String receivedData = new String(buffer, 0, bytes);

                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (connectionListener != null) {
                            connectionListener.onDataReceived(receivedData);
                        }
                    });
                } catch (IOException e) {
                    Log.e(TAG, "Error reading data", e);
                    handleConnectionLost(e.getMessage());
                    break;
                }
            }
        }).start();
    }

    private synchronized void handleConnectionLost(String reason) {
        Log.w(TAG, "Connection lost: " + reason);

        // Close streams and socket safely
        try {
            if (inputStream != null) {
                try { inputStream.close(); } catch (IOException ignore) {}
            }
            if (outputStream != null) {
                try { outputStream.close(); } catch (IOException ignore) {}
            }
            if (bluetoothSocket != null) {
                try { bluetoothSocket.close(); } catch (IOException ignore) {}
            }
        } finally {
            inputStream = null;
            outputStream = null;
            bluetoothSocket = null;
        }

        if (autoReconnectEnabled && lastConnectedDevice != null) {
            // Start listening for the same device to reconnect and suppress immediate waiting callback
            suppressNextWaitingCallback = true;
            waitForConnectionFromDevice(lastConnectedDevice); // passive listen
        }

        // Start/refresh continuous active reconnect loop if enabled
        if (continuousReconnectEnabled && lastConnectedDevice != null) {
            reconnectionHandler.removeCallbacks(continuousReconnectRunnable);
            reconnectionHandler.postDelayed(continuousReconnectRunnable, continuousReconnectIntervalMs);
        }

        // Notify UI immediately; Activity will handle the 60s grace period
        new Handler(Looper.getMainLooper()).post(() -> {
            if (connectionListener != null) {
                connectionListener.onDeviceDisconnected();
            }
        });
    }

    private synchronized void cancelDelayedDisconnectionNotifier() {
        if (delayedDisconnectionNotifier != null) {
            reconnectionHandler.removeCallbacks(delayedDisconnectionNotifier);
            delayedDisconnectionNotifier = null;
        }
    }

    public void sendData(String data) {
        if (outputStream != null) {
            new Thread(() -> {
                try {
                    outputStream.write(data.getBytes());
                    outputStream.flush();
                } catch (IOException e) {
                    Log.e(TAG, "Error sending data", e);
                }
            }).start();
        }
    }

    public void disconnect() {
        Log.d(TAG, "disconnect() called");
        cancelContinuousReconnect();
        try {
            stopDiscovery();
            stopListening();

            if (inputStream != null) {
                try {
                    inputStream.close();
                    Log.d(TAG, "Input stream closed successfully");
                } catch (IOException e) {
                    Log.w(TAG, "Error closing input stream", e);
                } finally {
                    inputStream = null;
                }
            }

            if (outputStream != null) {
                try {
                    outputStream.close();
                    Log.d(TAG, "Output stream closed successfully");
                } catch (IOException e) {
                    Log.w(TAG, "Error closing output stream", e);
                } finally {
                    outputStream = null;
                }
            }

            if (bluetoothSocket != null) {
                try {
                    if (bluetoothSocket.isConnected()) {
                        Log.d(TAG, "Closing connected Bluetooth socket");
                        bluetoothSocket.close();
                    } else {
                        Log.d(TAG, "Bluetooth socket was not connected, closing anyway");
                        bluetoothSocket.close();
                    }
                    Log.d(TAG, "Bluetooth socket closed successfully");
                } catch (IOException e) {
                    Log.w(TAG, "Error closing Bluetooth socket", e);
                } catch (Exception e) {
                    Log.w(TAG, "Unexpected error closing Bluetooth socket", e);
                } finally {
                    bluetoothSocket = null;
                }
            }

            resetConnectionState();

            if (connectionListener != null) {
                connectionListener.onDeviceDisconnected();
            }

            Log.d(TAG, "Disconnect completed successfully");

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during disconnect", e);
        }
    }

    private void resetConnectionState() {
        Log.d(TAG, "Resetting connection state");

        isListeningForConnection = false;

        bluetoothSocket = null;
        bluetoothServerSocket = null;
        inputStream = null;
        outputStream = null;
        serverThread = null;

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Log.d(TAG, "Connection state reset complete");
    }

    public boolean isConnected() {
        try {
            return bluetoothSocket != null && bluetoothSocket.isConnected();
        } catch (Exception e) {
            Log.w(TAG, "Error checking connection status", e);
            return false;
        }
    }

    public void cleanup() {
        Log.d(TAG, "cleanup() called");
        cancelContinuousReconnect();
        try {
            stopDiscovery();
            stopListening();
            disconnect();

            connectionListener = null;

            Log.d(TAG, "Cleanup completed successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }

    // New methods for auto-reconnection

    private void stopAutoReconnect() {
        reconnectionHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "Auto-reconnect stopped");
    }

    // Call when a clean manual disconnect occurs
    public void cancelContinuousReconnect() {
        reconnectionHandler.removeCallbacks(continuousReconnectRunnable);
    }

}
