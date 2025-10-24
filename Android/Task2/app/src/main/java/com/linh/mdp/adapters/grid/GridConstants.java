package com.linh.mdp.adapters.grid;

import android.graphics.Color;

/**
 * Constants for the grid system
 */
public class GridConstants {
    public static final int GRID_SIZE = 21; // 21x21 grid for 0-19 indices with headers
    public static final int TOTAL_CELLS = GRID_SIZE * GRID_SIZE;
    public static final int DATA_SIZE = GRID_SIZE - 1; // 0-19 data rows/cols

    // Colors
    public static final int HEADER_COLOR = Color.parseColor("#2196F3");
    public static final int DEFAULT_CELL_COLOR = Color.parseColor("#E0E0E0");
    public static final int OBSTACLE_COLOR = Color.parseColor("#FF5722");
    public static final int ROBOT_COLOR = Color.parseColor("#03A9F4");
    public static final int ROBOT_FRONT_COLOR = Color.parseColor("#FFEB3B");
    public static final int SELECTED_COLOR = Color.parseColor("#FFEB3B");
    public static final int HIGHLIGHT_COLOR = Color.parseColor("#B2EBF2");
    public static final int TEMP_ROBOT_COLOR = Color.parseColor("#B3E5FC");

    // Robot orientations
    public static final int ORIENTATION_NORTH = 0;
    public static final int ORIENTATION_EAST = 1;
    public static final int ORIENTATION_SOUTH = 2;
    public static final int ORIENTATION_WEST = 3;

    // Text sizes
    public static final float NORMAL_TEXT_SIZE = 5f;
    public static final float TARGET_TEXT_SIZE = 12f;
}
