package com.linh.mdp.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.linh.mdp.R;
import com.linh.mdp.adapters.grid.GridCell;
import com.linh.mdp.adapters.grid.GridConstants;
import com.linh.mdp.adapters.grid.GridDataManager;
import com.linh.mdp.adapters.grid.HighlightManager;
import com.linh.mdp.adapters.grid.ObstacleManager;
import com.linh.mdp.adapters.grid.RobotManager;
import com.linh.mdp.ui.BorderableCell;

/**
 * Refactored GridTableAdapter - now uses composition pattern with specialized managers
 * This adapter is now focused only on the Android adapter responsibilities
 */
public class GridTableAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;

    // Managers for different responsibilities
    private final GridDataManager dataManager;
    private final RobotManager robotManager;
    private final ObstacleManager obstacleManager;
    private final HighlightManager highlightManager;

    // Batching for smoother dragging
    private boolean notificationsEnabled = true;

    public GridTableAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.dataManager = new GridDataManager();
        this.robotManager = new RobotManager();
        this.obstacleManager = new ObstacleManager();
        this.highlightManager = new HighlightManager();
    }

    // Helper: notify only when enabled
    private void notifyChangedIfEnabled() {
        if (notificationsEnabled) notifyDataSetChanged();
    }

    // Public batching API
    public void beginBatchUpdates() { notificationsEnabled = false; }
    public void endBatchUpdates() { notificationsEnabled = true; notifyDataSetChanged(); }

    @Override
    public int getCount() {
        return GridConstants.TOTAL_CELLS;
    }

    @Override
    public Object getItem(int position) {
        return dataManager.getCell(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        BorderableCell cellView = getCellView(convertView, parent);
        GridCell cell = dataManager.getCell(position);

        if (cell != null) {
            configureCellView(cellView, cell);
        }

        return cellView;
    }

    /**
     * Get or create cell view
     */
    private BorderableCell getCellView(View convertView, ViewGroup parent) {
        if (convertView == null) {
            return (BorderableCell) inflater.inflate(R.layout.grid_cell_item, parent, false);
        }
        return (BorderableCell) convertView;
    }

    /**
     * Configure cell view based on cell data
     */
    private void configureCellView(BorderableCell cellView, GridCell cell) {
        // Set basic cell properties
        cellView.setText(cell.getData());
        cellView.setBackgroundColor(cell.getColor());

        // Set text color and styling
        configureTextAppearance(cellView, cell);

        // Set borders
        configureBorders(cellView, cell);
    }

    /**
     * Configure text appearance based on cell type
     */
    private void configureTextAppearance(BorderableCell cellView, GridCell cell) {
        if (cell.isObstacle() && !cell.isRobot()) {
            cellView.setTextColor(Color.WHITE);
        } else {
            cellView.setTextColor(Color.BLACK);
        }

        if (cell.isTarget()) {
            cellView.setTypeface(cellView.getTypeface(), android.graphics.Typeface.BOLD);
            cellView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, GridConstants.TARGET_TEXT_SIZE);
        } else {
            cellView.setTypeface(cellView.getTypeface(), android.graphics.Typeface.NORMAL);
            cellView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, GridConstants.NORMAL_TEXT_SIZE);
        }
    }

    /**
     * Configure cell borders
     */
    private void configureBorders(BorderableCell cellView, GridCell cell) {
        if (cell.hasBorder()) {
            cellView.setBorderColor(cell.getBorderColor());
            cellView.setBorderDirection(cell.getBorderDirection());
        } else {
            cellView.clearBorders();
        }
    }

    // ============================================================================
    // PUBLIC API METHODS - Delegate to appropriate managers
    // ============================================================================

    // Basic grid operations
    public void updateCell(int row, int col, String data) {
        dataManager.updateCell(row, col, data);
        notifyChangedIfEnabled();
    }

    public void updateCell(int row, int col, String data, int color) {
        dataManager.updateCell(row, col, data, color);
        notifyChangedIfEnabled();
    }

    public void clearData() {
        dataManager.clearData();
        robotManager.clearRobot(dataManager.getAllCells());
        obstacleManager.resetObstacleCounter();
        notifyChangedIfEnabled();
    }

    public void fillWithSampleData() {
        dataManager.fillWithSampleData();
        notifyChangedIfEnabled();
    }

    public int getGridSize() {
        return GridConstants.GRID_SIZE;
    }

    // Robot operations
    public boolean hasRobot() {
        return robotManager.hasRobot();
    }

    public int getRobotCenterRow() {
        return robotManager.getCenterRow();
    }

    public int getRobotCenterCol() {
        return robotManager.getCenterCol();
    }

    public int getRobotOrientation() {
        return robotManager.getOrientation();
    }

    public int[] getRobotPosition() {
        return robotManager.getRobotPosition();
    }

    public int[] getRobotDisplayPosition() {
        return robotManager.getDisplayPosition();
    }

    public String getRobotDirectionString() {
        return robotManager.getDirectionString();
    }

    public boolean canPlaceRobotAtCenter(int centerRow, int centerCol) {
        return robotManager.canPlaceAtCenter(centerRow, centerCol, dataManager.getAllCells());
    }

    public boolean placeRobotAtCenter(int centerRow, int centerCol) {
        boolean result = robotManager.placeAtCenter(centerRow, centerCol, dataManager.getAllCells());
        if (result) notifyChangedIfEnabled();
        return result;
    }

    public boolean placeRobot(int row, int col, int orientation) {
        boolean result = robotManager.placeAtCenter(row, col, dataManager.getAllCells());
        if (result) {
            // Set orientation after placement
            for (int i = 0; i < orientation; i++) {
                robotManager.turnRight(dataManager.getAllCells());
            }
            notifyChangedIfEnabled();
        }
        return result;
    }

    public void clearRobot() {
        robotManager.clearRobot(dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    public void removeRobot() {
        clearRobot();
    }

    public void turnRobotLeft() {
        robotManager.turnLeft(dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    public void turnRobotRight() {
        robotManager.turnRight(dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    public boolean moveRobot(int dRow, int dCol) {
        boolean result = robotManager.moveRobot(dRow, dCol, dataManager.getAllCells());
        if (result) notifyChangedIfEnabled();
        return result;
    }

    public boolean updateRobotPosition(int x, int y, String direction) {
        boolean result = robotManager.updatePosition(x, y, direction, dataManager.getAllCells());
        if (result) notifyChangedIfEnabled();
        return result;
    }

    // Robot preview methods
    public boolean showTemporaryRobotAtCenter(int centerRow, int centerCol) {
        boolean result = robotManager.showTemporaryRobotAtCenter(centerRow, centerCol, dataManager.getAllCells());
        if (result) notifyChangedIfEnabled();
        return result;
    }

    public void clearTemporaryRobotPreview() {
        robotManager.clearTemporaryRobotPreview(dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    public boolean confirmTemporaryRobotPlacement() {
        boolean result = robotManager.confirmTemporaryRobotPlacement(dataManager.getAllCells());
        if (result) notifyChangedIfEnabled();
        return result;
    }

    public int getTempRobotCenterRow() {
        return robotManager.getTempCenterRow();
    }

    public int getTempRobotCenterCol() {
        return robotManager.getTempCenterCol();
    }

    // Obstacle operations
    public void setObstacle(int row, int col, boolean isObstacleCell) {
        if (isObstacleCell) {
            obstacleManager.setObstacle(row, col, dataManager.getAllCells());
        } else {
            obstacleManager.removeObstacle(row, col, dataManager.getAllCells());
        }
        notifyChangedIfEnabled();
    }

    public boolean removeObstacle(int row, int col) {
        boolean result = obstacleManager.removeObstacle(row, col, dataManager.getAllCells());
        if (result) notifyChangedIfEnabled();
        return result;
    }

    public void clearObstacles() {
        obstacleManager.clearAllObstacles(dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    public void clearAllObstacles() {
        clearObstacles();
    }

    public void clearTemporaryObstacle(int row, int col) {
        obstacleManager.clearTemporaryObstacle(row, col, dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    public boolean moveObstaclePreserveNumber(int fromRow, int fromCol, int toRow, int toCol) {
        boolean result = obstacleManager.moveObstaclePreserveNumber(fromRow, fromCol, toRow, toCol, dataManager.getAllCells());
        if (result) notifyChangedIfEnabled();
        return result;
    }

    public void highlightSelectedObstacle(int row, int col) {
        obstacleManager.highlightSelectedObstacle(row, col, dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    public void clearSelectedObstacleHighlight(int row, int col) {
        obstacleManager.clearSelectedObstacleHighlight(row, col, dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    public int getNextObstacleNumber() {
        return obstacleManager.getNextObstacleNumber();
    }

    // Target operations
    public boolean setObstacleAsTarget(int obstacleNumber, String targetId) {
        boolean result = obstacleManager.setObstacleAsTarget(obstacleNumber, targetId, dataManager.getAllCells());
        if (result) notifyChangedIfEnabled();
        return result;
    }

    public boolean removeTargetFromObstacle(int obstacleNumber) {
        boolean result = obstacleManager.removeTargetFromObstacle(obstacleNumber, dataManager.getAllCells());
        if (result) notifyChangedIfEnabled();
        return result;
    }

    public void clearAllTargets() {
        obstacleManager.clearAllTargets(dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    // Highlighting operations
    public void highlightObstacleBorders(String direction, int borderColor) {
        highlightManager.highlightObstacleBorders(direction, borderColor, dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    public void clearBorderHighlights() {
        highlightManager.clearBorderHighlights(dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    public void highlightCellBorder(int row, int col, int borderColor) {
        highlightManager.highlightCellBorder(row, col, borderColor, dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    public void highlightCellBorder(int row, int col, int borderColor, String direction) {
        highlightManager.highlightCellBorder(row, col, borderColor, direction, dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    public void clearCellBorder(int row, int col) {
        highlightManager.clearCellBorder(row, col, dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    public void applyTempRowColHighlight(int tempRow, int tempCol) {
        highlightManager.applyTempRowColHighlight(tempRow, tempCol, dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    public void clearTempRowColHighlight() {
        highlightManager.clearTempRowColHighlight(dataManager.getAllCells());
        notifyChangedIfEnabled();
    }

    // Query methods - delegate to data manager
    public boolean isCellObstacle(int row, int col) {
        return dataManager.isCellObstacle(row, col);
    }

    public boolean isCellPermanentObstacle(int row, int col) {
        return dataManager.isCellPermanentObstacle(row, col);
    }

    public boolean isCellTemporaryObstacle(int row, int col) {
        return dataManager.isCellTemporaryObstacle(row, col);
    }

    public boolean isCellRobot(int row, int col) {
        return dataManager.isCellRobot(row, col);
    }

    public boolean isCellTarget(int row, int col) {
        return dataManager.isCellTarget(row, col);
    }

    public String getCellTargetId(int row, int col) {
        return dataManager.getCellTargetId(row, col);
    }

    public int getObstacleNumber(int row, int col) {
        return dataManager.getObstacleNumberAt(row, col);
    }

    public int getObstacleNumberAt(int row, int col) {
        return dataManager.getObstacleNumberAt(row, col);
    }

    public boolean hasCellBorder(int row, int col) {
        return dataManager.hasCellBorder(row, col);
    }

    public String getCellBorderDirection(int row, int col) {
        return dataManager.getCellBorderDirection(row, col);
    }

    public boolean isVisuallyPermanentObstacle(int row, int col) {
        return dataManager.isVisuallyPermanentObstacle(row, col);
    }

    // Legacy methods that are no longer needed but kept for compatibility
    @Deprecated
    public void assignObstacleNumber(int row, int col) {
        // This functionality is now handled automatically in setObstacle
    }

    @Deprecated
    public void removeObstacleNumber(int row, int col) {
        // This functionality is now handled in removeObstacle
    }
}
