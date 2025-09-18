package com.linh.mdp.controllers;

import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * Controller class to handle FAB menu operations
 */
public class FabMenuController {

    private final android.content.Context context;

    // FAB menu components
    private FloatingActionButton fabMain, fabSend, fabReceive, fabObstacle, fabRobot;
    private TextView fabSendLabel, fabReceiveLabel, fabObstacleLabel, fabRobotLabel;
    private ScrollView sendDataSection, receiveDataSection, obstacleControlSection, robotControlSection;
    private boolean isFabMenuOpen = false;

    private OnFabMenuListener listener;

    public interface OnFabMenuListener {
        void onSendDataSectionSelected();
        void onReceiveDataSectionSelected();
        void onObstacleModeToggled();
        void onRobotPanelToggled();
    }

    public FabMenuController(android.content.Context context) {
        this.context = context;
    }

    public void initialize(OnFabMenuListener listener) {
        this.listener = listener;
    }

    public void setUIComponents(FloatingActionButton fabMain, FloatingActionButton fabSend,
                               FloatingActionButton fabReceive, FloatingActionButton fabObstacle,
                               FloatingActionButton fabRobot, TextView fabSendLabel,
                               TextView fabReceiveLabel, TextView fabObstacleLabel,
                               TextView fabRobotLabel, ScrollView sendDataSection,
                               ScrollView receiveDataSection, ScrollView obstacleControlSection,
                               ScrollView robotControlSection) {
        this.fabMain = fabMain;
        this.fabSend = fabSend;
        this.fabReceive = fabReceive;
        this.fabObstacle = fabObstacle;
        this.fabRobot = fabRobot;
        this.fabSendLabel = fabSendLabel;
        this.fabReceiveLabel = fabReceiveLabel;
        this.fabObstacleLabel = fabObstacleLabel;
        this.fabRobotLabel = fabRobotLabel;
        this.sendDataSection = sendDataSection;
        this.receiveDataSection = receiveDataSection;
        this.obstacleControlSection = obstacleControlSection;
        this.robotControlSection = robotControlSection;

        setupClickListeners();
    }

