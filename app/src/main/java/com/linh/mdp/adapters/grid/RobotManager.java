package com.linh.mdp.adapters.grid;

/**
 * Manages robot state and operations in the grid
 */
public class RobotManager {
    private int centerRow = -1;
    private int centerCol = -1;
    private int orientation = GridConstants.ORIENTATION_NORTH;
    private int tempCenterRow = -1;
    private int tempCenterCol = -1;

    /**
     * Check if robot is placed on the grid
     */
    public boolean hasRobot() {
        return centerRow != -1 && centerCol != -1;
    }

    /**
     * Get robot center position
     */
    public int[] getRobotPosition() {
        if (!hasRobot()) return null;
        return new int[]{centerRow, centerCol};
    }

    /**
     * Get robot position in display coordinates (0-19)
     */
    public int[] getDisplayPosition() {
        if (!hasRobot()) return null;
        int displayX = centerCol - 1; // col (1-20) -> x (0-19)
        // Correct off-by-one: internal row 0 (top) should map to displayY 19 (bottom)
        int displayY = (GridConstants.DATA_SIZE - 1) - centerRow; // row (0-19) -> y (0-19) inverted
        return new int[]{displayX, displayY};
    }

    /**
     * Check if a 3x3 robot can be placed at the given center
     */
    public boolean canPlaceAtCenter(int centerRow, int centerCol, GridCell[] cells) {
        // Ensure within data area
        if (!(centerCol > 0 && centerRow >= 0 && centerRow < GridConstants.DATA_SIZE)) return false;
        // Must have one-cell margin in all directions
        if (centerRow - 1 < 0 || centerRow + 1 > GridConstants.DATA_SIZE - 1) return false;
        if (centerCol - 1 < 1 || centerCol + 1 > GridConstants.GRID_SIZE - 1) return false;

        // Check collisions with permanent obstacles
        for (int r = centerRow - 1; r <= centerRow + 1; r++) {
            for (int c = centerCol - 1; c <= centerCol + 1; c++) {
                int pos = r * GridConstants.GRID_SIZE + c;
                GridCell cell = cells[pos];
                // Block if there is a permanent obstacle (excluding current robot position)
                if (cell.isObstacle() && !cell.isRobot()) return false;
            }
        }
        return true;
    }

    /**
     * Place robot at center position
     */
    public boolean placeAtCenter(int centerRow, int centerCol, GridCell[] cells) {
        if (!canPlaceAtCenter(centerRow, centerCol, cells)) return false;

        // Clear existing robot
        clearRobot(cells);

        // Mark new 3x3 as robot cells
        for (int r = centerRow - 1; r <= centerRow + 1; r++) {
            for (int c = centerCol - 1; c <= centerCol + 1; c++) {
                int pos = r * GridConstants.GRID_SIZE + c;
                GridCell cell = cells[pos];
                cell.setRobot(true);
                cell.setObstacle(true);
                cell.setTemporaryObstacle(false);
                cell.setData("R");
                cell.setColor(GridConstants.ROBOT_COLOR);
                cell.setBorderDirection(null);
                cell.setBorderColor(0);
            }
        }

        this.centerRow = centerRow;
        this.centerCol = centerCol;
        applyFrontMarker(cells);
        return true;
    }

    /**
     * Clear robot from grid
     */
    public void clearRobot(GridCell[] cells) {
        if (!hasRobot()) return;

        for (int r = centerRow - 1; r <= centerRow + 1; r++) {
            for (int c = centerCol - 1; c <= centerCol + 1; c++) {
                int pos = r * GridConstants.GRID_SIZE + c;
                GridCell cell = cells[pos];
                if (cell.isRobot()) {
                    cell.setRobot(false);
                    cell.setObstacle(false);
                    cell.setTemporaryObstacle(false);
                    cell.setData("");
                    cell.setColor(GridConstants.DEFAULT_CELL_COLOR);
                    cell.setBorderDirection(null);
                    cell.setBorderColor(0);
                }
            }
        }
        centerRow = -1;
        centerCol = -1;
    }

    /**
     * Turn robot left
     */
    public void turnLeft(GridCell[] cells) {
        if (!hasRobot()) return;
        orientation = (orientation + 3) % 4; // -1 mod 4
        applyFrontMarker(cells);
    }

    /**
     * Turn robot right
     */
    public void turnRight(GridCell[] cells) {
        if (!hasRobot()) return;
        orientation = (orientation + 1) % 4;
        applyFrontMarker(cells);
    }

    /**
     * Move robot by delta
     */
    public boolean moveRobot(int dRow, int dCol, GridCell[] cells) {
        if (!hasRobot()) return false;
        int newCenterRow = centerRow + dRow;
        int newCenterCol = centerCol + dCol;
        if (!canPlaceAtCenter(newCenterRow, newCenterCol, cells)) return false;

        // Check collisions excluding current robot footprint
        for (int r = newCenterRow - 1; r <= newCenterRow + 1; r++) {
            for (int c = newCenterCol - 1; c <= newCenterCol + 1; c++) {
                int pos = r * GridConstants.GRID_SIZE + c;
                boolean inCurrentRobot = (r >= centerRow - 1 && r <= centerRow + 1 &&
                                        c >= centerCol - 1 && c <= centerCol + 1);
                if (!inCurrentRobot && cells[pos].isObstacle() && !cells[pos].isRobot()) {
                    return false;
                }
            }
        }

        clearRobot(cells);
        return placeAtCenter(newCenterRow, newCenterCol, cells);
    }

