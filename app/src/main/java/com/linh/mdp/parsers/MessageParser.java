package com.linh.mdp.parsers;

import android.util.Log;

import com.linh.mdp.adapters.GridTableAdapter;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parser class to handle different types of incoming Bluetooth messages
 */
public class MessageParser {

    private static final String TAG = "MessageParser";

    private final GridTableAdapter gridAdapter;
    private final OnMessageParsedListener listener;

    public interface OnMessageParsedListener {
        void onRobotCommandParsed(String command);
        void onTargetMessageParsed(int obstacleNumber, String targetId);
        void onRobotPositionParsed(int x, int y, String direction);

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
                case 0: dRow = -1;
                    break; // North
                case 1:
                    dCol = 1; break;  // East
                case 2: dRow = 1;
                    break;  // South
                case 3:
                    dCol = -1; break; // West
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
                case 0: dRow = 1;
                    break;   // opposite of North
                case 1:
                    dCol = -1; break;  // opposite of East
                case 2: dRow = -1;
                    break;  // opposite of South
                case 3:
                    dCol = 1; break;   // opposite of West
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
     * Parse and handle TARGET messages in JSON format: {"cat": "image-rec", "value": {"image_id": "A", "obstacle_id": "1"}}
     */
    public void parseAndHandleTargetMessages(String data) {
        if (data == null || gridAdapter == null) return;

        String trimmed = data.trim();
        if (trimmed.isEmpty()) return;

        // Check if this is a JSON message
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                JSONObject jsonObject = new JSONObject(trimmed);

                // Check if this is an image-rec message
                if (jsonObject.has("cat") && "image-rec".equals(jsonObject.getString("cat"))) {

                    // Extract the value object
                    if (jsonObject.has("value")) {
                        JSONObject valueObject = jsonObject.getJSONObject("value");

                        // Extract image_id and obstacle_id
                        if (valueObject.has("image_id") && valueObject.has("obstacle_id")) {
                            String imageId = valueObject.getString("image_id");
                            String obstacleIdStr = valueObject.getString("obstacle_id");

                            // Parse obstacle number
                            int obstacleNumber = Integer.parseInt(obstacleIdStr);

                            // Apply target styling to the obstacle using image_id as the target ID
                            boolean success = gridAdapter.setObstacleAsTarget(obstacleNumber, imageId);

                            if (success) {
                                Log.d(TAG, "Target assigned: Obstacle " + obstacleNumber + " -> Image ID: " + imageId);

                                if (listener != null) {
                                    listener.onTargetMessageParsed(obstacleNumber, imageId);
                                    listener.showToast("Obstacle " + obstacleNumber + " marked as target: " + imageId);

                                    // Add to received data display
                                    String timestamp = listener.getCurrentTimestamp();
                                    String formattedMessage = "[" + timestamp + "] IMAGE-REC: Obstacle " + obstacleNumber + " -> " + imageId + "\n";
                                    listener.appendToReceivedData(formattedMessage);
                                }
                            } else {
                                Log.w(TAG, "Failed to assign target: Obstacle " + obstacleNumber + " not found");
                                if (listener != null) {
                                    listener.showToast("Obstacle " + obstacleNumber + " not found - cannot assign target");
                                }
                            }
                        } else {
                            Log.w(TAG, "Missing image_id or obstacle_id in JSON value object");
                            if (listener != null) {
                                listener.showToast("Invalid image recognition message: missing required fields");
                            }
                        }
                    } else {
                        Log.w(TAG, "Missing 'value' object in image-rec JSON message");
                        if (listener != null) {
                            listener.showToast("Invalid image recognition message: missing value object");
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Invalid JSON format in message: " + data, e);
                if (listener != null) {
                    listener.showToast("Invalid JSON format in message");
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid obstacle_id number in JSON message: " + data, e);
                if (listener != null) {
                    listener.showToast("Invalid obstacle_id in image recognition message");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing JSON image recognition message: " + data, e);
                if (listener != null) {
                    listener.showToast("Error processing image recognition message");
                }
            }
        }
    }


    /**
     * Parse and handle ROBOT messages in JSON format: {"cat": "location", "value": "{\"x\": 1, \"y\": 1, \"d\": 0}"}
     */
    public void parseAndHandleRobotMessages(String data) {
        if (data == null || gridAdapter == null) return;

        String trimmed = data.trim();
        if (trimmed.isEmpty()) return;

        // Check if this is a JSON message
        try {
            JSONObject jsonObject = new JSONObject(trimmed);

            // Check if this is a location message
            if (jsonObject.has("cat") && "location".equals(jsonObject.getString("cat"))) {

                // Extract the value - it can be either a JSON object or a JSON string
                if (jsonObject.has("value")) {
                    JSONObject valueObject;

                    // Check if value is a string (nested JSON) or already an object
                    Object valueRaw = jsonObject.get("value");
                    if (valueRaw instanceof String) {
                        // Parse the nested JSON string
                        String valueString = jsonObject.getString("value");
                        valueObject = new JSONObject(valueString);
                    } else {
                        // Value is already a JSON object
                        valueObject = jsonObject.getJSONObject("value");
                    }

                    // Extract x, y, and d (direction)
                    if (valueObject.has("x") && valueObject.has("y") && valueObject.has("d")) {
                        int x = valueObject.getInt("x");
                        int y = valueObject.getInt("y");
                        int direction = valueObject.getInt("d");

                        // Validate coordinates are in valid range (0-19)
                        if (x < 0 || x > 19 || y < 0 || y > 19) {
                            Log.w(TAG, "Invalid coordinates in location message: (" + x + ", " + y + ")");
                            if (listener != null) {
                                listener.showToast("Invalid robot coordinates: (" + x + ", " + y + ")");
                            }
                            return;
                        }

                        // Convert direction number to string
                        String directionStr = convertDirectionToString(direction);

                        if (directionStr != null) {
                            // Update robot position and direction
                            boolean success = gridAdapter.updateRobotPosition(x, y, directionStr);

                            if (success) {
                                Log.d(TAG, "Robot updated: Position (" + x + ", " + y + ") facing " + directionStr);

                                if (listener != null) {
                                    listener.onRobotPositionParsed(x, y, directionStr);
                                    listener.showToast("Robot moved to (" + x + ", " + y + ") facing " + directionStr);
                                    listener.updateRobotStatusText();

                                    // Add to received data display
                                    String timestamp = listener.getCurrentTimestamp();
                                    String formattedMessage = "[" + timestamp + "] LOCATION: Position (" + x + ", " + y + ") facing " + directionStr + "\n";
                                    listener.appendToReceivedData(formattedMessage);
                                }
                            } else {
                                Log.w(TAG, "Failed to update robot position: (" + x + ", " + y + ") facing " + directionStr);
                                if (listener != null) {
                                    listener.showToast("Cannot place robot at (" + x + ", " + y + ") - position blocked or invalid");
                                }
                            }
                        } else {
                            Log.w(TAG, "Invalid direction value in location message: " + direction);
                            if (listener != null) {
                                listener.showToast("Invalid direction value: " + direction);
                            }
                        }
                    } else {
                        Log.w(TAG, "Missing x, y, or d in JSON value object");
                        if (listener != null) {
                            listener.showToast("Invalid location message: missing required fields");
                        }
                    }
                } else {
                    Log.w(TAG, "Missing 'value' object in location JSON message");
                    if (listener != null) {
                        listener.showToast("Invalid location message: missing value object");
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON format in location message: " + data, e);
            if (listener != null) {
                listener.showToast("Invalid JSON format in location message");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing JSON location message: " + data, e);
            if (listener != null) {
                listener.showToast("Error processing location message");
            }
        }
    }

    /**
     * Helper method to convert direction number to direction string
     * @param direction 0=North, 2=East, 4=South, 6=West
     * @return direction string or null if invalid
     */
    private String convertDirectionToString(int direction) {
        switch (direction) {
            case 0: return "N";
            case 2: return "E";
            case 4: return "S";
            case 6: return "W";
            default: return null;
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
