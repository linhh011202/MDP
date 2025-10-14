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
    public void removeObstacle(int row, int col, GridCell[] cells) {
        if (!isValidDataCell(row, col)) return;

        int position = row * GridConstants.GRID_SIZE + col;
        GridCell cell = cells[position];

        if (!cell.isObstacle() || cell.isRobot()) return;

        cell.setObstacle(false);
        cell.setTemporaryObstacle(false);
        cell.setObstacleNumber(0);
        cell.setBorderDirection(null);
        cell.setBorderColor(0);
        cell.setData("");
        cell.setColor(GridConstants.DEFAULT_CELL_COLOR);
        cell.setTarget(false);

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
     * Set obstacle as target
     */
    public boolean setObstacleAsTarget(int obstacleNumber, String targetId, GridCell[] cells) {
        for (int i = 0; i < GridConstants.TOTAL_CELLS; i++) {
            GridCell cell = cells[i];
            if (cell.getObstacleNumber() == obstacleNumber && cell.isObstacle() && !cell.isRobot()) {
                cell.setTarget(true);
                cell.setData(targetId);
                return true;
            }
        }
        return false;
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

}
