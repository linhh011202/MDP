package com.linh.mdp.controllers;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.linh.mdp.adapters.GridTableAdapter;
import com.linh.mdp.bluetooth.BluetoothHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Controller class to handle obstacle-related operations
 */
public class ObstacleController {


    private final Context context;
    private GridTableAdapter gridAdapter;
    private BluetoothHelper bluetoothHelper;

    // UI components
    private Button clearAllObstaclesButton;
    private Button confirmObstacleButton;
    private Button cancelObstacleButton;
    private Button sendObstaclesButton; // new send obstacles button
    private Button toggleSendObstaclesButton; // toggle button for send obstacles
    private TextView obstacleActionStatus;
    private View borderDirectionSection;
    private EditText tempObstacleXInput;
    private EditText tempObstacleYInput;
    private Button placeTempObstacleButton;
    private TextView tempObstacleStatusText;
    private Spinner tempObstacleDirectionSpinner;

    // State variables
    private boolean isObstacleModeEnabled = false;
    private boolean isSendObstaclesEnabled = false; // new state for send obstacles toggle - default to disabled
    private String currentObstacleAction = "";
    private boolean isDraggingObstacle = false;
    private int temporaryObstacleRow = -1;
    private int temporaryObstacleCol = -1;
    private int previousTemporaryRow = -1;
    private int previousTemporaryCol = -1;
    private int selectedObstacleRow = -1;
    private int selectedObstacleCol = -1;
    private int previewBorderRow = -1;
    private int previewBorderCol = -1;
    private boolean hasPreviewBorder = false;
    private OnObstacleModeChangeListener obstacleListener;

    public interface OnObstacleModeChangeListener {
        void onObstacleModeChanged(boolean enabled);
        void updateGridTableHeader();
        String getCurrentTimestamp();
        void appendToReceivedData(String message);
        void appendToSentData(String message);
    }

    public ObstacleController(Context context) {
        this.context = context;
    }

    public void initialize(GridTableAdapter gridAdapter, BluetoothHelper bluetoothHelper, OnObstacleModeChangeListener listener) {
        this.gridAdapter = gridAdapter;
        this.bluetoothHelper = bluetoothHelper;
        this.obstacleListener = listener;
    }

    public void setUIComponents(Button clearAllObstaclesButton,
                               Button confirmObstacleButton, Button cancelObstacleButton,
                               TextView obstacleActionStatus, View borderDirectionSection,
                               Button sendObstaclesButton, Button toggleSendObstaclesButton, EditText tempObstacleXInput, EditText tempObstacleYInput,
                               Button placeTempObstacleButton, TextView tempObstacleStatusText, Spinner tempObstacleDirectionSpinner) {
        this.clearAllObstaclesButton = clearAllObstaclesButton;
        this.confirmObstacleButton = confirmObstacleButton;
        this.cancelObstacleButton = cancelObstacleButton;
        this.obstacleActionStatus = obstacleActionStatus;
        this.borderDirectionSection = borderDirectionSection;
        this.sendObstaclesButton = sendObstaclesButton;
        this.toggleSendObstaclesButton = toggleSendObstaclesButton;
        this.tempObstacleXInput = tempObstacleXInput;
        this.tempObstacleYInput = tempObstacleYInput;
        this.placeTempObstacleButton = placeTempObstacleButton;
        this.tempObstacleStatusText = tempObstacleStatusText;
        this.tempObstacleDirectionSpinner = tempObstacleDirectionSpinner;

        setupClickListeners();
        updateSendObstaclesButtonState(); // Initialize the button state
    }

    private void setupClickListeners() {
        // Action buttons
        // Per user request: "Clear All" should only execute when clicked, after Remove mode exposes it
        if (clearAllObstaclesButton != null) {
            clearAllObstaclesButton.setOnClickListener(v -> clearAllObstaclesAndResetUI());
        }
        if (confirmObstacleButton != null) {
            confirmObstacleButton.setOnClickListener(v -> confirmObstacleAction());
        }
        if (cancelObstacleButton != null) {
            cancelObstacleButton.setOnClickListener(v -> cancelObstacleAction());
        }

        // Add click listener for sending all obstacles
        if (sendObstaclesButton != null) {
            sendObstaclesButton.setOnClickListener(v -> sendAllObstacles());
        }

        // Add click listener for temporary obstacle placement by coordinates
        if (placeTempObstacleButton != null) {
            placeTempObstacleButton.setOnClickListener(v -> placeTempObstacleByCoordinates());
        }

        // Add click listener for toggle button
        if (toggleSendObstaclesButton != null) {
            toggleSendObstaclesButton.setOnClickListener(v -> toggleSendObstacles());
        }
    }

