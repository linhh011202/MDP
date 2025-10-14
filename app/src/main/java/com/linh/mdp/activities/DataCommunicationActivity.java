package com.linh.mdp.activities;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.linh.mdp.R;
import com.linh.mdp.adapters.GridTableAdapter;
import com.linh.mdp.bluetooth.BluetoothConnectionListener;
import com.linh.mdp.bluetooth.BluetoothHelper;
import com.linh.mdp.controllers.FabMenuController;
import com.linh.mdp.controllers.ObstacleController;
import com.linh.mdp.controllers.RobotController;
import com.linh.mdp.managers.DataCommunicationManager;
import com.linh.mdp.parsers.MessageParser;
import com.linh.mdp.utils.BluetoothConstants;

import java.util.Arrays;

public class DataCommunicationActivity extends AppCompatActivity implements
    BluetoothConnectionListener,
    FabMenuController.OnFabMenuListener,
    ObstacleController.OnObstacleModeChangeListener,
    MessageParser.OnMessageParsedListener {

    private static final String TAG = "DataCommunicationActivity";

    // Shared Bluetooth instance
    private static BluetoothHelper sharedBluetoothHelper;
    private BluetoothHelper bluetoothHelper;

    // UI Components
    private TextView gridTableHeaderText;
    private GridView gridView;
    private GridTableAdapter gridAdapter;

    // Controllers and Managers
    private DataCommunicationManager dataCommunicationManager;
    private FabMenuController fabMenuController;
    private RobotController robotController;
    private ObstacleController obstacleController;
    private MessageParser messageParser;

    // Current device name
    private String connectedDeviceName = "Unknown Device";

    // Reconnect grace period management (60s)
    private final Handler disconnectHandler = new Handler(Looper.getMainLooper());
    private Runnable delayedFinishRunnable;
    private boolean isWaitingForReconnect = false;
    private boolean manualDisconnectInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_communication);

        // Get device name from intent
        connectedDeviceName = getIntent().getStringExtra(BluetoothConstants.EXTRA_DEVICE_NAME);
        if (connectedDeviceName == null) {
            connectedDeviceName = "Unknown Device";
        }

        initializeControllers();
        initializeViews();
        setupBluetooth();
        setupBackPressedHandler();
        setupGridTable();
        setupUIComponents();
    }

    private void initializeControllers() {
        // Initialize controllers and managers
        dataCommunicationManager = new DataCommunicationManager(this);
        fabMenuController = new FabMenuController(this);
        robotController = new RobotController(this);
        obstacleController = new ObstacleController(this);
    }

    private void setupBackPressedHandler() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                disconnect();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void initializeViews() {
        gridTableHeaderText = findViewById(R.id.gridTableHeaderText);
        gridView = findViewById(R.id.gridView);

        Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(v -> disconnect());
    }

    private void setupUIComponents() {
        // Setup Data Communication Manager
        dataCommunicationManager.setUIComponents(
            findViewById(R.id.connectionStatusText),
            findViewById(R.id.receivedDataText),
            findViewById(R.id.sentDataText),
            findViewById(R.id.messageCountText),
            findViewById(R.id.messageInput),
            findViewById(R.id.sendButton),
            findViewById(R.id.clearButton),
            findViewById(R.id.quickSend1),
            findViewById(R.id.quickSend2),
            findViewById(R.id.quickSend3)
        );

        // Setup FAB Menu Controller
        fabMenuController.initialize(this);
        fabMenuController.setUIComponents(
            findViewById(R.id.fabMain),
            findViewById(R.id.fabSend),
            findViewById(R.id.fabReceive),
            findViewById(R.id.fabObstacle),
            findViewById(R.id.fabRobot),
            findViewById(R.id.fabSendLabel),
            findViewById(R.id.fabReceiveLabel),
            findViewById(R.id.fabObstacleLabel),
            findViewById(R.id.fabRobotLabel),
            findViewById(R.id.sendDataSection),
            findViewById(R.id.receiveDataSection),
            findViewById(R.id.obstacleControlSection),
            findViewById(R.id.robotControlSection)
        );

        // Setup Robot Controller
        robotController.setUIComponents(
            findViewById(R.id.placeRobotButton),
            findViewById(R.id.robotConfirmButton),
            findViewById(R.id.robotUpButton),
            findViewById(R.id.robotDownButton),
            findViewById(R.id.robotTurnLeftButton),
            findViewById(R.id.robotTurnRightButton),
            findViewById(R.id.robotStatusText),
            findViewById(R.id.robotCenterXInput),
            findViewById(R.id.robotCenterYInput),
            findViewById(R.id.placeRobotByCoordButton),
            findViewById(R.id.robotPositionStatusText)
        );

        // Setup Obstacle Controller
        obstacleController.initialize(gridAdapter, bluetoothHelper, this);
        obstacleController.setUIComponents(
            findViewById(R.id.clearAllObstaclesButton),
            findViewById(R.id.confirmObstacleButton),
            findViewById(R.id.cancelObstacleButton),
            findViewById(R.id.obstacleActionStatus),
            findViewById(R.id.borderDirectionSection),
            findViewById(R.id.sendObstaclesButton),
            findViewById(R.id.toggleSendObstaclesButton),
            findViewById(R.id.tempObstacleXInput),
            findViewById(R.id.tempObstacleYInput),
            findViewById(R.id.placeTempObstacleButton),
            findViewById(R.id.tempObstacleStatusText),
            findViewById(R.id.tempObstacleDirectionSpinner)
        );

        // Wire the dedicated Remove Obstacle button to enter remove mode (reveals Clear All)
        Button removeObstacleButton = findViewById(R.id.removeObstacleButton);
        if (removeObstacleButton != null) {
            removeObstacleButton.setOnClickListener(v -> obstacleController.enterRemoveMode());
        }
    }

    private void setupGridTable() {
        // Initialize the 21x21 grid adapter
        gridAdapter = new GridTableAdapter(this);
        gridView.setAdapter(gridAdapter);

        // Initialize controllers that depend on gridAdapter
        robotController.initialize(gridAdapter, bluetoothHelper);
        messageParser = new MessageParser(gridAdapter, this);

        // Set up grid item click listener
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            int row = position / gridAdapter.getGridSize();
            int col = position % gridAdapter.getGridSize();

            // Only allow editing data cells (col 1-20, row 0-19)
            if (col > 0 && row >= 0 && row < gridAdapter.getGridSize() - 1) {
                // Handle robot placement first
                if (robotController.handleRobotPlacementClick(row, col)) {
                    return; // Click was handled by robot controller
                }

                // Handle obstacle operations
                obstacleController.handleObstacleClick(row, col);// Click was handled by obstacle controller
            }
        });

        // Set up touch listener for dragging
        gridView.setOnTouchListener(this::handleGridTouch);

        Log.d(TAG, "21x21 Grid table initialized successfully");
    }

    private boolean handleGridTouch(View v, MotionEvent event) {
        // If placing the robot, handle drag-to-preview for robot placement
        if (robotController.isPlacingRobotMode()) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            int position = gridView.pointToPosition(x, y);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    preventParentIntercept(v, true);
                    if (position != GridView.INVALID_POSITION) {
                        int row = position / gridAdapter.getGridSize();
                        int col = position % gridAdapter.getGridSize();
                        if (col > 0 && row >= 0 && row < gridAdapter.getGridSize() - 1) {
                            robotController.previewRobotAt(row, col);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    preventParentIntercept(v, false);
                    v.performClick();
                    return true;
            }
            return true;
        }

        // Compute touched position within GridView
        int x = (int) event.getX();
        int y = (int) event.getY();
        int position = gridView.pointToPosition(x, y);

        // Handle dragging logic
        return handleDragLogic(v, event, position);
    }

    private boolean handleDragLogic(View v, MotionEvent event, int position) {
        // Handle dragging of the temporary '?' obstacle when in add mode
        if (obstacleController.isDraggingObstacle() &&
            "add".equals(obstacleController.getCurrentObstacleAction())) {
            return handleTemporaryObstacleDrag(v, event, position);
        }

        // Handle permanent obstacle dragging
        return handlePermanentObstacleDrag();
    }

    private boolean handleTemporaryObstacleDrag(View v, MotionEvent event, int position) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                preventParentIntercept(v, true);
                return true;

            case MotionEvent.ACTION_MOVE:
                preventParentIntercept(v, true);
                float x = event.getX();
                float y = event.getY();
                if (isPointerOutsideGrid(x, y)) {
                    // Only remove when actually outside the GridView bounds
                    obstacleController.removeTemporaryObstacleFromGrid();
                    return true;
                }
                // Inside GridView bounds: ignore transient invalid positions
                if (position == GridView.INVALID_POSITION) {
                    return true;
                }
                int row = position / gridAdapter.getGridSize();
                int col = position % gridAdapter.getGridSize();
                // If on header cells, ignore movement but do not remove
                if (!(col > 0 && row >= 0 && row < gridAdapter.getGridSize() - 1)) {
                    return true;
                }
                // Can't move onto a permanent obstacle or robot (allow temp)
                if (!gridAdapter.isCellObstacle(row, col) || gridAdapter.isCellTemporaryObstacle(row, col)) {
                    if (row != obstacleController.getPreviousTemporaryRow() ||
                        col != obstacleController.getPreviousTemporaryCol()) {
                        obstacleController.updateTemporaryPosition(row, col);
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                preventParentIntercept(v, false);
                v.performClick();
                return true;
        }
        return false;
    }

    // Determine if pointer is outside the GridView's visual bounds
    private boolean isPointerOutsideGrid(float x, float y) {
        int left = gridView.getPaddingLeft();
        int top = gridView.getPaddingTop();
        int right = gridView.getWidth() - gridView.getPaddingRight();
        int bottom = gridView.getHeight() - gridView.getPaddingBottom();
        return (x < left || x >= right || y < top || y >= bottom);
    }

    private boolean handlePermanentObstacleDrag() {
        // Individual obstacle removal has been removed - only clear all obstacles is available
        // This method is kept for compatibility but does nothing
        return false;
    }

    private void preventParentIntercept(View v, boolean prevent) {
        ViewParent parent = v.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(prevent);
        }
    }

    private void setupBluetooth() {
        // Use the shared BluetoothHelper instance from BluetoothActivity
        if (sharedBluetoothHelper != null) {
            bluetoothHelper = sharedBluetoothHelper;
            bluetoothHelper.setConnectionListener(this);
            Log.d(TAG, "Using shared BluetoothHelper instance");
        } else {
            // Fallback: create new instance (shouldn't happen in normal flow)
            bluetoothHelper = new BluetoothHelper(this);
            bluetoothHelper.setConnectionListener(this);
            Log.w(TAG, "Created new BluetoothHelper instance as fallback");
        }

        // Initialize components that depend on bluetoothHelper
        dataCommunicationManager.initialize(bluetoothHelper, connectedDeviceName);

        // Check if we still have an active connection
        if (!bluetoothHelper.isConnected()) {
            Toast.makeText(this, "Connection lost. Returning to scan.", Toast.LENGTH_LONG).show();
            finish();
        } else {
            Log.d(TAG, "Bluetooth connection is active");
        }
    }

    private void disconnect() {
        manualDisconnectInProgress = true;
        if (bluetoothHelper != null) {
            bluetoothHelper.disconnect();
            // Clear the shared instance to ensure clean reconnection
            sharedBluetoothHelper = null;
        }
        // Finish immediately on manual disconnect
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        finish();
    }

    // BluetoothConnectionListener implementation
    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        // Cancel any pending delayed finish if we were waiting for reconnection
        if (isWaitingForReconnect) {
            isWaitingForReconnect = false;
            if (delayedFinishRunnable != null) {
                disconnectHandler.removeCallbacks(delayedFinishRunnable);
                delayedFinishRunnable = null;
            }
            Toast.makeText(this, "Reconnected", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDeviceDisconnected() {
        // If this is a manual disconnect flow, we've already finished
        if (manualDisconnectInProgress) return;

        runOnUiThread(() -> {
            // Start a 60-second grace period to allow auto-reconnect
            if (!isWaitingForReconnect) {
                isWaitingForReconnect = true;
                Toast.makeText(this, "Device disconnected. Waiting 1 hour for reconnection...", Toast.LENGTH_LONG).show();
                delayedFinishRunnable = () -> {
                    // If still disconnected after 60s, leave this screen
                    if (bluetoothHelper == null || !bluetoothHelper.isConnected()) {
                        Toast.makeText(this, "No reconnection. Returning to scan.", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        // Reconnected within grace period; stay on screen
                        isWaitingForReconnect = false;
                    }
                };
                disconnectHandler.postDelayed(delayedFinishRunnable, 60_000);
            }
        });
    }

    @Override
    public void onDataReceived(String data) {
        runOnUiThread(() -> {
            // Notify data communication manager
            dataCommunicationManager.onDataReceived();

            // Parse and handle different types of messages
            if (messageParser != null) {
                String[] parts = Arrays.stream(data.split("\\n"))
                        .map(String::trim)
                        .filter(str -> !str.isEmpty())
                        .toArray(String[]::new);
                for (String part : parts) {
                    messageParser.parseAndHandleRobotCommands(part);
                    messageParser.parseAndHandleTargetMessages(part);
                    messageParser.parseAndHandleRobotMessages(part);

                    // Only display selective/important messages in the UI
                    if (messageParser.shouldDisplayMessage(part)) {
                        dataCommunicationManager.addImportantMessage(part);
                    } else {
                        // Log filtered out messages for debugging
                        Log.d(TAG, "Filtered out message: " + part);
                    }
                }
            }
        });
    }

    @Override
    public void onConnectionFailed(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Connection failed: " + error, Toast.LENGTH_LONG).show();
            finish();
        });
    }

    @Override
    public void onDeviceDiscovered(BluetoothDevice device) {
        // Not used in this activity
    }

    @Override
    public void onWaitingForConnection(BluetoothDevice device) {
        // Not used in this activity
    }

    @Override
    public void onConnectionTimeout() {
        // Not used in this activity
    }

    // FabMenuController.OnFabMenuListener implementation
    @Override
    public void onSendDataSectionSelected() {
        clearTemporaryObstacle();
        exitRobotMode();
        fabMenuController.showSendDataSection();
    }

    @Override
    public void onReceiveDataSectionSelected() {
        clearTemporaryObstacle();
        exitRobotMode();
        fabMenuController.showReceiveDataSection();
    }

    @Override
    public void onObstacleModeToggled() {
        exitRobotMode();
        obstacleController.toggleObstacleMode();
    }

    @Override
    public void onRobotPanelToggled() {
        if (fabMenuController.isRobotSectionVisible()) {
            fabMenuController.hideRobotSection();
        } else {
            clearTemporaryObstacle();
            fabMenuController.showRobotSection();
        }
    }

    // ObstacleController.OnObstacleModeChangeListener implementation
    @Override
    public void onObstacleModeChanged(boolean enabled) {
        fabMenuController.updateObstacleButtonState();
        if (enabled) {
            // Exit robot placement mode to avoid conflicts
            exitRobotMode();
            fabMenuController.showObstacleSection();
        }
    }

    // MessageParser.OnMessageParsedListener implementation
    @Override
    public void onRobotCommandParsed(String command) {
        // Handle robot command if needed
    }

    @Override
    public void onTargetMessageParsed(int obstacleNumber, String targetId) {
        // Handle target message if needed
    }

    @Override
    public void onRobotPositionParsed(int x, int y, String direction) {
        // Handle robot position update if needed
    }

    @Override
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public String getCurrentTimestamp() {
        return dataCommunicationManager.getCurrentTimestamp();
    }

    @Override
    public void appendToReceivedData(String message) {
        dataCommunicationManager.appendToReceivedData(message);
    }

    @Override
    public void appendToSentData(String message) {
        dataCommunicationManager.appendToSentData(message);
    }

    @Override
    public void updateRobotStatusText() {
        robotController.updateRobotStatusText();
    }

    @Override
    public void updateGridTableHeader() {
        updateGridTableHeaderText();
    }

    private void updateGridTableHeaderText() {
        if (gridTableHeaderText != null) {
            if (obstacleController.isDraggingObstacle() &&
                obstacleController.getTemporaryObstacleRow() != -1 &&
                obstacleController.getTemporaryObstacleCol() != -1) {
                int displayRow = gridAdapter.getGridSize() - 2 - obstacleController.getTemporaryObstacleRow();
                int displayCol = obstacleController.getTemporaryObstacleCol() - 1;
                gridTableHeaderText.setText("Placing Obstacle at (" + displayRow + ", " + displayCol + "):");
            } else if ("add".equals(obstacleController.getCurrentObstacleAction()) &&
                      obstacleController.isObstacleModeEnabled()) {
                gridTableHeaderText.setText("Add Obstacle Mode - Click on grid:");
            } else if (obstacleController.isObstacleModeEnabled()) {
                gridTableHeaderText.setText("Obstacle Mode Active (Index 0-19):");
            } else {
                gridTableHeaderText.setText("");
            }
        }
    }

    private void clearTemporaryObstacle() {
        // Cancel any active obstacle action and clear temporary obstacle/previews
        if (obstacleController != null) {
            obstacleController.cancelObstacleAction();
        }
        // Refresh header to reflect cleared state
        updateGridTableHeaderText();
    }

    private void exitRobotMode() {
        if (robotController.isPlacingRobotMode()) {
            robotController.exitPlacementMode();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancel any pending delayed finish to avoid leaks
        if (delayedFinishRunnable != null) {
            disconnectHandler.removeCallbacks(delayedFinishRunnable);
            delayedFinishRunnable = null;
        }
        if (bluetoothHelper != null) {
            bluetoothHelper.cleanup();
            // Clear the shared instance when activity is destroyed
            sharedBluetoothHelper = null;
        }
    }

    public static void setBluetoothHelper(BluetoothHelper helper) {
        sharedBluetoothHelper = helper;
    }
}
