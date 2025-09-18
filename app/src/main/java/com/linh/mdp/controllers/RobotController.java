package com.linh.mdp.controllers;

import android.content.Context;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.linh.mdp.adapters.GridTableAdapter;
import com.linh.mdp.bluetooth.BluetoothHelper;

/**
 * Controller class to handle robot-related operations
 */
public class RobotController {

    private final Context context;
    private GridTableAdapter gridAdapter;
    private BluetoothHelper bluetoothHelper;

    // UI components
    private Button placeRobotButton;
    private Button robotConfirmButton; // new confirm button
    private Button robotUpButton;
    private Button robotDownButton;
    private Button robotTurnLeftButton;
    private Button robotTurnRightButton;
    private TextView robotStatusText;

    private boolean isPlacingRobotMode = false;
    private boolean suppressCancelToastOnce = false; // prevent cancel toast after confirm

    public RobotController(Context context) {
        this.context = context;
    }

    public void initialize(GridTableAdapter gridAdapter, BluetoothHelper bluetoothHelper) {
        this.gridAdapter = gridAdapter;
        this.bluetoothHelper = bluetoothHelper;
    }

    public void setUIComponents(Button placeRobotButton, Button robotConfirmButton, Button robotUpButton, Button robotDownButton,
                               Button robotTurnLeftButton, Button robotTurnRightButton, TextView robotStatusText) {
        this.placeRobotButton = placeRobotButton;
        this.robotConfirmButton = robotConfirmButton;
        this.robotUpButton = robotUpButton;
        this.robotDownButton = robotDownButton;
        this.robotTurnLeftButton = robotTurnLeftButton;
        this.robotTurnRightButton = robotTurnRightButton;
        this.robotStatusText = robotStatusText;

        setupClickListeners();
        updateRobotButtonsState();
        updateRobotStatusText();
        updateConfirmButtonVisibility();
    }

    private void setupClickListeners() {
        if (placeRobotButton != null) {
            placeRobotButton.setOnClickListener(v -> togglePlaceRobotMode());
        }

        if (robotConfirmButton != null) {
            robotConfirmButton.setOnClickListener(v -> confirmRobotPlacement());
        }

        if (robotUpButton != null) {
            robotUpButton.setOnClickListener(v -> moveRobotForward());
        }

        if (robotDownButton != null) {
            robotDownButton.setOnClickListener(v -> moveRobotReverse());
        }

        if (robotTurnLeftButton != null) {
            robotTurnLeftButton.setOnClickListener(v -> turnRobotLeft());
        }

        if (robotTurnRightButton != null) {
            robotTurnRightButton.setOnClickListener(v -> turnRobotRight());
        }
    }

    // Helpers for continuous drag reuse (shared with obstacle smooth drag)
    private void beginContinuousDragIfSupported() {
        if (gridAdapter != null && !gridAdapter.isInContinuousDrag()) {
            gridAdapter.beginContinuousDrag();
        }
    }
    private void endContinuousDragIfActive() {
        if (gridAdapter != null && gridAdapter.isInContinuousDrag()) {
            gridAdapter.endContinuousDrag();
        }
    }

    public void moveRobotForward() {
        if (gridAdapter != null && gridAdapter.hasRobot()) {
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
            // Attempt the move
            boolean moved = gridAdapter.moveRobot(dRow, dCol);
            if (moved) {
                updateRobotStatusText();
                sendBluetoothCommand("f");
            } else {
                showToast("Blocked - cannot move forward");
            }
        } else {
            showToast("Place the robot first");
        }
    }

    public void moveRobotReverse() {
        if (gridAdapter != null && gridAdapter.hasRobot()) {
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
            if (moved) {
                updateRobotStatusText();
                sendBluetoothCommand("r");
            } else {
                showToast("Blocked - cannot move reverse");
            }
        } else {
            showToast("Place the robot first");
        }
    }

    public void turnRobotLeft() {
        if (gridAdapter != null && gridAdapter.hasRobot()) {
            gridAdapter.turnRobotLeft();
            updateRobotStatusText();
        } else {
            showToast("Place the robot first");
        }

        // Send turn-left command via Bluetooth
        sendBluetoothCommand("tl");
    }

    public void turnRobotRight() {
        if (gridAdapter != null && gridAdapter.hasRobot()) {
            gridAdapter.turnRobotRight();
            updateRobotStatusText();
        } else {
            showToast("Place the robot first");
        }

        // Send turn-right command via Bluetooth
        sendBluetoothCommand("tr");
    }

    public void togglePlaceRobotMode() {
        isPlacingRobotMode = !isPlacingRobotMode;
        setPlacingRobotMode(isPlacingRobotMode);
    }