    public void toggleObstacleMode() {
        // Make obstacle mode only enable and never disable
        boolean wasEnabled = isObstacleModeEnabled;
        isObstacleModeEnabled = true;
        if (!wasEnabled) {
            showToast("Obstacle Mode Enabled - Click cells to add obstacles");
        }
        if (obstacleListener != null) {
            obstacleListener.onObstacleModeChanged(true);
        }
    }

    public void setCurrentObstacleAction(String action) {
        // If leaving add drag mode, end continuous drag
        if (!"add".equals(action)) {
            endContinuousDragIfActive();
        }
        currentObstacleAction = action;

        // Clear any temporary obstacle when switching actions
        clearTemporaryObstacle();

        // Clear any unconfirmed preview border when changing action
        clearPreviewBorderIfAny();

        // If switching away from current mode, clear any selected obstacle highlight
        if (selectedObstacleRow != -1 && selectedObstacleCol != -1) {
            if (gridAdapter != null) {
                gridAdapter.clearSelectedObstacleHighlight(selectedObstacleRow, selectedObstacleCol);
            }
            selectedObstacleRow = -1;
            selectedObstacleCol = -1;
        }

        // Reset selection states
        selectedObstacleRow = -1;
        selectedObstacleCol = -1;

        setupActionUI(action);

        // Update the grid table header text based on current state
        if (obstacleListener != null) {
            obstacleListener.updateGridTableHeader();
        }
        updateConfirmButtonState();
    }

    /**
     * Enter remove mode: show only the Clear All button and status; do not clear until user confirms.
     */
    public void enterRemoveMode() {
        currentObstacleAction = "remove";

        // Clear any temporary/previews/selection for a clean state
        clearTemporaryObstacle();
        clearPreviewBorderIfAny();
        if (selectedObstacleRow != -1 && selectedObstacleCol != -1 && gridAdapter != null) {
            gridAdapter.clearSelectedObstacleHighlight(selectedObstacleRow, selectedObstacleCol);
        }
        selectedObstacleRow = -1;
        selectedObstacleCol = -1;

        // Update UI: only show Clear All during remove mode
        if (borderDirectionSection != null) borderDirectionSection.setVisibility(View.GONE);
        if (confirmObstacleButton != null) confirmObstacleButton.setVisibility(View.GONE);
        if (cancelObstacleButton != null) cancelObstacleButton.setVisibility(View.GONE);
        if (clearAllObstaclesButton != null) clearAllObstaclesButton.setVisibility(View.VISIBLE);
        if (obstacleActionStatus != null) obstacleActionStatus.setText("Click Clear All to remove all obstacles");

        updateConfirmButtonState();
        if (obstacleListener != null) obstacleListener.updateGridTableHeader();
        showToast("Remove mode: tap Clear All to confirm");
    }

    private void setupActionUI(String action) {
        if ("add".equals(action)) {
            if (borderDirectionSection != null) borderDirectionSection.setVisibility(View.GONE);
            if (obstacleActionStatus != null) obstacleActionStatus.setText("Click on a grid cell to place obstacle");
            if (confirmObstacleButton != null) confirmObstacleButton.setVisibility(View.GONE);
            if (cancelObstacleButton != null) cancelObstacleButton.setVisibility(View.GONE);
            if (clearAllObstaclesButton != null) clearAllObstaclesButton.setVisibility(View.GONE);
            isDraggingObstacle = false;
            showToast("Add mode: Click on the grid to place an obstacle");
        } else if ("remove".equals(action)) {
            // Should be handled by enterRemoveMode(), but keep consistent state if called
            enterRemoveMode();
        } else {
            if (borderDirectionSection != null) borderDirectionSection.setVisibility(View.GONE);
            if (obstacleActionStatus != null) obstacleActionStatus.setText("Select an action above");
            if (confirmObstacleButton != null) confirmObstacleButton.setVisibility(View.GONE);
            if (cancelObstacleButton != null) cancelObstacleButton.setVisibility(View.GONE);
            if (clearAllObstaclesButton != null) clearAllObstaclesButton.setVisibility(View.GONE);
            isDraggingObstacle = false;
        }
    }

