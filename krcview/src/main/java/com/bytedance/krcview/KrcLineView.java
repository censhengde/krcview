package com.bytedance.krcview;

import static com.bytedance.krcview.KrcView.logI;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Shader.TileMode;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bytedance.krcview.KrcLineInfo.Word;
import com.bytedance.krcview.KrcView.LineHolder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

/**
 * Author：censhengde on 2023/10/16 14:26
 *
 * explain：
 */
public class KrcLineView extends View implements AnimatorUpdateListener, LineHolder.CallBack {

    private static final String TAG = "KrcLineView";

    final int[] currentLineColors = new int[2];
    private final float[] currentLineColorPositions = new float[2];
    final ValueAnimator curLineTextScaleAnima = new ValueAnimator();
    private Method drawTextMethod;
    // 实现单句过长歌词自动换行的工具类
    private StaticLayout staticLayout;
    final TextPaint lineTextPaint = new TextPaint();
    private LineHolder lineHolder;


    public KrcLineView(Context context) {
        this(context, null);
    }

    public KrcLineView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KrcLineView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        curLineTextScaleAnima.addUpdateListener(this);
        curLineTextScaleAnima.setInterpolator(new LinearInterpolator());
    }


    @Override
    public void attachedViewHolder(@NonNull LineHolder holder) {
        lineHolder = holder;
        currentLineColors[0] = holder.krcView.currentLineHLTextColor;
        currentLineColors[1] = holder.krcView.currentLineTextColor;
        curLineTextScaleAnima.setDuration(holder.krcView.textScaleAnimDuration);
        lineTextPaint.set(holder.krcView.textPaint);
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        if (checkLineDataInvalid()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        final KrcLineInfo krcLineInfo = lineHolder.getKrcLineInfo();

        // 必须按照text最大尺寸 测量
        final float maxTextSize = lineHolder.krcView.maxTextPaint.getTextSize();
        lineTextPaint.setTextSize(maxTextSize);
        float contentWidth;
        final int widthMeasureMode = MeasureSpec.getMode(widthMeasureSpec);
        switch (widthMeasureMode) {
            case MeasureSpec.AT_MOST:
            case MeasureSpec.UNSPECIFIED:
                contentWidth =
                        lineTextPaint.measureText(krcLineInfo.text) + getPaddingStart()
                                + getPaddingEnd();
                logI("===> onMeasure:width MeasureSpec.AT_MOST,contentWidth: " + contentWidth);
                break;
            case MeasureSpec.EXACTLY:
            default:
                contentWidth = MeasureSpec.getSize(widthMeasureSpec);
                break;
        }
        final int localMaxWordsPerLine = Math.min(lineHolder.krcView.maxWordsPerLine, krcLineInfo.words.size());
        float maxWordsWidth = 0;
        for (int i = 0; i < localMaxWordsPerLine; i++) {
            maxWordsWidth += lineTextPaint.measureText(krcLineInfo.words.get(i).text);
        }

        if (staticLayout == null || !staticLayout.getText().equals(krcLineInfo.text)) {
            staticLayout = new StaticLayout(krcLineInfo.text, lineTextPaint, (int) maxWordsWidth,
                    Layout.Alignment.ALIGN_CENTER, 1f, 0.0f, false);
        }

        if (widthMeasureMode == MeasureSpec.AT_MOST || widthMeasureMode == MeasureSpec.UNSPECIFIED) {
            contentWidth = maxWordsWidth;
        }

        float contentHeight;
        final int heightMeasureMode = MeasureSpec.getMode(heightMeasureSpec);
        switch (heightMeasureMode) {
            case MeasureSpec.AT_MOST:
            case MeasureSpec.UNSPECIFIED:
                contentHeight =
                        (staticLayout == null ? 0 : staticLayout.getHeight()) + getPaddingTop()
                                + getPaddingBottom();
                break;
            case MeasureSpec.EXACTLY:
            default:
                contentHeight = MeasureSpec.getSize(heightMeasureSpec);
                break;
        }
        logI("===> onMeasure position:" + lineHolder.getBindingAdapterPosition() + " object:"
                + KrcLineView.this.hashCode());

        // 恢复最小尺寸。
        final float minTextSize = lineHolder.krcView.textPaint.getTextSize();
        lineTextPaint.setTextSize(minTextSize);
        setMeasuredDimension((int) contentWidth, (int) contentHeight);
    }

    private boolean checkLineDataInvalid() {
        return lineHolder == null
                || lineHolder.getKrcLineInfo() == null
                || lineHolder.getKrcLineInfo().words.isEmpty();
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (canvas != null) {
            drawText(canvas);
        }
    }

    private void drawText(@NonNull Canvas canvas) {
        if (checkLineDataInvalid() || staticLayout == null) {
            return;
        }

        canvas.save();
        final float contentWidth = getWidth() - getPaddingStart() - getPaddingEnd();
        final float translateX = (contentWidth - staticLayout.getWidth()) * 0.5f;
        final float translateY = getPaddingTop();
        canvas.translate(translateX, translateY);
        // draw current line high light text
        if (lineHolder.isCurrentLine()) {
            final float totalHlWidth = calculateHighLightWidth(lineHolder.getLineProgress());
            float totalTextWidth = 0;
            for (int i = 0; i < staticLayout.getLineCount(); i++) {
                float lineTextWidth = staticLayout.getLineWidth(i);
                totalTextWidth += lineTextWidth;
                final float left = (staticLayout.getWidth() - lineTextWidth) * 0.5f;
                final float right = left + lineTextWidth;
                if (totalHlWidth >= totalTextWidth) {
                    currentLineColorPositions[0] = 1.0f;
                    currentLineColorPositions[1] = 0.0f;

                } else if ((totalTextWidth - totalHlWidth) < lineTextWidth) {
                    final float pos = 1.0f - (totalTextWidth - totalHlWidth) / lineTextWidth;
                    currentLineColorPositions[0] = pos;
                    currentLineColorPositions[1] = pos;
                } else {
                    currentLineColorPositions[0] = 0;
                    currentLineColorPositions[1] = 0;
                }
                final LinearGradient linearGradient = new LinearGradient(left, 0, right, 0, currentLineColors,
                        currentLineColorPositions, TileMode.CLAMP);
                lineTextPaint.setShader(linearGradient);
                drawLineText(canvas, i);
            }

        }
        // draw normal text
        else {
            lineTextPaint.setShader(null);
            lineTextPaint.setColor(lineHolder.krcView.normalTextColor);
            for (int i = 0; i < staticLayout.getLineCount(); i++) {
                drawLineText(canvas, i);
            }
        }
        canvas.restore();
    }

    @SuppressLint({"SoonBlockedPrivateApi", "DiscouragedPrivateApi"})
    private void drawLineText(Canvas canvas, int line) {
        if (staticLayout == null || canvas == null) {
            return;
        }
        try {
            final String methodName = "drawText";
            if (VERSION.SDK_INT >= VERSION_CODES.P) {
                HiddenApiBypass.invoke(Layout.class, staticLayout, methodName, canvas, line, line);
            } else {
                if (drawTextMethod == null) {
                    drawTextMethod = Layout.class.getDeclaredMethod(methodName, Canvas.class, int.class, int.class);
                    drawTextMethod.setAccessible(true);
                }
                drawTextMethod.invoke(staticLayout, canvas, line, line);
            }

        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }

    }


    /**
     * 计算当前行高亮文字的宽度
     *
     * @param lineProgress 相对当前行歌词开始时间的进度。
     * @return 当前行高亮文字的宽度
     */
    private float calculateHighLightWidth(long lineProgress) {
        if (checkLineDataInvalid()) {
            return 0;
        }
        final KrcLineInfo krcLineInfo = lineHolder.getKrcLineInfo();
        if (lineProgress <= krcLineInfo.words.get(0).startTimeMs) {
            return 0;
        }

        final int curWordIndex = Collections.binarySearch(krcLineInfo.words, lineProgress);
        if (curWordIndex < 0) {
            return 0;
        }
        final Word curWord = krcLineInfo.words.get(curWordIndex);
        if (curWord.duration == 0L) {
            return curWord.previousWordsWidth;
        }

        final long cutoffDuration = Math.min(curWord.duration, lineProgress - curWord.startTimeMs);
        if (cutoffDuration <= 0) {
            return 0;
        }
        final float curWordWidth = curWord.textWidth;
        final float curWordHLWidth = Math.min(curWordWidth, curWordWidth / curWord.duration * cutoffDuration);
        return curWord.previousWordsWidth + curWordHLWidth;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        final float size = (float) animation.getAnimatedValue();
        lineTextPaint.setTextSize(size);
        invalidate();
    }

    @Override
    public void isCurrentLineChanged(@NonNull LineHolder holder, int currentLineIndex) {
        final float minTextSize = lineHolder.krcView.textPaint.getTextSize();
        final float maxTextSize = lineHolder.krcView.maxTextPaint.getTextSize();
        assert maxTextSize >= minTextSize;
        if (maxTextSize > minTextSize) {
            curLineTextScaleAnima.cancel();
            if (holder.isCurrentLine()) {
                curLineTextScaleAnima.setFloatValues(minTextSize, maxTextSize);
            } else {
                curLineTextScaleAnima.setFloatValues(maxTextSize, minTextSize);
            }
            curLineTextScaleAnima.start();
        }
    }

    @Override
    public void onLineProgressChanged(@NonNull LineHolder holder, long lineProgress) {
        this.invalidate();
    }

    @Override
    public void onLineInfoChanged(@NonNull LineHolder holder, KrcLineInfo info) {
        this.requestLayout();
    }
}

