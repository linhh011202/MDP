package com.linh.mdp.adapters.grid;

/**
 * Represents a cell in the grid with its properties
 */
public class GridCell {
    private String data;
    private int color;
    private boolean isObstacle;
    private boolean isTemporaryObstacle;
    private boolean isRobot;
    private boolean isTarget;
    private int obstacleNumber;
    private String borderDirection;
    private int borderColor;
    private boolean isTempHighlight;
    private boolean isTempRobot;

    public GridCell() {
        this.data = "";
        this.color = GridConstants.DEFAULT_CELL_COLOR;
        this.isObstacle = false;
        this.isTemporaryObstacle = false;
        this.isRobot = false;
        this.isTarget = false;
        this.obstacleNumber = 0;
        this.borderDirection = null;
        this.borderColor = 0;
        this.isTempHighlight = false;
        this.isTempRobot = false;
    }

    // Getters and setters
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }

    public boolean isObstacle() { return isObstacle; }
    public void setObstacle(boolean obstacle) { this.isObstacle = obstacle; }

    public boolean isTemporaryObstacle() { return isTemporaryObstacle; }
    public void setTemporaryObstacle(boolean temporaryObstacle) { this.isTemporaryObstacle = temporaryObstacle; }

    public boolean isRobot() { return isRobot; }
    public void setRobot(boolean robot) { this.isRobot = robot; }

    public boolean isTarget() { return isTarget; }
    public void setTarget(boolean target) { this.isTarget = target; }
    public int getObstacleNumber() { return obstacleNumber; }
    public void setObstacleNumber(int obstacleNumber) { this.obstacleNumber = obstacleNumber; }

    public String getBorderDirection() { return borderDirection; }
    public void setBorderDirection(String borderDirection) { this.borderDirection = borderDirection; }

    public int getBorderColor() { return borderColor; }
    public void setBorderColor(int borderColor) { this.borderColor = borderColor; }

    public boolean isTempHighlight() { return isTempHighlight; }
    public void setTempHighlight(boolean tempHighlight) { this.isTempHighlight = tempHighlight; }

    public boolean isTempRobot() { return isTempRobot; }
    public void setTempRobot(boolean tempRobot) { this.isTempRobot = tempRobot; }

    /**
     * Check if cell has any border
     */
    public boolean hasBorder() {
        return borderDirection != null && borderColor != 0;
    }

}
