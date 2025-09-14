package com.linh.mdp.adapters.grid;

import java.util.Random;

/**
 * Manages the core grid data and operations
 */
public class GridDataManager {
    private final GridCell[] cells;
    private final Random random;

    public GridDataManager() {
        this.cells = new GridCell[GridConstants.TOTAL_CELLS];
        this.random = new Random();
        initializeGrid();
    }

    /**
     * Initialize the grid with headers and empty data cells
     */
    private void initializeGrid() {
        for (int i = 0; i < GridConstants.TOTAL_CELLS; i++) {
            cells[i] = new GridCell();

            int row = i / GridConstants.GRID_SIZE;
            int col = i % GridConstants.GRID_SIZE;

            if (col == 0 && row < GridConstants.DATA_SIZE) {
                // Row headers: 19-0 (reversed)
                cells[i].setData(String.valueOf((GridConstants.DATA_SIZE - 1) - row));
                cells[i].setColor(GridConstants.HEADER_COLOR);
            } else if (row == GridConstants.DATA_SIZE) {
                // Bottom row: headers and bottom-left corner
                if (col == 0) {
                    // Bottom-left corner: empty
                    cells[i].setData("");
                } else {
                    // Bottom column headers: 0-19
                    cells[i].setData(String.valueOf(col - 1));
                }
                cells[i].setColor(GridConstants.HEADER_COLOR);
            }
            // Data cells are left with defaults
        }
    }

    /**
     * Update a specific cell with new data
     */
    public void updateCell(int row, int col, String data) {
        if (!isValidDataCell(row, col)) return;

        int position = row * GridConstants.GRID_SIZE + col;
        GridCell cell = cells[position];

        cell.setData(data);
        // Keep obstacle flags in sync
        cell.setObstacle("X".equals(data) || "?".equals(data) || "R".equals(data));
        cell.setTemporaryObstacle("?".equals(data));
    }

    /**
     * Update a specific cell with new data and color
     */
    public void updateCell(int row, int col, String data, int color) {
        if (!isValidDataCell(row, col)) return;

        int position = row * GridConstants.GRID_SIZE + col;
        GridCell cell = cells[position];

        cell.setData(data);
        cell.setColor(color);
        cell.setObstacle("X".equals(data) || "?".equals(data) || "R".equals(data));
        cell.setTemporaryObstacle("?".equals(data));
    }

    /**
     * Clear all data cells (keeping headers)
     */
    public void clearData() {
        for (int i = 0; i < GridConstants.TOTAL_CELLS; i++) {
            int row = i / GridConstants.GRID_SIZE;
            int col = i % GridConstants.GRID_SIZE;

            if (isValidDataCell(row, col)) {
                cells[i].clear();
            }
        }
    }

    /**
     * Fill grid with random sample data
     */
    public void fillWithSampleData() {
        for (int row = 0; row < GridConstants.DATA_SIZE; row++) {
            for (int col = 1; col < GridConstants.GRID_SIZE; col++) {
                String data = String.valueOf(random.nextInt(100));
                updateCell(row, col, data, GridConstants.DEFAULT_CELL_COLOR);
            }
        }
    }

    /**
     * Get cell at specific position
     */
    public GridCell getCell(int row, int col) {
        if (row < 0 || row >= GridConstants.GRID_SIZE || col < 0 || col >= GridConstants.GRID_SIZE) {
            return null;
        }
        return cells[row * GridConstants.GRID_SIZE + col];
    }

    /**
     * Get cell by linear position
     */
    public GridCell getCell(int position) {
        if (position < 0 || position >= GridConstants.TOTAL_CELLS) {
            return null;
        }
        return cells[position];
    }

    /**
     * Get all cells
     */
    public GridCell[] getAllCells() {
        return cells;
    }

    /**
     * Check if coordinates represent a valid data cell
     */
    public boolean isValidDataCell(int row, int col) {
        return col > 0 && row >= 0 && row < GridConstants.DATA_SIZE;
    }

    /**
     * Check if cell is obstacle
     */
    public boolean isCellObstacle(int row, int col) {
        if (!isValidDataCell(row, col)) return false;
        return getCell(row, col).isObstacle();
    }

    /**
     * Check if cell is permanent obstacle
     */
    public boolean isCellPermanentObstacle(int row, int col) {
        if (!isValidDataCell(row, col)) return false;
        GridCell cell = getCell(row, col);
        return cell.isObstacle() && !cell.isTemporaryObstacle();
    }

    /**
     * Check if cell is temporary obstacle
     */
    public boolean isCellTemporaryObstacle(int row, int col) {
        if (!isValidDataCell(row, col)) return false;
        return getCell(row, col).isTemporaryObstacle();
    }

    /**
     * Check if cell is robot
     */
    public boolean isCellRobot(int row, int col) {
        if (!isValidDataCell(row, col)) return false;
        return getCell(row, col).isRobot();
    }

    /**
     * Check if cell is target
     */
    public boolean isCellTarget(int row, int col) {
        if (!isValidDataCell(row, col)) return false;
        return getCell(row, col).isTarget();
    }

    /**
     * Get target ID for cell
     */
    public String getCellTargetId(int row, int col) {
        if (!isValidDataCell(row, col)) return null;
        return getCell(row, col).getTargetId();
    }

    /**
     * Get obstacle number at position
     */
    public int getObstacleNumberAt(int row, int col) {
        if (!isValidDataCell(row, col)) return 0;
        return getCell(row, col).getObstacleNumber();
    }

    /**
     * Check if cell has border
     */
    public boolean hasCellBorder(int row, int col) {
        if (!isValidDataCell(row, col)) return false;
        return getCell(row, col).hasBorder();
    }

    /**
     * Get cell border direction
     */
    public String getCellBorderDirection(int row, int col) {
        if (!isValidDataCell(row, col)) return null;
        return getCell(row, col).getBorderDirection();
    }

    /**
     * Check if cell is visually permanent obstacle
     */
    public boolean isVisuallyPermanentObstacle(int row, int col) {
        if (!isValidDataCell(row, col)) return false;
        return getCell(row, col).isPermanentObstacle();
    }
}
