package com.linh.mdp.adapters.grid;

/**
 * Manages the core grid data and operations
 */
public class GridDataManager {
    private final GridCell[] cells;

    public GridDataManager() {
        this.cells = new GridCell[GridConstants.TOTAL_CELLS];
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
     * Update a specific cell with new data and color
     */
    public void updateCell(int row, int col, String data, int color) {
        if (isValidDataCell(row, col)) return;

        int position = row * GridConstants.GRID_SIZE + col;
        GridCell cell = cells[position];

        cell.setData(data);
        cell.setColor(color);
        cell.setObstacle("X".equals(data) || "?".equals(data) || "R".equals(data));
        cell.setTemporaryObstacle("?".equals(data));
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
        return col <= 0 || row < 0 || row >= GridConstants.DATA_SIZE;
    }

    /**
     * Check if cell is obstacle
     */
    public boolean isCellObstacle(int row, int col) {
        if (isValidDataCell(row, col)) return false;
        return getCell(row, col).isObstacle();
    }

    /**
     * Check if cell is permanent obstacle
     */
    public boolean isCellPermanentObstacle(int row, int col) {
        if (isValidDataCell(row, col)) return false;
        GridCell cell = getCell(row, col);
        return cell.isObstacle() && !cell.isTemporaryObstacle();
    }

    /**
     * Check if cell is temporary obstacle
     */
    public boolean isCellTemporaryObstacle(int row, int col) {
        if (isValidDataCell(row, col)) return false;
        return getCell(row, col).isTemporaryObstacle();
    }

    /**
     * Check if cell is robot
     */
    public boolean isCellRobot(int row, int col) {
        if (isValidDataCell(row, col)) return false;
        return getCell(row, col).isRobot();
    }

    /**
     * Get obstacle number at position
     */
    public int getObstacleNumberAt(int row, int col) {
        if (isValidDataCell(row, col)) return 0;
        return getCell(row, col).getObstacleNumber();
    }

}
