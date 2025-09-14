package com.linh.mdp.managers;

import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.linh.mdp.bluetooth.BluetoothHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Manager class to handle data communication operations
 */
public class DataCommunicationManager {

    private final Context context;
    private BluetoothHelper bluetoothHelper;

    // UI components
    private TextView connectionStatusText;
    private TextView receivedDataText;
    private TextView sentDataText;
    private TextView messageCountText;
    private EditText messageInput;
    private Button sendButton;
    private Button clearButton;
    private Button quickSend1, quickSend2, quickSend3;

    // Data buffers
    private final StringBuilder receivedDataBuffer;
    private final StringBuilder sentDataBuffer;
    private int messageCount = 0;

    private String connectedDeviceName = "Unknown Device";

    public DataCommunicationManager(Context context) {
        this.context = context;
        this.receivedDataBuffer = new StringBuilder();
        this.sentDataBuffer = new StringBuilder();
    }

    public void initialize(BluetoothHelper bluetoothHelper, String deviceName) {
        this.bluetoothHelper = bluetoothHelper;
        this.connectedDeviceName = deviceName != null ? deviceName : "Unknown Device";
    }

    public void setUIComponents(TextView connectionStatusText, TextView receivedDataText,
                               TextView sentDataText, TextView messageCountText, EditText messageInput,
                               Button sendButton, Button clearButton, Button quickSend1,
                               Button quickSend2, Button quickSend3) {
        this.connectionStatusText = connectionStatusText;
        this.receivedDataText = receivedDataText;
        this.sentDataText = sentDataText;
        this.messageCountText = messageCountText;
        this.messageInput = messageInput;
        this.sendButton = sendButton;
        this.clearButton = clearButton;
        this.quickSend1 = quickSend1;
        this.quickSend2 = quickSend2;
        this.quickSend3 = quickSend3;

        setupInitialStatus();
        setupClickListeners();
    }

    private void setupInitialStatus() {
        if (connectionStatusText != null) {
            connectionStatusText.setText("Connected to " + connectedDeviceName);
        }
        updateMessageCount();
    }

    private void setupClickListeners() {
        if (sendButton != null) {
            sendButton.setOnClickListener(v -> sendMessage());
        }

        if (clearButton != null) {
            clearButton.setOnClickListener(v -> clearReceivedData());
        }

        if (quickSend1 != null) {
            quickSend1.setOnClickListener(v -> sendQuickMessage("Hello"));
        }

        if (quickSend2 != null) {
            quickSend2.setOnClickListener(v -> sendQuickMessage("OK"));
        }

        if (quickSend3 != null) {
            quickSend3.setOnClickListener(v -> sendQuickMessage("Test"));
        }
    }

    public void sendMessage() {
        if (messageInput == null) return;

        String message = messageInput.getText().toString().trim();
        if (!message.isEmpty()) {
            if (bluetoothHelper != null && bluetoothHelper.isConnected()) {
                bluetoothHelper.sendData(message);
                messageInput.setText("");

                // Add sent message to display with timestamp
                String timestamp = getCurrentTimestamp();
                String formattedMessage = "[" + timestamp + "] SENT: " + message + "\n";
                appendToReceivedData(formattedMessage);
                appendToSentData(formattedMessage);

                showToast("Message sent");
            } else {
                showToast("Connection lost!");
            }
        } else {
            showToast("Enter a message to send");
        }
    }

    public void sendQuickMessage(String message) {
        if (bluetoothHelper != null && bluetoothHelper.isConnected()) {
            bluetoothHelper.sendData(message);

            // Add sent message to display with timestamp
            String timestamp = getCurrentTimestamp();
            String formattedMessage = "[" + timestamp + "] SENT: " + message + "\n";
            appendToReceivedData(formattedMessage);
            appendToSentData(formattedMessage);

            showToast("Sent: " + message);
        } else {
            showToast("Connection lost!");
        }
    }

    public void clearReceivedData() {
        receivedDataBuffer.setLength(0);
        if (receivedDataText != null) {
            receivedDataText.setText("No messages received yet...");
        }
        messageCount = 0;
        updateMessageCount();
        showToast("Messages cleared");
    }

    public void onDataReceived(String data) {
        // Always increment message count for all received data
        messageCount++;
        updateMessageCount();
    }

    public void addImportantMessage(String data) {
        String timestamp = getCurrentTimestamp();
        String formattedMessage = "[" + timestamp + "] RECEIVED: " + data + "\n";
        appendToReceivedData(formattedMessage);

        // Auto-scroll to bottom (guard against null layout)
        if (receivedDataText != null) {
            receivedDataText.post(() -> {
                if (receivedDataText.getLayout() != null) {
                    int scrollY = receivedDataText.getLayout().getLineTop(receivedDataText.getLineCount()) - receivedDataText.getHeight();
                    if (scrollY > 0) {
                        receivedDataText.scrollTo(0, scrollY);
                    }
                }
            });
        }

        showToast("Important data received");
    }

    public void appendToReceivedData(String message) {
        receivedDataBuffer.append(message);
        if (receivedDataText != null) {
            receivedDataText.setText(receivedDataBuffer.toString());
        }
    }

    public void appendToSentData(String message) {
        sentDataBuffer.append(message);
        if (sentDataText != null) {
            sentDataText.setText(sentDataBuffer.toString());
        }
    }

    private void updateMessageCount() {
        if (messageCountText != null) {
            messageCountText.setText("Messages: " + messageCount);
        }
    }

    public String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void resetMessageCount() {
        messageCount = 0;
        updateMessageCount();
    }

    public void setConnectedDeviceName(String deviceName) {
        this.connectedDeviceName = deviceName != null ? deviceName : "Unknown Device";
        if (connectionStatusText != null) {
            connectionStatusText.setText("Connected to " + connectedDeviceName);
        }
    }

    private void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public StringBuilder getReceivedDataBuffer() {
        return receivedDataBuffer;
    }

    public StringBuilder getSentDataBuffer() {
        return sentDataBuffer;
    }
}
