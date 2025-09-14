package com.linh.mdp.parsers;

import android.util.Log;

import com.linh.mdp.adapters.GridTableAdapter;

/**
 * Parser class to handle different types of incoming Bluetooth messages
 */
public class MessageParser {

    private static final String TAG = "MessageParser";

    private GridTableAdapter gridAdapter;
    private OnMessageParsedListener listener;

    public interface OnMessageParsedListener {
        void onRobotCommandParsed(String command);
        void onTargetMessageParsed(int obstacleNumber, String targetId);
        void onRobotPositionParsed(int x, int y, String direction);
        void onImportantMessageReceived(String message);
        void showToast(String message);
        String getCurrentTimestamp();
        void appendToReceivedData(String message);
        void updateRobotStatusText();
    }

    public MessageParser(GridTableAdapter gridAdapter, OnMessageParsedListener listener) {
        this.gridAdapter = gridAdapter;
        this.listener = listener;
    }

    /**
     * Parse and handle basic robot control commands from incoming data
     */
    public void parseAndHandleRobotCommands(String data) {
        if (data == null || gridAdapter == null) return;

        String trimmed = data.trim().toLowerCase();
        if (trimmed.isEmpty()) return;

        // Handle basic robot movement commands
        switch (trimmed) {
            case "f":
            case "forward":
                handleForwardCommand();
                if (listener != null) {
                    listener.onRobotCommandParsed("forward");
                }
                break;

            case "r":
            case "reverse":
                handleReverseCommand();
                if (listener != null) {
                    listener.onRobotCommandParsed("reverse");
                }
                break;

            case "tl":
            case "turnleft":
            case "turn_left":
                handleTurnLeftCommand();
                if (listener != null) {
                    listener.onRobotCommandParsed("turnleft");
                }
                break;

            case "tr":
            case "turnright":
            case "turn_right":
                handleTurnRightCommand();
                if (listener != null) {
                    listener.onRobotCommandParsed("turnright");
                }
                break;

            default:
                // Not a basic robot command
                break;
        }
    }

    private void handleForwardCommand() {
        if (gridAdapter.hasRobot()) {
            int dRow = 0, dCol = 0;
            switch (gridAdapter.getRobotOrientation()) {
                case 0: dRow = -1; dCol = 0; break; // North
                case 1: dRow = 0; dCol = 1; break;  // East
                case 2: dRow = 1; dCol = 0; break;  // South
                case 3: dRow = 0; dCol = -1; break; // West
            }
            boolean moved = gridAdapter.moveRobot(dRow, dCol);
            if (listener != null) {
                listener.updateRobotStatusText();
            }
            Log.d(TAG, "Robot forward command received, moved: " + moved);
        } else {
            Log.w(TAG, "Forward command received but robot not placed");
        }
    }

    private void handleReverseCommand() {
        if (gridAdapter.hasRobot()) {
            int dRow = 0, dCol = 0;
            switch (gridAdapter.getRobotOrientation()) {
                case 0: dRow = 1; dCol = 0; break;   // opposite of North
                case 1: dRow = 0; dCol = -1; break;  // opposite of East
                case 2: dRow = -1; dCol = 0; break;  // opposite of South
                case 3: dRow = 0; dCol = 1; break;   // opposite of West
            }
            boolean moved = gridAdapter.moveRobot(dRow, dCol);
            if (listener != null) {
                listener.updateRobotStatusText();
            }
            Log.d(TAG, "Robot reverse command received, moved: " + moved);
        } else {
            Log.w(TAG, "Reverse command received but robot not placed");
        }
    }

    private void handleTurnLeftCommand() {
        if (gridAdapter.hasRobot()) {
            gridAdapter.turnRobotLeft();
            if (listener != null) {
                listener.updateRobotStatusText();
            }
            Log.d(TAG, "Robot turn left command received");
        } else {
            Log.w(TAG, "Turn left command received but robot not placed");
        }
    }

    private void handleTurnRightCommand() {
        if (gridAdapter.hasRobot()) {
            gridAdapter.turnRobotRight();
            if (listener != null) {
                listener.updateRobotStatusText();
            }
            Log.d(TAG, "Robot turn right command received");
        } else {
            Log.w(TAG, "Turn right command received but robot not placed");
        }
    }

