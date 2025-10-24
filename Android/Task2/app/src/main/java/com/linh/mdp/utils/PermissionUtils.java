package com.linh.mdp.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for handling Bluetooth permissions across different Android versions
 */
public class PermissionUtils {

    /**
     * Check if all required Bluetooth permissions are granted
     */
    public static boolean checkBluetoothPermissions(Activity activity) {
        List<String> permissionsNeeded = getRequiredBluetoothPermissions(activity);
        return permissionsNeeded.isEmpty();
    }

    /**
     * Check and request Bluetooth permissions if needed
     * @return true if permissions are already granted, false if we need to request them
     */
    public static boolean checkAndRequestBluetoothPermissions(Activity activity) {
        List<String> permissionsNeeded = getRequiredBluetoothPermissions(activity);

        if (permissionsNeeded.isEmpty()) {
            return false;
        }

        ActivityCompat.requestPermissions(
            activity,
            permissionsNeeded.toArray(new String[0]),
            BluetoothConstants.REQUEST_PERMISSIONS
        );
        return true;
    }

    /**
     * Get the list of required Bluetooth permissions based on Android version
     */
    private static List<String> getRequiredBluetoothPermissions(Activity activity) {
        List<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 and above
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        } else {
            // Below Android 12
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

        return permissionsNeeded;
    }

    // Private constructor to prevent instantiation
    private PermissionUtils() {
        throw new AssertionError("Cannot instantiate utility class");
    }
}