    public void handleObstacleClick(int row, int col) {
        if (!isObstacleModeEnabled || gridAdapter == null) return;

        if (currentObstacleAction.isEmpty()) {
            showToast("Please select Add first");
            return;
        }

        switch (currentObstacleAction) {
            case "add":
                handleAddObstacleClick(row, col);
                return;
            case "remove":
                showToast("Press Clear All to remove obstacles");
                return;
            default:
        }
    }

    private void handleAddObstacleClick(int row, int col) {
        if (isDraggingObstacle) {
            // Move the temporary obstacle
            if (gridAdapter.isCellObstacle(row, col) && !gridAdapter.isCellTemporaryObstacle(row, col)) {
                showToast("Cell already has a permanent obstacle");
                return;
            }

            // Clear previous temporary obstacle properly
            if (previousTemporaryRow != -1 && previousTemporaryCol != -1) {
                gridAdapter.beginBatchUpdates();
                try {
                    if (gridAdapter.isCellPermanentObstacle(previousTemporaryRow, previousTemporaryCol)) {
                        gridAdapter.setObstacle(previousTemporaryRow, previousTemporaryCol, true);
                    } else {
                        gridAdapter.updateCell(previousTemporaryRow, previousTemporaryCol, "", Color.parseColor("#E0E0E0"));
                    }
                } finally {
                    gridAdapter.endBatchUpdates();
                }
            }

            // Set new temporary position
            temporaryObstacleRow = row;
            temporaryObstacleCol = col;
            previousTemporaryRow = row;
            previousTemporaryCol = col;
            gridAdapter.beginBatchUpdates();
            try {
                gridAdapter.updateCell(row, col, "?", Color.parseColor("#FF9800"));
                gridAdapter.applyTempRowColHighlight(row, col);
            } finally {
                gridAdapter.endBatchUpdates();
            }
            updateObstacleCoordinate();

            if (obstacleListener != null) {
                obstacleListener.updateGridTableHeader();
            }

            // Removed per-move toast to keep dragging smooth
        } else {
            // Start dragging mode
            if (gridAdapter.isCellObstacle(row, col)) {
                showToast("Cell already has an obstacle");
                return;
            }

            temporaryObstacleRow = row;
            temporaryObstacleCol = col;
            previousTemporaryRow = row;
            previousTemporaryCol = col;
            isDraggingObstacle = true;

            // Begin low-latency continuous drag updates
            beginContinuousDragIfSupported();

            gridAdapter.beginBatchUpdates();
            try {
                gridAdapter.updateCell(row, col, "?", Color.parseColor("#FF9800"));
                gridAdapter.applyTempRowColHighlight(row, col);
            } finally {
                gridAdapter.endBatchUpdates();
            }
            updateObstacleCoordinate();

            showToast("Obstacle placed - Click another cell to move or Confirm to add");
        }

    }

    private void confirmObstacleAction() {
        if ("add".equals(currentObstacleAction)) {
            confirmAddObstacle();
        } else if ("remove".equals(currentObstacleAction)) {
            clearAllObstaclesAndResetUI();
        }
    }