    /**
     * Parse and handle TARGET messages in the format: "TARGET, <Obstacle Number>, <Target ID>"
     */
    public void parseAndHandleTargetMessages(String data) {
        if (data == null || gridAdapter == null) return;

        String trimmed = data.trim();
        if (trimmed.isEmpty()) return;

        // Check if this is a TARGET message
        if (trimmed.toUpperCase().startsWith("TARGET")) {
            try {
                // Split by comma and clean up whitespace
                String[] parts = trimmed.split(",");
                if (parts.length >= 3) {
                    String command = parts[0].trim().toUpperCase();
                    String obstacleNumberStr = parts[1].trim();
                    String targetId = parts[2].trim();

                    // Validate that we have the TARGET command
                    if ("TARGET".equals(command)) {
                        // Parse obstacle number
                        int obstacleNumber = Integer.parseInt(obstacleNumberStr);

                        // Apply target styling to the obstacle
                        boolean success = gridAdapter.setObstacleAsTarget(obstacleNumber, targetId);

                        if (success) {
                            Log.d(TAG, "Target assigned: Obstacle " + obstacleNumber + " -> Target ID: " + targetId);

                            if (listener != null) {
                                listener.onTargetMessageParsed(obstacleNumber, targetId);
                                listener.showToast("Obstacle " + obstacleNumber + " marked as target: " + targetId);

                                // Add to received data display
                                String timestamp = listener.getCurrentTimestamp();
                                String formattedMessage = "[" + timestamp + "] TARGET: Obstacle " + obstacleNumber + " -> " + targetId + "\n";
                                listener.appendToReceivedData(formattedMessage);
                            }
                        } else {
                            Log.w(TAG, "Failed to assign target: Obstacle " + obstacleNumber + " not found");
                            if (listener != null) {
                                listener.showToast("Obstacle " + obstacleNumber + " not found - cannot assign target");
                            }
                        }
                    }
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid obstacle number in TARGET message: " + data, e);
                if (listener != null) {
                    listener.showToast("Invalid TARGET message format");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing TARGET message: " + data, e);
                if (listener != null) {
                    listener.showToast("Error processing TARGET message");
                }
            }
        }
    }

    /**
     * Parse and handle ROBOT messages in the format: "ROBOT, <x>, <y>, <direction>"
     */
    public void parseAndHandleRobotMessages(String data) {
        if (data == null || gridAdapter == null) return;

        String trimmed = data.trim();
        if (trimmed.isEmpty()) return;

        // Check if this is a ROBOT message
        if (trimmed.toUpperCase().startsWith("ROBOT")) {
            try {
                // Split by comma and clean up whitespace
                String[] parts = trimmed.split(",");
                if (parts.length >= 4) {
                    String command = parts[0].trim().toUpperCase();
                    String xStr = parts[1].trim();
                    String yStr = parts[2].trim();
                    String direction = parts[3].trim();

                    // Validate that we have the ROBOT command
                    if ("ROBOT".equals(command)) {
                        // Parse coordinates
                        int x = Integer.parseInt(xStr);
                        int y = Integer.parseInt(yStr);

                        // Validate coordinates are in valid range (0-19)
                        if (x < 0 || x > 19 || y < 0 || y > 19) {
                            Log.w(TAG, "Invalid coordinates in ROBOT message: (" + x + ", " + y + ")");
                            if (listener != null) {
                                listener.showToast("Invalid robot coordinates: (" + x + ", " + y + ")");
                            }
                            return;
                        }

                        // Update robot position and direction
                        boolean success = gridAdapter.updateRobotPosition(x, y, direction);

                        if (success) {
                            Log.d(TAG, "Robot updated: Position (" + x + ", " + y + ") facing " + direction);

                            if (listener != null) {
                                listener.onRobotPositionParsed(x, y, direction);
                                listener.showToast("Robot moved to (" + x + ", " + y + ") facing " + direction);
                                listener.updateRobotStatusText();

                                // Add to received data display
                                String timestamp = listener.getCurrentTimestamp();
                                String formattedMessage = "[" + timestamp + "] ROBOT: Position (" + x + ", " + y + ") facing " + direction + "\n";
                                listener.appendToReceivedData(formattedMessage);
                            }
                        } else {
                            Log.w(TAG, "Failed to update robot position: (" + x + ", " + y + ") facing " + direction);
                            if (listener != null) {
                                listener.showToast("Cannot place robot at (" + x + ", " + y + ") - position blocked or invalid");
                            }
                        }
                    }
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid coordinates in ROBOT message: " + data, e);
                if (listener != null) {
                    listener.showToast("Invalid ROBOT message format - coordinates must be numbers");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing ROBOT message: " + data, e);
                if (listener != null) {
                    listener.showToast("Error processing ROBOT message");
                }
            }
        }
    }

    /**
     * Determine if a received message should be displayed in the UI
     * Only displays important/status messages and filters out routine data
     */
    public boolean shouldDisplayMessage(String data) {
        if (data == null) return false;

        String lowerData = data.toLowerCase().trim();
        if (lowerData.isEmpty()) return false;

        // First check if message contains any filter-out patterns - these should NOT be displayed
        String[] filterOutPatterns = {
            "sensor:", "coordinate:", "x:", "y:", "z:", "temp:", "humidity:",
            "pressure:", "voltage:", "current:", "raw data:", "debug:",
            "trace:", "heartbeat", "ping", "ack", "data:", "value:",
            "reading:", "measurement:", "sample:", "update:", "sync:",
            "buffer:", "packet:", "frame:", "bytes:", "signal:", "noise:",
            "rssi:", "timestamp:", "counter:", "index:", "position:",
            "angle:", "speed:", "acceleration:", "gyro:", "compass:",
            "gps:", "wifi:", "bluetooth:", "cellular:", "network:"
        };

        for (String pattern : filterOutPatterns) {
            if (lowerData.contains(pattern)) {
                return false;
            }
        }

        // Then check if message contains any important patterns - these SHOULD be displayed
        String[] importantPatterns = {
            "ready to start", "looking for target", "target found", "target lost",
            "mission complete", "obstacle detected", "path blocked", "battery low",
            "error", "warning", "status:", "connected", "disconnected",
            "initialization", "calibration", "startup", "shutdown",
            "emergency", "alert", "mission", "task", "complete", "failed",
            "success", "abort", "stop", "pause", "resume", "scanning",
            "searching", "found", "lost", "detected", "arrived", "destination"
        };

        for (String pattern : importantPatterns) {
            if (lowerData.contains(pattern)) {
                return true;
            }
        }

        // Default to NOT show messages unless they match important patterns
        return false;
    }
}
