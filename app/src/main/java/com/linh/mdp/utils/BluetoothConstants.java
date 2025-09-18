package com.linh.mdp.utils;

import java.util.UUID;

/**
 * Constants used throughout the Bluetooth application
 */
public final class BluetoothConstants {

    // Standard SPP (Serial Port Profile) UUID
    public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Fallback UUIDs for different device compatibility
    public static final UUID[] FALLBACK_UUIDS = {
        UUID.fromString("00001105-0000-1000-8000-00805F9B34FB"), // OBEX Object Push
        UUID.fromString("0000110A-0000-1000-8000-00805F9B34FB"), // Audio Source
        UUID.fromString("0000110C-0000-1000-8000-00805F9B34FB"), // Remote Control Target
        UUID.fromString("00001108-0000-1000-8000-00805F9B34FB"), // Headset
        UUID.fromString("0000111E-0000-1000-8000-00805F9B34FB"), // Hands-free
        UUID.fromString("00001112-0000-1000-8000-00805F9B34FB"), // Headset Audio Gateway
        UUID.fromString("00001116-0000-1000-8000-00805F9B34FB"), // NAP (Network Access Point)
        UUID.fromString("0000111F-0000-1000-8000-00805F9B34FB")  // HFP (Hands-Free Profile)
    };

    // Request codes
    public static final int REQUEST_PERMISSIONS = 2;

    // Timeouts
    public static final long CONNECTION_TIMEOUT_MS = 60000; // 60 seconds
    public static final long DISCOVERY_TIMEOUT_MS = 12000;  // 12 seconds

    // Delay between retry attempts in milliseconds
    public static final long RETRY_DELAY_MS = 2000; // 2 seconds

    // Intent extras
    public static final String EXTRA_DEVICE_NAME = "DEVICE_NAME";

    // Private constructor to prevent instantiation
    private BluetoothConstants() {
        throw new AssertionError("Cannot instantiate utility class");
    }
}
