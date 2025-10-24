package com.linh.mdp.adapters.grid;

/**
 * Manages highlighting, borders, and visual effects in the grid
 */
public class HighlightManager {

    /**
     * Highlight specific cell border with given direction
     */
    public void highlightCellBorder(int row, int col, int borderColor, String direction, GridCell[] cells) {
        if (!isValidDataCell(row, col)) return;

        int position = row * GridConstants.GRID_SIZE + col;
        GridCell cell = cells[position];
        cell.setBorderDirection(direction.toUpperCase());
        cell.setBorderColor(borderColor);
    }

    /**
     * Clear border from specific cell
     */
    public void clearCellBorder(int row, int col, GridCell[] cells) {
        if (!isValidDataCell(row, col)) return;

        int position = row * GridConstants.GRID_SIZE + col;
        GridCell cell = cells[position];
        cell.setBorderDirection(null);
        cell.setBorderColor(0);
    }

    /**
     * Apply temporary row/column highlight
     */
    public void applyTempRowColHighlight(int tempRow, int tempCol, GridCell[] cells) {
        clearTempRowColHighlight(cells);

        // Highlight row
        if (tempRow >= 0 && tempRow < GridConstants.DATA_SIZE) {
            for (int c = 1; c < GridConstants.GRID_SIZE; c++) {
                int pos = tempRow * GridConstants.GRID_SIZE + c;
                GridCell cell = cells[pos];
                if (c == tempCol) continue; // skip the temporary obstacle cell
                if (!cell.isObstacle() && !cell.isRobot()) {
                    cell.setColor(GridConstants.HIGHLIGHT_COLOR);
                    cell.setTempHighlight(true);
                }
            }
        }

        // Highlight column
        if (tempCol > 0 && tempCol < GridConstants.GRID_SIZE) {
            for (int r = 0; r < GridConstants.DATA_SIZE; r++) {
                int pos = r * GridConstants.GRID_SIZE + tempCol;
                GridCell cell = cells[pos];
                if (r == tempRow) continue; // skip the temporary obstacle cell
                if (!cell.isObstacle() && !cell.isRobot()) {
                    cell.setColor(GridConstants.HIGHLIGHT_COLOR);
                    cell.setTempHighlight(true);
                }
            }
        }
    }

    /**
     * Clear temporary row/column highlight
     */
    public void clearTempRowColHighlight(GridCell[] cells) {
        for (int i = 0; i < GridConstants.TOTAL_CELLS; i++) {
            GridCell cell = cells[i];
            if (cell.isTempHighlight()) {
                int row = i / GridConstants.GRID_SIZE;
                int col = i % GridConstants.GRID_SIZE;
                if (isValidDataCell(row, col)) {
                    if (!cell.isObstacle() && !cell.isRobot()) {
                        cell.setColor(GridConstants.DEFAULT_CELL_COLOR);
                    }
                }
                cell.setTempHighlight(false);
            }
        }
    }

    /**
     * Check if cell is valid data cell
     */
    private boolean isValidDataCell(int row, int col) {
        return col > 0 && row >= 0 && row < GridConstants.DATA_SIZE;
    }
}
