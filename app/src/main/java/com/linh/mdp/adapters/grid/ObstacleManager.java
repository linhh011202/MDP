package com.linh.mdp.adapters.grid;

/**
 * Manages obstacles and targets in the grid
 */
public class ObstacleManager {
    private int nextObstacleNumber = 1;

    /**
     * Set a cell as an obstacle
     */
    public void setObstacle(int row, int col, GridCell[] cells) {
        if (!isValidDataCell(row, col)) return;

        int position = row * GridConstants.GRID_SIZE + col;
        GridCell cell = cells[position];

        cell.setObstacle(true);
        cell.setTemporaryObstacle(false);
        cell.setObstacleNumber(nextObstacleNumber++);
        cell.setData(String.valueOf(cell.getObstacleNumber()));
        cell.setColor(GridConstants.OBSTACLE_COLOR);
        cell.setRobot(false);
    }

    /**
     * Remove an obstacle
     */
    public boolean removeObstacle(int row, int col, GridCell[] cells) {
        if (!isValidDataCell(row, col)) return false;

        int position = row * GridConstants.GRID_SIZE + col;
        GridCell cell = cells[position];

        if (!cell.isObstacle() || cell.isRobot()) return false;

        cell.setObstacle(false);
        cell.setTemporaryObstacle(false);
        cell.setObstacleNumber(0);
        cell.setBorderDirection(null);
        cell.setBorderColor(0);
        cell.setData("");
        cell.setColor(GridConstants.DEFAULT_CELL_COLOR);
        cell.setTarget(false);
        cell.setTargetId(null);

        return true;
    }

    /**
     * Clear all obstacles (except robot)
     */
    public void clearAllObstacles(GridCell[] cells) {
        for (int i = 0; i < GridConstants.TOTAL_CELLS; i++) {
            int row = i / GridConstants.GRID_SIZE;
            int col = i % GridConstants.GRID_SIZE;

            if (isValidDataCell(row, col)) {
                GridCell cell = cells[i];
                if (cell.isObstacle() && !cell.isRobot()) {
                    cell.setObstacle(false);
                    cell.setTemporaryObstacle(false);
                    cell.setObstacleNumber(0);
                    cell.setBorderDirection(null);
                    cell.setBorderColor(0);
                    cell.setData("");
                    cell.setColor(GridConstants.DEFAULT_CELL_COLOR);
                    cell.setTarget(false);
                    cell.setTargetId(null);
                }
            }
        }
        nextObstacleNumber = 1;
    }

    /**
     * Clear temporary obstacle from a cell
     */
    public void clearTemporaryObstacle(int row, int col, GridCell[] cells) {
        if (!isValidDataCell(row, col)) return;

        int position = row * GridConstants.GRID_SIZE + col;
        GridCell cell = cells[position];

        if (cell.isTemporaryObstacle()) {
            cell.setData("");
            cell.setColor(GridConstants.DEFAULT_CELL_COLOR);
            cell.setObstacle(false);
            cell.setTemporaryObstacle(false);
        }
    }

    /**
     * Move obstacle while preserving its number
     */
    public boolean moveObstaclePreserveNumber(int fromRow, int fromCol, int toRow, int toCol, GridCell[] cells) {
        if (!isValidDataCell(fromRow, fromCol) || !isValidDataCell(toRow, toCol)) return false;

        int fromPos = fromRow * GridConstants.GRID_SIZE + fromCol;
        int toPos = toRow * GridConstants.GRID_SIZE + toCol;
        GridCell fromCell = cells[fromPos];
        GridCell toCell = cells[toPos];

        // Must be a permanent obstacle at source
        if (!fromCell.isPermanentObstacle() || fromCell.isRobot()) return false;

        // Destination must be empty or same as source
        if (toPos != fromPos && (toCell.isObstacle() || toCell.isRobot() || toCell.isTemporaryObstacle())) {
            return false;
        }

        // Capture source data
        int number = fromCell.getObstacleNumber();
        String display = fromCell.getData();
        String borderDir = fromCell.getBorderDirection();
        int borderColor = fromCell.getBorderColor();

        // Clear source
        fromCell.clear();

        // Place at destination
        toCell.setObstacle(true);
        toCell.setTemporaryObstacle(false);
        toCell.setRobot(false);
        toCell.setObstacleNumber(number);
        toCell.setData(number > 0 ? String.valueOf(number) : (display != null && !display.trim().isEmpty() ? display.trim() : "1"));
        toCell.setColor(GridConstants.OBSTACLE_COLOR);
        toCell.setBorderDirection(borderDir);
        toCell.setBorderColor(borderColor);

        return true;
    }

    /**
     * Set obstacle as target
     */
    public boolean setObstacleAsTarget(int obstacleNumber, String targetId, GridCell[] cells) {
        for (int i = 0; i < GridConstants.TOTAL_CELLS; i++) {
            GridCell cell = cells[i];
            if (cell.getObstacleNumber() == obstacleNumber && cell.isObstacle() && !cell.isRobot()) {
                cell.setTarget(true);
                cell.setTargetId(targetId);
                cell.setData(targetId);
                return true;
            }
        }
        return false;
    }

    /**
     * Remove target status from obstacle
     */
    public boolean removeTargetFromObstacle(int obstacleNumber, GridCell[] cells) {
        for (int i = 0; i < GridConstants.TOTAL_CELLS; i++) {
            GridCell cell = cells[i];
            if (cell.getObstacleNumber() == obstacleNumber && cell.isObstacle() && !cell.isRobot()) {
                cell.setTarget(false);
                cell.setTargetId(null);
                cell.setData(String.valueOf(cell.getObstacleNumber()));
                return true;
            }
        }
        return false;
    }

    /**
     * Clear all targets
     */
    public void clearAllTargets(GridCell[] cells) {
        for (int i = 0; i < GridConstants.TOTAL_CELLS; i++) {
            GridCell cell = cells[i];
            if (cell.isTarget()) {
                cell.setTarget(false);
                cell.setTargetId(null);
                if (cell.getObstacleNumber() > 0) {
                    cell.setData(String.valueOf(cell.getObstacleNumber()));
                }
            }
        }
    }

    /**
     * Highlight selected obstacle
     */
    public void highlightSelectedObstacle(int row, int col, GridCell[] cells) {
        if (!isValidDataCell(row, col)) return;

        int position = row * GridConstants.GRID_SIZE + col;
        GridCell cell = cells[position];

        if (cell.isObstacle() && !cell.isRobot()) {
            cell.setColor(GridConstants.SELECTED_COLOR);
        }
    }

    /**
     * Clear selected obstacle highlight
     */
    public void clearSelectedObstacleHighlight(int row, int col, GridCell[] cells) {
        if (!isValidDataCell(row, col)) return;

        int position = row * GridConstants.GRID_SIZE + col;
        GridCell cell = cells[position];

        if (cell.isObstacle() && !cell.isRobot()) {
            cell.setColor(GridConstants.OBSTACLE_COLOR);
        }
    }

    /**
     * Check if cell is a valid data cell
     */
    private boolean isValidDataCell(int row, int col) {
        return col > 0 && row >= 0 && row < GridConstants.DATA_SIZE;
    }

    // Getters
    public int getNextObstacleNumber() {
        return nextObstacleNumber;
    }

    public void resetObstacleCounter() {
        nextObstacleNumber = 1;
    }
}