    private void confirmAddObstacle() {
        if (!isDraggingObstacle || temporaryObstacleRow == -1 || temporaryObstacleCol == -1) {
            showToast("No obstacle selected to confirm");
            return;
        }

        int obstacleRow = temporaryObstacleRow;
        int obstacleCol = temporaryObstacleCol;
        int displayRow = gridAdapter.getGridSize() - 2 - obstacleRow;
        int displayCol = obstacleCol - 1;

        // Confirm adding the obstacle permanently
        gridAdapter.setObstacle(obstacleRow, obstacleCol, true);

        showToast("Obstacle confirmed at (" + displayRow + ", " + displayCol + ")");

        // Clear the temporary row/column highlight
        gridAdapter.clearTempRowColHighlight();

        // End continuous drag session
        endContinuousDragIfActive();

        // Clear the input fields when confirming obstacle placement
        if (tempObstacleXInput != null) {
            tempObstacleXInput.setText("");
        }
        if (tempObstacleYInput != null) {
            tempObstacleYInput.setText("");
        }

        // Reset the dragging state completely
        resetTemporaryObstacleState();

        // Exit Add action
        currentObstacleAction = "";
        if (obstacleActionStatus != null) {
            obstacleActionStatus.setText("Select an action above");
        }
        if (obstacleListener != null) {
            obstacleListener.updateGridTableHeader();
        }

        updateConfirmButtonState();
    }

    public void cancelObstacleAction() {
        endContinuousDragIfActive();
        clearTemporaryObstacle();
        clearPreviewBorderIfAny();

        // Clear selected obstacle yellow highlight if any
        if (selectedObstacleRow != -1 && selectedObstacleCol != -1) {
            gridAdapter.clearSelectedObstacleHighlight(selectedObstacleRow, selectedObstacleCol);
        }

        selectedObstacleRow = -1;
        selectedObstacleCol = -1;
        updateConfirmButtonState();
        if (obstacleActionStatus != null) {
            obstacleActionStatus.setText("Select an action above");
        }

        // Hide direction section when cancelling
        if (borderDirectionSection != null) {
            borderDirectionSection.setVisibility(View.GONE);
        }

        showToast("Obstacle action cancelled");
    }

    private void clearAllObstacles() {
        if (gridAdapter != null) {
            gridAdapter.clearAllObstacles();
            showToast("All obstacles cleared");
        }
    }

    private void updateObstacleCoordinate() {
        if (confirmObstacleButton != null) {
            confirmObstacleButton.setVisibility(View.VISIBLE);
        }
        if (cancelObstacleButton != null) {
            cancelObstacleButton.setVisibility(View.VISIBLE);
        }
        updateConfirmButtonState();
    }

    private void updateConfirmButtonState() {
        if (confirmObstacleButton != null) {
            boolean canConfirm;
            if ("add".equals(currentObstacleAction)) {
                canConfirm = isDraggingObstacle && temporaryObstacleRow != -1 && temporaryObstacleCol != -1;
            } else if ("remove".equals(currentObstacleAction)) {
                // Confirm button in remove mode is tied to Clear All
                canConfirm = false;
            } else {
                canConfirm = false;
            }
            confirmObstacleButton.setEnabled(canConfirm);
        }
    }

    private void clearTemporaryObstacle() {
        if (previousTemporaryRow != -1 && previousTemporaryCol != -1) {
            if (gridAdapter != null) {
                gridAdapter.beginBatchUpdates();
                try {
                    // Only clear border if this was actually a temporary obstacle, not a permanent one
                    if (gridAdapter.isCellTemporaryObstacle(previousTemporaryRow, previousTemporaryCol)) {
                        gridAdapter.clearCellBorder(previousTemporaryRow, previousTemporaryCol);
                    }
                    gridAdapter.clearTemporaryObstacle(previousTemporaryRow, previousTemporaryCol);
                } finally {
                    gridAdapter.endBatchUpdates();
                }
            }
            previousTemporaryRow = -1;
            previousTemporaryCol = -1;
        }
        if (gridAdapter != null) {
            gridAdapter.clearTempRowColHighlight();
        }

        endContinuousDragIfActive();
        resetTemporaryObstacleState();
        updateConfirmButtonState();
    }

    private void resetTemporaryObstacleState() {
        temporaryObstacleRow = -1;
        temporaryObstacleCol = -1;
        isDraggingObstacle = false;


        if (confirmObstacleButton != null) {
            confirmObstacleButton.setVisibility(View.GONE);
        }
        if (cancelObstacleButton != null) {
            cancelObstacleButton.setVisibility(View.GONE);
        }
    }

    private void clearPreviewBorderIfAny() {
        if (hasPreviewBorder && previewBorderRow != -1 && previewBorderCol != -1) {
            if (gridAdapter != null) {
                gridAdapter.clearCellBorder(previewBorderRow, previewBorderCol);
            }
            hasPreviewBorder = false;
            previewBorderRow = -1;
            previewBorderCol = -1;
        }
    }

