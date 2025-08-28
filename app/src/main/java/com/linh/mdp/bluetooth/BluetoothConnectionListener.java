package com.linh.mdp.bluetooth;

import android.bluetooth.BluetoothDevice;

/**
 * Interface for Bluetooth connection events and callbacks
 */
public interface BluetoothConnectionListener {
    void onDeviceConnected(BluetoothDevice device);
    void onDeviceDisconnected();
    void onDataReceived(String data);
    void onConnectionFailed(String error);
    void onDeviceDiscovered(BluetoothDevice device);
    void onWaitingForConnection(BluetoothDevice device);
    void onConnectionTimeout();
}
