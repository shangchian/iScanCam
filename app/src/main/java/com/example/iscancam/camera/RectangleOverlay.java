package com.example.iscancam.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

/*
因爲 RectangleOverlay 類繼承了 View，所以它可以像其他視圖組件一樣使用。當您將 RectangleOverlay 添加到佈局中時，它會與其他視圖一起顯示。在這種情況下，它會與相機預覽一起顯示，使矩形框顯示在預覽的頂部。

onDraw() 方法在 RectangleOverlay 類中被重寫，它負責繪制矩形框。Canvas 對象作為參數傳遞給 onDraw()，您可以使用 Canvas API 在其上繪制矩形框。

因此，當您在相機預覽的根佈局（例如 FrameLayout）中添加 RectangleOverlay 時，它會與預覽視圖一起顯示，從而實現疊加的效果

 */
public class RectangleOverlay extends View {
    private ArrayList<Rect> mRectList = new ArrayList<>();
    private Paint mPaint;

    public RectangleOverlay(Context context) {
        super(context);
        init();
    }

    public RectangleOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mRectList.add(new Rect(100, 100, 400, 400));
        mPaint = new Paint();
        mPaint.setColor(Color.RED);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(5.0f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Rect rect : mRectList) {
            canvas.drawRect(rect, mPaint);
        }
    }

    // 動態調整矩形方框的位置和大小。
    public void setRectanglePosition(int rectIndex, int left, int top, int right, int bottom) {
        mRectList.get(rectIndex).set(left, top, right, bottom);
        invalidate(); // 通知視圖重繪
    }

    public void setRectangleSize(int rectIndex, int width, int height) {
        int centerX = mRectList.get(rectIndex).centerX();
        int centerY = mRectList.get(rectIndex).centerY();
        mRectList.get(rectIndex).set(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2);
        mRectList.get(rectIndex).set(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2);
        invalidate(); // 通知視圖重繪
    }

    public void drawRectByTargetCoordinate(JSONArray targetCoordinateArray) {
        mRectList.clear();
        try {
            for (int i = 0; i < targetCoordinateArray.length(); i++) {
                JSONArray coordinateArray = targetCoordinateArray.getJSONArray(i);
                int x1 = coordinateArray.getInt(0);
                int y1 = coordinateArray.getInt(1);
                int x2 = coordinateArray.getInt(2);
                int y2 = coordinateArray.getInt(3);
                mRectList.add(new Rect((int) x1, (int) y1, (int) x2, (int) y2));
            }
            invalidate(); // 通知視圖重繪
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