    // Build and send obstacles JSON with mode "0"
    private void sendAllObstacles() {
        // Check if sending is enabled
        if (!isSendObstaclesEnabled) {
            showToast("Send Obstacles is disabled. Enable it first.");
            return;
        }

        if (gridAdapter == null) {
            showToast("Grid not ready");
            return;
        }
        if (bluetoothHelper == null || !bluetoothHelper.isConnected()) {
            showToast("Not connected");
            return;
        }
        try {
            JSONArray obstaclesArray = new JSONArray();
            int size = gridAdapter.getGridSize();
            for (int row = 0; row < size - 1; row++) { // data rows only
                for (int col = 1; col < size; col++) { // data cols only
                    if (gridAdapter.isCellPermanentObstacle(row, col) && !gridAdapter.isCellRobot(row, col)) {
                        int id = gridAdapter.getObstacleNumber(row, col);
                        if (id <= 0) continue; // skip if id unresolved
                        int x = col - 1; // display col
                        int y = (size - 2) - row; // display row
                        String dir = gridAdapter.getCellBorderDirection(row, col);
                        int d = mapDirectionToInt(dir); // -1 if not set
                        JSONObject obj = new JSONObject();
                        obj.put("x", x);
                        obj.put("y", y);
                        obj.put("id", id);
                        obj.put("d", d);
                        obstaclesArray.put(obj);
                    }
                }
            }
            JSONObject value = new JSONObject();
            value.put("obstacles", obstaclesArray);
            value.put("mode", "0"); // always string "0"
            JSONObject root = new JSONObject();
            root.put("cat", "obstacles");
            root.put("value", value);

            String payload = root.toString();
            bluetoothHelper.sendData(payload);

            if (obstacleListener != null) {
                String timestamp = obstacleListener.getCurrentTimestamp();
                String formattedMessage = "[" + timestamp + "] SENT: " + payload + "\n";
                obstacleListener.appendToReceivedData(formattedMessage);
                obstacleListener.appendToSentData(formattedMessage);
            }

            showToast("Sent obstacles (" + obstaclesArray.length() + ")");
        } catch (JSONException e) {
            showToast("Failed to build JSON");
        }
    }

    private int mapDirectionToInt(String dir) {
        if (dir == null) return 8; // SKIP when not set
        switch (dir.toUpperCase()) {
            case "N": return 0;
            case "E": return 2;
            case "S": return 4;
            case "W": return 6;
            default: return 8; // SKIP for unknown
        }
    }

    private void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    // Getters for state
    public boolean isObstacleModeEnabled() { return isObstacleModeEnabled; }
    public String getCurrentObstacleAction() { return currentObstacleAction; }
    public boolean isDraggingObstacle() { return isDraggingObstacle; }

    public int getTemporaryObstacleRow() { return temporaryObstacleRow; }
    public int getTemporaryObstacleCol() { return temporaryObstacleCol; }
    public int getPreviousTemporaryRow() { return previousTemporaryRow; }
    public int getPreviousTemporaryCol() { return previousTemporaryCol; }

    public void updateTemporaryPosition(int row, int col) {
        if (isDraggingObstacle && "add".equals(currentObstacleAction)) {
            // Ensure continuous drag loop is active
            beginContinuousDragIfSupported();
            // Update temporary obstacle position during drag
            if (row != previousTemporaryRow || col != previousTemporaryCol) {
                // Clear previous temp cell visuals and border
                gridAdapter.beginBatchUpdates();
                try {
                    // Only clear border if the previous position was actually a temporary obstacle
                    if (gridAdapter.isCellTemporaryObstacle(previousTemporaryRow, previousTemporaryCol)) {
                        gridAdapter.clearCellBorder(previousTemporaryRow, previousTemporaryCol);
                    }
                    gridAdapter.updateCell(previousTemporaryRow, previousTemporaryCol, "", Color.parseColor("#E0E0E0"));

                    // Update new temp position
                    temporaryObstacleRow = row;
                    temporaryObstacleCol = col;
                    previousTemporaryRow = row;
                    previousTemporaryCol = col;
                    gridAdapter.updateCell(row, col, "?", Color.parseColor("#FF9800"));
                    gridAdapter.applyTempRowColHighlight(row, col);
                } finally {
                    gridAdapter.endBatchUpdates();
                }
                updateObstacleCoordinate();

                if (obstacleListener != null) {
                    obstacleListener.updateGridTableHeader();
                }
            }
        }
    }