    /**
     * Apply front marker color based on orientation
     */
    private void applyFrontMarker(GridCell[] cells) {
        if (!hasRobot()) return;

        // Repaint all robot cells to default robot color
        for (int r = centerRow - 1; r <= centerRow + 1; r++) {
            for (int c = centerCol - 1; c <= centerCol + 1; c++) {
                int pos = r * GridConstants.GRID_SIZE + c;
                if (cells[pos].isRobot()) {
                    cells[pos].setColor(GridConstants.ROBOT_COLOR);
                }
            }
        }

        // Determine front cell based on orientation
        int relR = 0, relC = 1; // default North
        switch (orientation) {
            case GridConstants.ORIENTATION_NORTH:
                relR = 0; relC = 1;
                break;
            case GridConstants.ORIENTATION_EAST:
                relR = 1; relC = 2;
                break;
            case GridConstants.ORIENTATION_SOUTH:
                relR = 2; relC = 1;
                break;
            case GridConstants.ORIENTATION_WEST:
                relR = 1; relC = 0;
                break;
        }

        int frontRow = (centerRow - 1) + relR;
        int frontCol = (centerCol - 1) + relC;
        int frontPos = frontRow * GridConstants.GRID_SIZE + frontCol;
        cells[frontPos].setColor(GridConstants.ROBOT_FRONT_COLOR);
    }

    /**
     * Update robot position from external coordinates
     */
    public boolean updatePosition(int x, int y, String direction, GridCell[] cells) {
        // Convert display coordinates (0-19, origin at bottom-left) to grid coordinates
        int gridCol = x + 1; // shift because col 0 is non-data margin
        // Correct off-by-one: y=0 (bottom) should map to last data row (DATA_SIZE-1)
        int gridRow = (GridConstants.DATA_SIZE - 1) - y; // invert Y axis

        // Convert direction to orientation
        int newOrientation = parseDirection(direction);
        if (newOrientation == -1) return false;

        if (placeAtCenter(gridRow, gridCol, cells)) {
            orientation = newOrientation;
            applyFrontMarker(cells);
            return true;
        }
        return false;
    }

    /**
     * Parse direction string to orientation
     */
    private int parseDirection(String direction) {
        if (direction == null) return -1;
        switch (direction.toUpperCase().trim()) {
            case "N": return GridConstants.ORIENTATION_NORTH;
            case "E": return GridConstants.ORIENTATION_EAST;
            case "S": return GridConstants.ORIENTATION_SOUTH;
            case "W": return GridConstants.ORIENTATION_WEST;
            default: return -1;
        }
    }

    /**
     * Get direction as string
     */
    public String getDirectionString() {
        if (!hasRobot()) return null;
        switch (orientation) {
            case GridConstants.ORIENTATION_NORTH: return "N";
            case GridConstants.ORIENTATION_EAST: return "E";
            case GridConstants.ORIENTATION_SOUTH: return "S";
            case GridConstants.ORIENTATION_WEST: return "W";
            default: return "N";
        }
    }

    // Getters
    public int getCenterRow() { return centerRow; }
    public int getCenterCol() { return centerCol; }
    public int getOrientation() { return orientation; }
    public int getTempCenterRow() { return tempCenterRow; }
    public int getTempCenterCol() { return tempCenterCol; }

    // Temporary robot preview methods
    public boolean showTemporaryRobotAtCenter(int centerRow, int centerCol, GridCell[] cells) {
        if (!canPlaceAtCenter(centerRow, centerCol, cells)) return false;
        clearTemporaryRobotPreview(cells);

        for (int r = centerRow - 1; r <= centerRow + 1; r++) {
            for (int c = centerCol - 1; c <= centerCol + 1; c++) {
                int pos = r * GridConstants.GRID_SIZE + c;
                GridCell cell = cells[pos];
                if (!cell.isObstacle() && !cell.isRobot()) {
                    cell.setColor(GridConstants.TEMP_ROBOT_COLOR);
                }
                cell.setTempRobot(true);
            }
        }
        tempCenterRow = centerRow;
        tempCenterCol = centerCol;
        return true;
    }

    public void clearTemporaryRobotPreview(GridCell[] cells) {
        if (tempCenterRow == -1 || tempCenterCol == -1) {
            // Fallback: clear scattered flags
            for (int i = 0; i < GridConstants.TOTAL_CELLS; i++) {
                GridCell cell = cells[i];
                if (cell.isTempRobot()) {
                    int row = i / GridConstants.GRID_SIZE;
                    int col = i % GridConstants.GRID_SIZE;
                    if (col > 0 && row >= 0 && row < GridConstants.DATA_SIZE &&
                        !cell.isObstacle() && !cell.isRobot()) {
                        cell.setColor(GridConstants.DEFAULT_CELL_COLOR);
                    }
                    cell.setTempRobot(false);
                }
            }
            return;
        }

        for (int r = tempCenterRow - 1; r <= tempCenterRow + 1; r++) {
            for (int c = tempCenterCol - 1; c <= tempCenterCol + 1; c++) {
                int pos = r * GridConstants.GRID_SIZE + c;
                if (pos >= 0 && pos < GridConstants.TOTAL_CELLS) {
                    GridCell cell = cells[pos];
                    if (!cell.isObstacle() && !cell.isRobot()) {
                        cell.setColor(GridConstants.DEFAULT_CELL_COLOR);
                    }
                    cell.setTempRobot(false);
                }
            }
        }
        tempCenterRow = -1;
        tempCenterCol = -1;
    }

    public boolean confirmTemporaryRobotPlacement(GridCell[] cells) {
        if (tempCenterRow == -1 || tempCenterCol == -1) return false;
        int r = tempCenterRow, c = tempCenterCol;
        clearTemporaryRobotPreview(cells);
        return placeAtCenter(r, c, cells);
    }
}