    public void setPlacingRobotMode(boolean enabled) {
        isPlacingRobotMode = enabled;
        if (enabled) {
            beginContinuousDragIfSupported();
            // Clear any temp preview first
            if (gridAdapter != null) gridAdapter.clearTemporaryRobotPreview();
            if (placeRobotButton != null) {
                placeRobotButton.setText("Cancel Placement");
            }
            showToast("Drag on grid to preview the robot, then tap Confirm");
        } else {
            endContinuousDragIfActive();
            if (gridAdapter != null) gridAdapter.clearTemporaryRobotPreview();
            if (placeRobotButton != null) {
                placeRobotButton.setText("Place Robot (3x3)");
            }
            if (!suppressCancelToastOnce) {
                showToast("Robot placement cancelled");
            }
            suppressCancelToastOnce = false; // reset flag
        }
        updateConfirmButtonVisibility();
    }

    public boolean handleRobotPlacementClick(int row, int col) {
        if (!isPlacingRobotMode || gridAdapter == null) return false;
        // Legacy click-to-place kept for compatibility if user taps without dragging
        return previewRobotAt(row, col);
    }

    // Called continuously during grid touch-drag
    public boolean previewRobotAt(int row, int col) {
        if (!isPlacingRobotMode || gridAdapter == null) return false;
        beginContinuousDragIfSupported();
        boolean shown = gridAdapter.showTemporaryRobotAtCenter(row, col);
        updateConfirmButtonVisibility();
        if (robotStatusText != null && shown) {
            int displayRow = gridAdapter.getGridSize() - 2 - row;
            int displayCol = col - 1;
            robotStatusText.setText("Preview robot at (" + displayRow + ", " + displayCol + ")");
        }
        return true; // handled (even if not shown due to invalid position)
    }

    public void confirmRobotPlacement() {
        if (!isPlacingRobotMode || gridAdapter == null) return;
        boolean placed = gridAdapter.confirmTemporaryRobotPlacement();
        if (placed) {
            int[] pos = gridAdapter.getRobotPosition();
            if (pos != null && robotStatusText != null) {
                int displayRow = gridAdapter.getGridSize() - 2 - pos[0];
                int displayCol = pos[1] - 1;
                robotStatusText.setText("Robot at (" + displayRow + ", " + displayCol + ") placed");
            }
            suppressCancelToastOnce = true; // avoid showing cancel toast when exiting after confirm
            endContinuousDragIfActive();
            setPlacingRobotMode(false);
            updateRobotButtonsState();
        } else {
            showToast("Invalid position - cannot place robot");
        }
        updateConfirmButtonVisibility();
    }

    private void updateConfirmButtonVisibility() {
        if (robotConfirmButton != null) {
            boolean show = isPlacingRobotMode && gridAdapter != null && gridAdapter.getTempRobotCenterRow() != -1;
            robotConfirmButton.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
            robotConfirmButton.setEnabled(show);
        }
    }

    public void updateRobotStatusText() {
        if (robotStatusText != null && gridAdapter != null) {
            if (gridAdapter.hasRobot()) {
                int[] robotPos = gridAdapter.getRobotPosition();
                if (robotPos != null && robotPos.length >= 2) {
                    // Convert internal coordinates to display coordinates (0-19 range)
                    int displayRow = gridAdapter.getGridSize() - 2 - robotPos[0];
                    int displayCol = robotPos[1] - 1;
                    String orientation = getRobotOrientationString(gridAdapter.getRobotOrientation());
                    robotStatusText.setText("Robot at (" + displayRow + ", " + displayCol + ") facing " + orientation);
                } else {
                    robotStatusText.setText("Robot position unknown");
                }
            } else {
                robotStatusText.setText("Robot not placed");
            }
        }
    }

    public void updateRobotButtonsState() {
        if (placeRobotButton != null) {
            // Always default to "Place Robot (3x3)" when not actively placing
            if (!isPlacingRobotMode) {
                placeRobotButton.setText("Place Robot (3x3)");
            }
        }
        boolean hasRobot = gridAdapter != null && gridAdapter.hasRobot();
        if (robotUpButton != null) robotUpButton.setEnabled(hasRobot);
        if (robotDownButton != null) robotDownButton.setEnabled(hasRobot);
        if (robotTurnLeftButton != null) robotTurnLeftButton.setEnabled(hasRobot);
        if (robotTurnRightButton != null) robotTurnRightButton.setEnabled(hasRobot);
    }

    private String getRobotOrientationString(int orientation) {
        switch (orientation) {
            case 0: return "North";
            case 1: return "East";
            case 2: return "South";
            case 3: return "West";
            default: return "Unknown";
        }
    }

    private void sendBluetoothCommand(String command) {
        if (bluetoothHelper != null && bluetoothHelper.isConnected()) {
            bluetoothHelper.sendData(command);
        } else {
            showToast("Not connected");
        }
    }

    private void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public boolean isPlacingRobotMode() {
        return isPlacingRobotMode;
    }

    public void exitPlacementMode() {
        if (isPlacingRobotMode) {
            endContinuousDragIfActive();
            setPlacingRobotMode(false);
        }
    }
}