    // New public API: remove the temporary obstacle when dragged outside the map
    public void removeTemporaryObstacleFromGrid() {
        endContinuousDragIfActive();
        // Keep current action (e.g., Add) but clear temp visuals and state
        clearTemporaryObstacle();
        if ("add".equals(currentObstacleAction)) {
            if (obstacleActionStatus != null) {
                obstacleActionStatus.setText("Click on a grid cell to place obstacle");
            }
            if (obstacleListener != null) {
                obstacleListener.updateGridTableHeader();
            }
        }
    }

    // Consolidated helper to clear all obstacles and reset related UI/state
    private void clearAllObstaclesAndResetUI() {
        endContinuousDragIfActive();
        clearTemporaryObstacle();
        clearPreviewBorderIfAny();
        if (selectedObstacleRow != -1 && selectedObstacleCol != -1 && gridAdapter != null) {
            gridAdapter.clearSelectedObstacleHighlight(selectedObstacleRow, selectedObstacleCol);
        }
        selectedObstacleRow = -1;
        selectedObstacleCol = -1;
        currentObstacleAction = "";
        clearAllObstacles();
        if (obstacleActionStatus != null) {
            obstacleActionStatus.setText("Select an action above");
        }
        if (confirmObstacleButton != null) confirmObstacleButton.setVisibility(View.GONE);
        if (cancelObstacleButton != null) cancelObstacleButton.setVisibility(View.GONE);
        if (borderDirectionSection != null) borderDirectionSection.setVisibility(View.GONE);
        if (clearAllObstaclesButton != null) clearAllObstaclesButton.setVisibility(View.GONE);
        updateConfirmButtonState();
        if (obstacleListener != null) obstacleListener.updateGridTableHeader();
    }

    // Helpers to manage continuous drag lifecycle
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

    /**
     * Place a temporary obstacle at the specified coordinates from user input
     */
    private void placeTempObstacleByCoordinates() {
        if (tempObstacleXInput == null || tempObstacleYInput == null || tempObstacleStatusText == null || tempObstacleDirectionSpinner == null) {
            showToast("Input fields not available");
            return;
        }

        // Get coordinates from input fields
        String xText = tempObstacleXInput.getText().toString().trim();
        String yText = tempObstacleYInput.getText().toString().trim();

        if (xText.isEmpty() || yText.isEmpty()) {
            updateTempObstacleStatus("Please enter both X and Y coordinates", false);
            return;
        }

        try {
            int displayX = Integer.parseInt(xText);
            int displayY = Integer.parseInt(yText);

            // Validate coordinate ranges (X: 0-19, Y: 0-19)
            if (displayX < 0 || displayX > 19) {
                updateTempObstacleStatus("X coordinate must be between 0 and 19", false);
                return;
            }
            if (displayY < 0 || displayY > 19) {
                updateTempObstacleStatus("Y coordinate must be between 0 and 19", false);
                return;
            }

            // Convert display coordinates to grid coordinates
            int gridCol = displayX + 1; // X maps to column (0-19 -> 1-20)
            int gridRow = (gridAdapter.getGridSize() - 2) - displayY; // Y maps inversely to row (0-19 -> 19-0)

            // Check if the cell is already occupied
            if (gridAdapter.isCellObstacle(gridRow, gridCol) && !gridAdapter.isCellTemporaryObstacle(gridRow, gridCol)) {
                updateTempObstacleStatus("Cell (" + displayX + ", " + displayY + ") already has a permanent obstacle", false);
                return;
            }

            if (gridAdapter.isCellRobot(gridRow, gridCol)) {
                updateTempObstacleStatus("Cell (" + displayX + ", " + displayY + ") is occupied by robot", false);
                return;
            }

            // Get selected direction from spinner
            String selectedSpinnerDirection = getSelectedDirectionFromSpinner();

            // Clear any existing temporary obstacle
            clearTemporaryObstacle();

            // Set the temporary obstacle at the specified position
            temporaryObstacleRow = gridRow;
            temporaryObstacleCol = gridCol;
            previousTemporaryRow = gridRow;
            previousTemporaryCol = gridCol;
            isDraggingObstacle = true;

            // Set current action to add mode to enable confirmation
            currentObstacleAction = "add";

            // Update the grid with direction border if selected
            gridAdapter.beginBatchUpdates();
            try {
                gridAdapter.updateCell(gridRow, gridCol, "?", Color.parseColor("#FF9800"));
                gridAdapter.applyTempRowColHighlight(gridRow, gridCol);

                // Apply direction border if selected
                if (!selectedSpinnerDirection.isEmpty()) {
                    int borderColor = Color.parseColor("#4CAF50");
                    gridAdapter.highlightCellBorder(gridRow, gridCol, borderColor, selectedSpinnerDirection);
                }
            } finally {
                gridAdapter.endBatchUpdates();
            }

            // Update UI state
            updateObstacleCoordinate();
            if (obstacleListener != null) {
                obstacleListener.updateGridTableHeader();
            }

            // Don't clear input fields here - keep X and Y values for user convenience

            String directionText = !selectedSpinnerDirection.isEmpty() ?
                " with " + getDirectionName(selectedSpinnerDirection) + " direction" : "";
            updateTempObstacleStatus("Temporary obstacle placed at (" + displayX + ", " + displayY + ")" + directionText + ". Click Confirm to make permanent.", true);
            showToast("Temporary obstacle placed at (" + displayX + ", " + displayY + ")" + directionText);

        } catch (NumberFormatException e) {
            updateTempObstacleStatus("Please enter valid numbers for coordinates", false);
        }
    }

