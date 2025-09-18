package com.linh.mdp.utils;

import java.util.UUID;

/**
 * Constants used throughout the Bluetooth application
 */
public final class BluetoothConstants {

    // Standard SPP (Serial Port Profile) UUID
    public static final UUID MY_UUID = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee");

    // Request codes
    public static final int REQUEST_PERMISSIONS = 2;

    // Timeouts - Optimized for faster connections
    public static final long CONNECTION_TIMEOUT_MS = 10000; // Reduced from 60s to 10s
    public static final long DISCOVERY_TIMEOUT_MS = 8000;   // Reduced from 12s to 8s
    // Intent extras
    public static final String EXTRA_DEVICE_NAME = "DEVICE_NAME";

    // Private constructor to prevent instantiation
    private BluetoothConstants() {
        throw new AssertionError("Cannot instantiate utility class");
    }
}
