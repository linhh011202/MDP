package com.linh.mdp.adapters.grid;

/**
 * Manages highlighting, borders, and visual effects in the grid
 */
public class HighlightManager {

    /**
     * Highlight obstacle borders in a specific direction
     */
    public void highlightObstacleBorders(String direction, int borderColor, GridCell[] cells) {
        clearBorderHighlights(cells);

        for (int row = 0; row < GridConstants.DATA_SIZE; row++) {
            for (int col = 1; col < GridConstants.GRID_SIZE; col++) {
                GridCell cell = cells[row * GridConstants.GRID_SIZE + col];
                if (cell.isObstacle()) {
                    boolean shouldHighlight = shouldHighlightBorder(row, col, direction, cells);
                    if (shouldHighlight) {
                        cell.setBorderDirection(direction.toUpperCase());
                        cell.setBorderColor(borderColor);
                    }
                }
            }
        }
    }

    /**
     * Clear all border highlights
     */
    public void clearBorderHighlights(GridCell[] cells) {
        for (int i = 0; i < GridConstants.TOTAL_CELLS; i++) {
            GridCell cell = cells[i];
            cell.setBorderDirection(null);
            cell.setBorderColor(0);
        }
    }

    /**
     * Highlight specific cell border
     */
    public void highlightCellBorder(int row, int col, int borderColor, GridCell[] cells) {
        if (!isValidDataCell(row, col)) return;

        int position = row * GridConstants.GRID_SIZE + col;
        GridCell cell = cells[position];

        String borderDirection = determineBorderDirection(row, col, cells);
        cell.setBorderDirection(borderDirection);
        cell.setBorderColor(borderColor);
    }

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
     * Determine if border should be highlighted based on direction
     */
    private boolean shouldHighlightBorder(int row, int col, String direction, GridCell[] cells) {
        switch (direction.toUpperCase()) {
            case "N": // North border
                return (row == 0 || !isCellObstacle(row - 1, col, cells));
            case "S": // South border
                return (row == GridConstants.DATA_SIZE - 1 || !isCellObstacle(row + 1, col, cells));
            case "E": // East border
                return (col == GridConstants.GRID_SIZE - 1 || !isCellObstacle(row, col + 1, cells));
            case "W": // West border
                return (col == 1 || !isCellObstacle(row, col - 1, cells));
            default:
                return false;
        }
    }

    /**
     * Determine border direction based on adjacent obstacles
     */
    private String determineBorderDirection(int row, int col, GridCell[] cells) {
        StringBuilder borderDirection = new StringBuilder();

        boolean hasNorthBorder = (row == 0 || !isCellObstacle(row - 1, col, cells));
        boolean hasSouthBorder = (row == GridConstants.DATA_SIZE - 1 || !isCellObstacle(row + 1, col, cells));
        boolean hasEastBorder = (col == GridConstants.GRID_SIZE - 1 || !isCellObstacle(row, col + 1, cells));
        boolean hasWestBorder = (col == 1 || !isCellObstacle(row, col - 1, cells));

        if (hasNorthBorder) borderDirection.append("N");
        if (hasSouthBorder) borderDirection.append("S");
        if (hasEastBorder) borderDirection.append("E");
        if (hasWestBorder) borderDirection.append("W");

        return borderDirection.length() == 0 ? "ALL" : borderDirection.toString();
    }

    /**
     * Check if cell is obstacle
     */
    private boolean isCellObstacle(int row, int col, GridCell[] cells) {
        if (!isValidDataCell(row, col)) return false;
        int position = row * GridConstants.GRID_SIZE + col;
        return cells[position].isObstacle();
    }

    /**
     * Check if cell is valid data cell
     */
    private boolean isValidDataCell(int row, int col) {
        return col > 0 && row >= 0 && row < GridConstants.DATA_SIZE;
    }
}