    /**
     * Get the selected direction from the spinner and convert to internal format
     */
    private String getSelectedDirectionFromSpinner() {
        if (tempObstacleDirectionSpinner == null) {
            return "";
        }

        String selectedItem = tempObstacleDirectionSpinner.getSelectedItem().toString();
        switch (selectedItem.toLowerCase()) {
            case "up":
                return "N";
            case "down":
                return "S";
            case "left":
                return "W";
            case "right":
                return "E";
            default:
                return "";
        }
    }

    /**
     * Get the display name for a direction
     */
    private String getDirectionName(String direction) {
        switch (direction.toUpperCase()) {
            case "N": return "North";
            case "S": return "South";
            case "E": return "East";
            case "W": return "West";
            default: return direction;
        }
    }

    /**
     * Update the status text for temporary obstacle input
     */
    private void updateTempObstacleStatus(String message, boolean isSuccess) {
        if (tempObstacleStatusText != null) {
            tempObstacleStatusText.setText(message);
            // Change text color based on success/error
            if (isSuccess) {
                tempObstacleStatusText.setTextColor(Color.parseColor("#4CAF50")); // Green for success
            } else {
                tempObstacleStatusText.setTextColor(Color.parseColor("#F44336")); // Red for error
            }
        }
    }

    /**
     * Toggle the state of sending obstacles
     */
    private void toggleSendObstacles() {
        isSendObstaclesEnabled = !isSendObstaclesEnabled;
        updateSendObstaclesButtonState();

        String statusMessage = isSendObstaclesEnabled ? "Send Obstacles: ON" : "Send Obstacles: OFF";
        showToast(statusMessage);
    }

    /**
     * Update the visual state of the send obstacles button and toggle button
     */
    private void updateSendObstaclesButtonState() {
        if (toggleSendObstaclesButton != null) {
            int color = isSendObstaclesEnabled ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336");
            toggleSendObstaclesButton.setBackgroundColor(color);
            toggleSendObstaclesButton.setText(isSendObstaclesEnabled ? "Enabled" : "Disabled");
        }

        if (sendObstaclesButton != null) {
            sendObstaclesButton.setEnabled(isSendObstaclesEnabled);
            // Apply blur effect when disabled
            sendObstaclesButton.setAlpha(isSendObstaclesEnabled ? 1.0f : 0.5f);
        }
    }
}