    private void setupClickListeners() {
        if (fabMain != null) {
            fabMain.setOnClickListener(v -> toggleFabMenu());
        }

        if (fabSend != null) {
            fabSend.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSendDataSectionSelected();
                }
                closeFabMenu();
            });
        }

        if (fabReceive != null) {
            fabReceive.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReceiveDataSectionSelected();
                }
                closeFabMenu();
            });
        }

        if (fabObstacle != null) {
            fabObstacle.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onObstacleModeToggled();
                }
                closeFabMenu();
            });
        }

        if (fabRobot != null) {
            fabRobot.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRobotPanelToggled();
                }
                closeFabMenu();
            });
        }
    }

    public void toggleFabMenu() {
        if (isFabMenuOpen) {
            closeFabMenu();
        } else {
            openFabMenu();
        }
    }

    public void openFabMenu() {
        isFabMenuOpen = true;

        // Show sub FABs and labels with animation
        setFabVisibility();
        animateFabsIn();

        // Rotate main FAB
        if (fabMain != null) {
            fabMain.animate().rotation(45f).setDuration(300);
        }
    }

    public void closeFabMenu() {
        isFabMenuOpen = false;

        // Hide sub FABs and labels with animation
        animateFabsOut();

        // Rotate main FAB back
        if (fabMain != null) {
            fabMain.animate().rotation(0f).setDuration(300);
        }
    }

    private void setFabVisibility() {
        if (fabSend != null) fabSend.setVisibility(View.VISIBLE);
        if (fabReceive != null) fabReceive.setVisibility(View.VISIBLE);
        if (fabObstacle != null) fabObstacle.setVisibility(View.VISIBLE);
        if (fabRobot != null) fabRobot.setVisibility(View.VISIBLE);
        if (fabSendLabel != null) fabSendLabel.setVisibility(View.VISIBLE);
        if (fabReceiveLabel != null) fabReceiveLabel.setVisibility(View.VISIBLE);
        if (fabObstacleLabel != null) fabObstacleLabel.setVisibility(View.VISIBLE);
        if (fabRobotLabel != null) fabRobotLabel.setVisibility(View.VISIBLE);
    }

    private void animateFabsIn() {
        if (fabSend != null) {
            fabSend.animate().translationY(0).alpha(1.0f).setDuration(300);
        }
        if (fabReceive != null) {
            fabReceive.animate().translationY(0).alpha(1.0f).setDuration(300);
        }
        if (fabObstacle != null) {
            fabObstacle.animate().translationY(0).alpha(1.0f).setDuration(300);
        }
        if (fabRobot != null) {
            fabRobot.animate().translationY(0).alpha(1.0f).setDuration(300);
        }
        if (fabSendLabel != null) {
            fabSendLabel.animate().translationY(0).alpha(1.0f).setDuration(300);
        }
        if (fabReceiveLabel != null) {
            fabReceiveLabel.animate().translationY(0).alpha(1.0f).setDuration(300);
        }
        if (fabObstacleLabel != null) {
            fabObstacleLabel.animate().translationY(0).alpha(1.0f).setDuration(300);
        }
        if (fabRobotLabel != null) {
            fabRobotLabel.animate().translationY(0).alpha(1.0f).setDuration(300);
        }
    }

    private void animateFabsOut() {
        if (fabSend != null) {
            fabSend.animate().translationY(0).alpha(0.0f).setDuration(300).withEndAction(() -> fabSend.setVisibility(View.GONE));
        }
        if (fabReceive != null) {
            fabReceive.animate().translationY(0).alpha(0.0f).setDuration(300).withEndAction(() -> fabReceive.setVisibility(View.GONE));
        }
        if (fabObstacle != null) {
            fabObstacle.animate().translationY(0).alpha(0.0f).setDuration(300).withEndAction(() -> fabObstacle.setVisibility(View.GONE));
        }
        if (fabRobot != null) {
            fabRobot.animate().translationY(0).alpha(0.0f).setDuration(300).withEndAction(() -> fabRobot.setVisibility(View.GONE));
        }
        if (fabSendLabel != null) {
            fabSendLabel.animate().translationY(0).alpha(0.0f).setDuration(300).withEndAction(() -> fabSendLabel.setVisibility(View.GONE));
        }
        if (fabReceiveLabel != null) {
            fabReceiveLabel.animate().translationY(0).alpha(0.0f).setDuration(300).withEndAction(() -> fabReceiveLabel.setVisibility(View.GONE));
        }
        if (fabObstacleLabel != null) {
            fabObstacleLabel.animate().translationY(0).alpha(0.0f).setDuration(300).withEndAction(() -> fabObstacleLabel.setVisibility(View.GONE));
        }
        if (fabRobotLabel != null) {
            fabRobotLabel.animate().translationY(0).alpha(0.0f).setDuration(300).withEndAction(() -> fabRobotLabel.setVisibility(View.GONE));
        }
    }

    public void showSendDataSection() {
        if (sendDataSection != null) sendDataSection.setVisibility(View.VISIBLE);
        if (receiveDataSection != null) receiveDataSection.setVisibility(View.GONE);
        if (obstacleControlSection != null) obstacleControlSection.setVisibility(View.GONE);
        if (robotControlSection != null) robotControlSection.setVisibility(View.GONE);
        showToast("Send Data Section");
    }

    public void showReceiveDataSection() {
        if (sendDataSection != null) sendDataSection.setVisibility(View.GONE);
        if (receiveDataSection != null) receiveDataSection.setVisibility(View.VISIBLE);
        if (obstacleControlSection != null) obstacleControlSection.setVisibility(View.GONE);
        if (robotControlSection != null) robotControlSection.setVisibility(View.GONE);
        showToast("Receive Data Section");
    }

    public void showObstacleSection() {
        if (sendDataSection != null) sendDataSection.setVisibility(View.GONE);
        if (receiveDataSection != null) receiveDataSection.setVisibility(View.GONE);
        if (obstacleControlSection != null) obstacleControlSection.setVisibility(View.VISIBLE);
        if (robotControlSection != null) robotControlSection.setVisibility(View.GONE);
    }

    public void showRobotSection() {
        if (sendDataSection != null) sendDataSection.setVisibility(View.GONE);
        if (receiveDataSection != null) receiveDataSection.setVisibility(View.GONE);
        if (obstacleControlSection != null) obstacleControlSection.setVisibility(View.GONE);
        if (robotControlSection != null) robotControlSection.setVisibility(View.VISIBLE);
        showToast("Robot panel shown");
    }

    public void hideRobotSection() {
        if (robotControlSection != null) robotControlSection.setVisibility(View.GONE);
        showToast("Robot panel hidden");
    }

    public void updateObstacleButtonState() {
        // Disable toggle semantics; always show consistent icon/label for obstacle mode
        if (fabObstacle != null) {
            fabObstacle.setImageResource(android.R.drawable.ic_delete);
        }
        if (fabObstacleLabel != null) {
            fabObstacleLabel.setText("Obstacle Mode");
        }
    }

    public boolean isRobotSectionVisible() {
        return robotControlSection != null && robotControlSection.getVisibility() == View.VISIBLE;
    }

    private void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
