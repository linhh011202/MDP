package com.linh.mdp.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * Custom TextView that can display colored borders on specific sides
 */
public class BorderableCell extends AppCompatTextView {

    private Paint borderPaint;
    private int borderColor = 0;
    private int borderWidth = 8; // Border thickness in pixels
    private boolean showNorthBorder = false;
    private boolean showSouthBorder = false;
    private boolean showEastBorder = false;
    private boolean showWestBorder = false;

    public BorderableCell(Context context) {
        super(context);
        init();
    }

    public BorderableCell(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BorderableCell(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.FILL);
        borderPaint.setAntiAlias(true);
    }

    public void setBorder(String direction, int color) {
        clearBorders();
        borderColor = color;
        borderPaint.setColor(color);

        switch (direction.toUpperCase()) {
            case "N":
                showNorthBorder = true;
                break;
            case "S":
                showSouthBorder = true;
                break;
            case "E":
                showEastBorder = true;
                break;
            case "W":
                showWestBorder = true;
                break;
        }
        invalidate();
    }

    public void setBorderDirection(String direction) {
        clearBorders();
        if (direction != null && !direction.isEmpty()) {
            // Handle multiple directions
            if (direction.contains("N")) showNorthBorder = true;
            if (direction.contains("S")) showSouthBorder = true;
            if (direction.contains("E")) showEastBorder = true;
            if (direction.contains("W")) showWestBorder = true;
            if (direction.equals("ALL")) {
                showNorthBorder = true;
                showSouthBorder = true;
                showEastBorder = true;
                showWestBorder = true;
            }
        }
        invalidate();
    }

    public void setBorderColor(int color) {
        borderColor = color;
        borderPaint.setColor(color);
        invalidate();
    }

    public void clearBorders() {
        showNorthBorder = false;
        showSouthBorder = false;
        showEastBorder = false;
        showWestBorder = false;
        invalidate();
    }

    public boolean hasBorders() {
        return showNorthBorder || showSouthBorder || showEastBorder || showWestBorder;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (hasBorders()) {
            int width = getWidth();
            int height = getHeight();

            if (showNorthBorder) {
                // Draw top border
                canvas.drawRect(0, 0, width, borderWidth, borderPaint);
            }

            if (showSouthBorder) {
                // Draw bottom border
                canvas.drawRect(0, height - borderWidth, width, height, borderPaint);
            }

            if (showWestBorder) {
                // Draw left border
                canvas.drawRect(0, 0, borderWidth, height, borderPaint);
            }

            if (showEastBorder) {
                // Draw right border
                canvas.drawRect(width - borderWidth, 0, width, height, borderPaint);
            }
        }
    }
}
