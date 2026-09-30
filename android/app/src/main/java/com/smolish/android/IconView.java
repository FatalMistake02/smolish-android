package com.smolish.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small, density-independent line icons used by navigation and video actions. */
final class IconView extends View {
    private final String kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int ink = Color.WHITE;

    IconView(Context context, String kind) {
        super(context);
        this.kind = kind;
        setMinimumWidth(Ui.dp(context, 44));
        setMinimumHeight(Ui.dp(context, 44));
    }

    void setInk(int color) { ink = color; invalidate(); }

    @Override protected void onDraw(Canvas original) {
        super.onDraw(original);
        Canvas c = original;
        int save = c.save();
        float unit = Ui.dp(getContext(), 24) / 24f;
        c.translate((getWidth() - 24 * unit) / 2f, (getHeight() - 24 * unit) / 2f);
        c.scale(unit, unit);
        paint.setColor(ink);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.9f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        path.reset();
        switch (kind) {
            case "home":
                path.moveTo(3, 10); path.lineTo(12, 3); path.lineTo(21, 10);
                path.moveTo(5, 9); path.lineTo(5, 21); path.lineTo(19, 21); path.lineTo(19, 9);
                path.moveTo(10, 21); path.lineTo(10, 14); path.lineTo(14, 14); path.lineTo(14, 21);
                c.drawPath(path, paint); break;
            case "search":
                c.drawCircle(10.5f, 10.5f, 6.5f, paint);
                c.drawLine(15.5f, 15.5f, 21, 21, paint); break;
            case "studio":
                c.drawRoundRect(new RectF(3, 7, 21, 21), 2, 2, paint);
                path.moveTo(3, 7); path.lineTo(6, 3); path.lineTo(21, 3); path.lineTo(21, 7);
                path.moveTo(8, 3); path.lineTo(5, 7); path.moveTo(15, 3); path.lineTo(12, 7);
                path.moveTo(10, 11); path.lineTo(16, 14); path.lineTo(10, 17); path.close();
                c.drawPath(path, paint); break;
            case "bell":
                path.moveTo(5, 17); path.cubicTo(7, 15, 7, 13, 7, 10);
                path.cubicTo(7, 6, 9, 4, 12, 4); path.cubicTo(15, 4, 17, 6, 17, 10);
                path.cubicTo(17, 13, 17, 15, 19, 17); path.lineTo(19, 19); path.lineTo(5, 19); path.close();
                c.drawPath(path, paint); c.drawArc(new RectF(10, 17, 14, 22), 0, 180, false, paint); break;
            case "person":
                c.drawCircle(12, 8, 4, paint);
                c.drawArc(new RectF(4, 13, 20, 25), 180, 180, false, paint); break;
            case "back":
                c.drawLine(19, 12, 5, 12, paint);
                c.drawLine(5, 12, 11, 6, paint); c.drawLine(5, 12, 11, 18, paint); break;
            case "close":
                c.drawLine(6, 6, 18, 18, paint); c.drawLine(18, 6, 6, 18, paint); break;
            case "heart":
                path.moveTo(12, 20); path.cubicTo(8, 17, 3, 13, 3, 9);
                path.cubicTo(3, 3, 9, 3, 12, 7); path.cubicTo(15, 3, 21, 3, 21, 9);
                path.cubicTo(21, 13, 16, 17, 12, 20); path.close(); c.drawPath(path, paint); break;
            case "comment":
                c.drawRoundRect(new RectF(3, 4, 21, 18), 4, 4, paint);
                path.moveTo(8, 18); path.lineTo(7, 22); path.lineTo(13, 18); c.drawPath(path, paint); break;
            case "share":
                c.drawLine(5, 19, 19, 5, paint);
                c.drawLine(10, 5, 19, 5, paint); c.drawLine(19, 5, 19, 14, paint);
                c.drawLine(4, 12, 4, 20, paint); c.drawLine(4, 20, 12, 20, paint); break;
            case "bookmark":
                path.moveTo(6, 3); path.lineTo(18, 3); path.lineTo(18, 21);
                path.lineTo(12, 17); path.lineTo(6, 21); path.close(); c.drawPath(path, paint); break;
            case "more":
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(5, 12, 1.7f, paint); c.drawCircle(12, 12, 1.7f, paint);
                c.drawCircle(19, 12, 1.7f, paint); break;
            case "refresh":
                c.drawArc(new RectF(4, 4, 20, 20), 45, 285, false, paint);
                path.moveTo(19, 5); path.lineTo(20, 11); path.lineTo(14, 10); c.drawPath(path, paint); break;
        }
        c.restoreToCount(save);
    }
}
