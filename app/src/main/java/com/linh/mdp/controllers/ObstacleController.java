package com.linh.mdp.controllers;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
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
    private Button addObstacleButton;
    private Button clearAllObstaclesButton;
    private Button confirmObstacleButton;
    private Button cancelObstacleButton;
    private Button setDirectionButton;
    private Button northBorderButton;
    private Button southBorderButton;
    private Button eastBorderButton;
    private Button westBorderButton;
    private Button sendObstaclesButton; // new send obstacles button
    private TextView obstacleActionStatus;
    private View borderDirectionSection;

    // State variables
    private boolean isObstacleModeEnabled = false;
    private String currentObstacleAction = "";
    private String selectedDirection = "";
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

    public void setUIComponents(Button addObstacleButton, Button clearAllObstaclesButton,
                               Button confirmObstacleButton, Button cancelObstacleButton, Button setDirectionButton,
                               Button northBorderButton, Button southBorderButton, Button eastBorderButton, Button westBorderButton,
                               TextView obstacleActionStatus, View borderDirectionSection,
                               Button sendObstaclesButton) { // added param
        this.addObstacleButton = addObstacleButton;
        this.clearAllObstaclesButton = clearAllObstaclesButton;
        this.confirmObstacleButton = confirmObstacleButton;
        this.cancelObstacleButton = cancelObstacleButton;
        this.setDirectionButton = setDirectionButton;
        this.northBorderButton = northBorderButton;
        this.southBorderButton = southBorderButton;
        this.eastBorderButton = eastBorderButton;
        this.westBorderButton = westBorderButton;
        this.obstacleActionStatus = obstacleActionStatus;
        this.borderDirectionSection = borderDirectionSection;
        this.sendObstaclesButton = sendObstaclesButton;

        setupClickListeners();
    }

    private void setupClickListeners() {
        // Action buttons
        if (addObstacleButton != null) {
            addObstacleButton.setOnClickListener(v -> setCurrentObstacleAction("add"));
        }
        if (setDirectionButton != null) {
            setDirectionButton.setOnClickListener(v -> setCurrentObstacleAction("direction"));
        }
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

        // Direction pickers
        if (northBorderButton != null) {
            northBorderButton.setOnClickListener(v -> setBorder("N"));
        }
        if (southBorderButton != null) {
            southBorderButton.setOnClickListener(v -> setBorder("S"));
        }
        if (eastBorderButton != null) {
            eastBorderButton.setOnClickListener(v -> setBorder("E"));
        }
        if (westBorderButton != null) {
            westBorderButton.setOnClickListener(v -> setBorder("W"));
        }
        if (sendObstaclesButton != null) {
            sendObstaclesButton.setOnClickListener(v -> sendAllObstacles());
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

        // If switching away from direction mode, clear any selected obstacle highlight
        if (!"direction".equals(action) && selectedObstacleRow != -1 && selectedObstacleCol != -1) {
            if (gridAdapter != null) {
                gridAdapter.clearSelectedObstacleHighlight(selectedObstacleRow, selectedObstacleCol);
            }
            selectedObstacleRow = -1;
            selectedObstacleCol = -1;
        }

        // Reset direction selection states
        selectedDirection = "";
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
        selectedDirection = "";

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
        } else if ("direction".equals(action)) {
            if (borderDirectionSection != null) borderDirectionSection.setVisibility(View.VISIBLE);
            if (obstacleActionStatus != null) obstacleActionStatus.setText("Click an obstacle, then choose a direction and Confirm");
            if (confirmObstacleButton != null) confirmObstacleButton.setVisibility(View.GONE);
            if (cancelObstacleButton != null) cancelObstacleButton.setVisibility(View.GONE);
            if (clearAllObstaclesButton != null) clearAllObstaclesButton.setVisibility(View.GONE);
            isDraggingObstacle = false;
            showToast("Direction mode: Select an obstacle to set its direction");
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
            showToast("Please select Add or Set Direction first");
            return;
        }

        switch (currentObstacleAction) {
            case "add":
                handleAddObstacleClick(row, col);
                return;
            case "direction":
                handleDirectionObstacleClick(row, col);
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
                    if (selectedDirection != null && !selectedDirection.isEmpty()) {
                        gridAdapter.clearCellBorder(previousTemporaryRow, previousTemporaryCol);
                    }
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
                if (selectedDirection != null && !selectedDirection.isEmpty()) {
                    int borderColor = Color.parseColor("#4CAF50");
                    gridAdapter.highlightCellBorder(row, col, borderColor, selectedDirection);
                }
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

            selectedDirection = "";

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

    private void handleDirectionObstacleClick(int row, int col) {
        if (gridAdapter.isCellObstacle(row, col) && !gridAdapter.isCellRobot(row, col) && !gridAdapter.isCellTemporaryObstacle(row, col)) {
            // If there is an unconfirmed preview on another cell, clear it
            if (hasPreviewBorder && (previewBorderRow != row || previewBorderCol != col)) {
                gridAdapter.clearCellBorder(previewBorderRow, previewBorderCol);
                hasPreviewBorder = false;
                previewBorderRow = -1;
                previewBorderCol = -1;
            }

            // Clear previous selected obstacle yellow highlight if selecting a different one
            if (selectedObstacleRow != -1 && selectedObstacleCol != -1 && (selectedObstacleRow != row || selectedObstacleCol != col)) {
                gridAdapter.clearSelectedObstacleHighlight(selectedObstacleRow, selectedObstacleCol);
            }

            selectedObstacleRow = row;
            selectedObstacleCol = col;

            // Highlight this selected obstacle in yellow
            gridAdapter.highlightSelectedObstacle(row, col);

            // Show cancel/confirm; confirm enabled only when a direction picked
            if (cancelObstacleButton != null) {
                cancelObstacleButton.setVisibility(View.VISIBLE);
            }
            if (confirmObstacleButton != null) {
                confirmObstacleButton.setVisibility(View.VISIBLE);
            }
            updateConfirmButtonState();
        } else {
            showToast("Select a valid obstacle cell");
        }
    }

    private void confirmObstacleAction() {
        if ("add".equals(currentObstacleAction)) {
            confirmAddObstacle();
        } else if ("direction".equals(currentObstacleAction)) {
            confirmDirectionObstacle();
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

    private void confirmDirectionObstacle() {
        if (selectedObstacleRow == -1 || selectedObstacleCol == -1) {
            showToast("Select an obstacle first");
            return;
        }
        if (selectedDirection == null || selectedDirection.isEmpty()) {
            showToast("Select a direction");
            return;
        }

        int obstacleNumber = gridAdapter.getObstacleNumber(selectedObstacleRow, selectedObstacleCol);
        if (obstacleNumber <= 0) {
            showToast("Cannot determine obstacle ID");
            return;
        }

        // Keep the border as the committed direction; just clear preview state
        hasPreviewBorder = false;
        previewBorderRow = -1;
        previewBorderCol = -1;

        // Clear selected obstacle yellow highlight
        if (selectedObstacleRow != -1 && selectedObstacleCol != -1) {
            gridAdapter.clearSelectedObstacleHighlight(selectedObstacleRow, selectedObstacleCol);
        }

        selectedObstacleRow = -1;
        selectedObstacleCol = -1;
        selectedDirection = "";

        if (confirmObstacleButton != null) {
            confirmObstacleButton.setVisibility(View.GONE);
        }
        if (cancelObstacleButton != null) {
            cancelObstacleButton.setVisibility(View.GONE);
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
        selectedDirection = "";
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

    private void setBorder(String direction) {
        // In direction mode, apply border highlight to the selected obstacle
        if ("direction".equals(currentObstacleAction)) {
            if (selectedObstacleRow != -1 && selectedObstacleCol != -1) {
                // If a preview was on another cell, clear it first
                if (hasPreviewBorder && (previewBorderRow != selectedObstacleRow || previewBorderCol != selectedObstacleCol)) {
                    gridAdapter.clearCellBorder(previewBorderRow, previewBorderCol);
                    hasPreviewBorder = false;
                }

                // Clear existing border on the selected cell then set new one (preview)
                gridAdapter.beginBatchUpdates();
                try {
                    gridAdapter.clearCellBorder(selectedObstacleRow, selectedObstacleCol);
                    int borderColor = Color.parseColor("#4CAF50");
                    gridAdapter.highlightCellBorder(selectedObstacleRow, selectedObstacleCol, borderColor, direction);
                } finally {
                    gridAdapter.endBatchUpdates();
                }
                selectedDirection = direction;

                // Track preview state
                previewBorderRow = selectedObstacleRow;
                previewBorderCol = selectedObstacleCol;
                hasPreviewBorder = true;

                updateConfirmButtonState();
                String directionName = getDirectionName(direction);
                showToast(directionName + " selected for obstacle");
            } else {
                showToast("Select an obstacle first");
            }
            return;
        }

        // Existing behavior for add mode temporary obstacle
        if (isDraggingObstacle && temporaryObstacleRow != -1 && temporaryObstacleCol != -1) {
            gridAdapter.beginBatchUpdates();
            try {
                gridAdapter.clearCellBorder(temporaryObstacleRow, temporaryObstacleCol);
                int borderColor = Color.parseColor("#4CAF50");
                gridAdapter.highlightCellBorder(temporaryObstacleRow, temporaryObstacleCol, borderColor, direction);
            } finally {
                gridAdapter.endBatchUpdates();
            }
        }

        selectedDirection = direction;
        updateConfirmButtonState();

        String directionName = getDirectionName(direction);
        showToast(directionName + " border selected for obstacle");
    }

    private String getDirectionName(String direction) {
        switch (direction.toUpperCase()) {
            case "N": return "North";
            case "S": return "South";
            case "E": return "East";
            case "W": return "West";
            default: return direction;
        }
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
            } else if ("direction".equals(currentObstacleAction)) {
                canConfirm = (selectedObstacleRow != -1 && selectedObstacleCol != -1 &&
                             selectedDirection != null && !selectedDirection.isEmpty());
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
                    gridAdapter.clearCellBorder(previousTemporaryRow, previousTemporaryCol);
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
                    if (selectedDirection != null && !selectedDirection.isEmpty()) {
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
                    if (selectedDirection != null && !selectedDirection.isEmpty()) {
                        int borderColor = Color.parseColor("#4CAF50");
                        gridAdapter.highlightCellBorder(row, col, borderColor, selectedDirection);
                    }
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
        selectedDirection = "";
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
}
