package com.bytedance.krcview;

import static java.util.Collections.emptyList;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader.TileMode;
import android.graphics.Typeface;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.service.quickaccesswallet.GetWalletCardsError;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.LayoutInflaterCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ItemDecoration;
import androidx.recyclerview.widget.RecyclerView.OnScrollListener;
import androidx.recyclerview.widget.RecyclerView.State;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import com.bytedance.krcview.KrcLineInfo.Word;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

/**
 * Author：Shengde·Cen on 2023/2/15 14:09
 *
 * explain：
 */
public class KrcView extends FrameLayout {

    private static final String TAG = "KrcView";
    // 从 krc 歌词文件解析得到的数据。
    private List<KrcLineInfo> krcData = emptyList();

    // 当前高亮歌词行的索引
    private int curLineIndex = -1;
    // 上一次高亮歌词行的索引
    private int lastCurLineIndex = -1;
    // 进度。单位：毫秒。
    private long progress = 0L;
    // 单行歌词最大限制字数，超过字数则换行。
    private int maxWordsPerLine = 7;

    // 当前行高亮歌词颜色。（即已唱部分歌词颜色）
    @ColorInt
    private int currentLineHLTextColor = 0;

    // 当前行普通歌词颜色。（即当前行未唱部分歌词颜色）
    @ColorInt
    private int currentLineTextColor = 0;
    // 其他行普通歌词颜色。
    @ColorInt
    private int normalTextColor = 0;

    // 歌词行间距，单位：像素。
    private float lineSpace = 0f;
    // 当前行歌词具体控件顶部距离。单位：像素。
    private int currentLineTopOffset;

    private final TextPaint textPaint = new TextPaint();
    private final Paint maxTextPaint = new Paint();
    private int textScaleAnimDuration = 200;


    private onDraggingListener onDraggingListener;


    // locatedView 延迟消失的时间，单位：毫秒。
    private long hideLocatedViewDelayTimeMs = 3000;

    private final RecyclerView recyclerView;
    // 拖拽歌词的时候出现在当前行底部的那个view。
    private View locatedView;
    private int locateViewTopOffset = 0;
    private boolean isUserInputEnabled = true;


    private final Runnable hideLocatedViewTask = () -> {
        if (locatedView == null || locatedView.getVisibility() != View.VISIBLE) {
            return;
        }
        locatedView.setVisibility(View.INVISIBLE);
    };

    private static void logI(String msg) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, msg);
        }
    }

    private final LinearSmoothScroller topSmoothScroller;

    public KrcView(@NonNull Context context) {
        this(context, null);
    }

    public KrcView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KrcView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        recyclerView = new RecyclerView(context) {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                if (!isUserInputEnabled && e.getAction() == MotionEvent.ACTION_MOVE) {
                    return true;
                }
                return super.onTouchEvent(e);
            }
        };
        final LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(recyclerView, lp);
        topSmoothScroller = new LinearSmoothScroller(getContext()) {
            public int getVerticalSnapPreference() {
                return SNAP_TO_START;
            }

            @Override
            public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                return (boxStart - viewStart) + currentLineTopOffset;
            }

            public float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                return super.calculateSpeedPerPixel(displayMetrics) * 12;
            }
        };
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setItemViewCacheSize(5);
        recyclerView.addOnScrollListener(onScrollListener);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false) {
            @Override
            public void measureChildWithMargins(@NonNull View child, int widthUsed, int heightUsed) {
                super.measureChildWithMargins(child, widthUsed, heightUsed);
                if (recyclerView.getAdapter() == null) {
                    return;
                }
                final RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) child.getLayoutParams();
                final int adapterPosition = lp.getBindingAdapterPosition();
                if (adapterPosition < 0) {
                    return;
                }
                if (adapterPosition == 0) {
                    lp.topMargin = currentLineTopOffset;
                } else {
                    lp.topMargin = 0;
                }

                if (adapterPosition == recyclerView.getAdapter().getItemCount() - 1) {
                    lp.bottomMargin =
                            KrcView.this.getHeight() - KrcView.this.getPaddingBottom() - KrcView.this.getPaddingTop() -
                                    (child.getHeight() + (int) lineSpace + currentLineTopOffset);
                } else {
                    lp.bottomMargin = 0;
                }
            }

            @Override
            protected int getExtraLayoutSpace(State state) {
                return getMeasuredHeight() >> 1;
            }
        });

        final TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.KrcView);
        initPaints(a);
        lineSpace = a.getDimension(R.styleable.KrcView_lineSpace, 0f);
        currentLineTopOffset = (int) a.getDimension(R.styleable.KrcView_current_line_top_offset, 0f);
        assert currentLineTopOffset >= 0;
        maxWordsPerLine = a.getInt(R.styleable.KrcView_maxWordsPerLine, 10);
        assert (maxWordsPerLine > 0);
        normalTextColor = readAttrColor(a, R.styleable.KrcView_normal_text_color, Color.WHITE);
        currentLineTextColor = readAttrColor(a, R.styleable.KrcView_current_line_text_color, normalTextColor);
        currentLineHLTextColor = readAttrColor(a, R.styleable.KrcView_current_line_highLight_text_color, Color.GREEN);
        isUserInputEnabled = a.getBoolean(R.styleable.KrcView_isUserInputEnabled, true);
        final int locatedViewLayoutId = a.getResourceId(R.styleable.KrcView_located_view_layout, View.NO_ID);
        if (locatedViewLayoutId != View.NO_ID) {
            locatedView = LayoutInflater.from(getContext()).inflate(locatedViewLayoutId, this, false);
            setLocatedView(locatedView);
        }
        a.recycle();
        if (lineSpace > 0f) {
            recyclerView.addItemDecoration(new ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                        @NonNull State state) {
                    final int position = parent.getChildAdapterPosition(view);
                    if (position > 0) {
                        outRect.top = (int) lineSpace;
                    }
                }
            });
        }

    }

    @ColorInt
    private int readAttrColor(TypedArray a, int index, @ColorInt int defValue) {
        int color = a.getColor(index, 0);
        if (color == 0) {
            final int colorRes = a.getResourceId(index, 0);
            if (colorRes != 0) {
                color = getResources().getColor(colorRes);
            }
        }
        return color != 0 ? color : defValue;
    }

    private void initPaints(@NonNull TypedArray a) {
        final float minTextSize = a.getDimension(R.styleable.KrcView_min_text_size, sp2px(15));
        final float maxTextSize = a.getDimension(R.styleable.KrcView_max_text_size, minTextSize);
        assert (maxTextSize >= minTextSize);
        textPaint.setTextSize(minTextSize);
        maxTextPaint.setTextSize(maxTextSize);
        textPaint.setDither(true);
        textPaint.setAntiAlias(true);
    }


    private float sp2px(final float sp) {
        final float fontScale = getResources().getDisplayMetrics().scaledDensity;
        return (sp * fontScale + 0.5f);
    }


    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (locatedView != null && locateViewTopOffset > 0) {
            locatedView.offsetTopAndBottom(locateViewTopOffset);
        }
    }

    public void setTextScaleAnimDuration(@IntRange(from = 0, to = Integer.MAX_VALUE) int duration) {
        assert duration >= 0;
        this.textScaleAnimDuration = duration;
    }

    public boolean isUserInputEnabled() {
        return isUserInputEnabled;
    }

    public void setUserInputEnabled(boolean userInputEnabled) {
        isUserInputEnabled = userInputEnabled;
    }


    // 设置当前歌词进度。
    public final void setProgress(final long progress) {
        if (krcData == null || krcData.isEmpty()) {
            return;
        }
        // 这里说明发生了seek 事件，需要走二分法重新定位当前行歌词。用户未发生seek 行为不会走进这里，故避免了二分法复杂度。
        if (progress < this.progress || progress - this.progress > 4000) {
            this.progress = progress;
            curLineIndex = Collections.binarySearch(krcData, progress);
            curLineIndex = Math.max(-1, curLineIndex);
            updateCurrentLineState(progress);
            return;
        }

        this.progress = progress;
        // 当前进度不在歌词进度范围，忽略。
        if (progress < krcData.get(0).startTimeMs) {
            return;
        }
        // 当前进度不在歌词进度范围，忽略。
        final KrcLineInfo lastLine = krcData.get(krcData.size() - 1);
        if (progress > lastLine.endTimeMs()) {
            return;
        }
        // 正常情况下进度是持续前进的，可直接通过 curLineIndex 定位到当前行歌词，属于O1复杂度，比二分法性能更优。
        curLineIndex = Math.max(0, curLineIndex);
        for (int i = curLineIndex; i < krcData.size(); i++) {
            if (krcData.get(i).compareTo(progress) == 0) {
                curLineIndex = i;
                break;
            }
        }
        // current line is changed
        updateCurrentLineState(progress);

    }

    // 更新当前行歌词状态。
    private void updateCurrentLineState(long progress) {
        if (recyclerView.getAdapter() == null) {
            return;
        }
        if (curLineIndex != lastCurLineIndex) {
            if (lastCurLineIndex != -1) {
                recyclerView.getAdapter().notifyItemChanged(lastCurLineIndex, false);
            }
            if (curLineIndex != -1) {
                recyclerView.getAdapter().notifyItemChanged(curLineIndex, true);
            }
            lastCurLineIndex = curLineIndex;
            // scroll to current line
            scrollToPositionWithOffset(Math.max(0, curLineIndex));
        }
        if (curLineIndex != -1) {
            final long lineProgress = progress - krcData.get(curLineIndex).startTimeMs;
            recyclerView.getAdapter().notifyItemChanged(curLineIndex, lineProgress);
        }
    }

    public void setHideLocatedViewDelayTimeMs(long timeMs) {
        this.hideLocatedViewDelayTimeMs = timeMs;
    }

    @Nullable
    public View getLocatedView() {
        return locatedView;
    }

    // 设置拖拽歌词监听器。
    public void setOnDraggingListener(@NonNull onDraggingListener listener) {
        this.onDraggingListener = listener;
    }


    // 设置 LocatedView。note:LocatedView 的垂直中心点与当前行歌词bottom 对齐。
    public void setLocatedView(View view) {
        if (view == null) {
            return;
        }
        if (view.getParent() != null) {
            throw new RuntimeException("view.getParent() must be null !");
        }
        if (view.getLayoutParams() == null) {
            final LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            view.setLayoutParams(lp);
        } else {
            ((LayoutParams) view.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        }
        locatedView = view;
        view.setVisibility(View.INVISIBLE);
        addView(view);
        post(this::updateLocateViewTopOffset);
    }


    /**
     * 设置数据
     *
     * @param data krc 数据
     */
    public void setKrcData(List<KrcLineInfo> data) {
        if (data == null) {
            return;
        }
        krcData = data;
        /**
         * 将 KrcLineInfo 数据集组织成一条链。方便当前 progress 通过二分法能快速定位到当前行歌词。
         * @see #setProgress(long)
         */
        for (int i = 0; i < data.size(); i++) {
            final int next = i + 1;
            if (next < data.size()) {
                data.get(i).nextKrcLineInfo = data.get(next);
            }
        }
        recyclerView.setAdapter(new AdapterImpl());
        post(this::updateLocateViewTopOffset);
    }


    private void scrollToPositionWithOffset(int position) {
        if (position < 0 || position >= krcData.size() || recyclerView.getLayoutManager() == null) {
            return;
        }
        topSmoothScroller.setTargetPosition(position);
        recyclerView.getLayoutManager().startSmoothScroll(topSmoothScroller);
    }


    /**
     * 将画笔暴露给用户随意设置字体外观。
     *
     * @return textPaint
     */
    @NonNull
    public TextPaint getTextPaint() {
        return textPaint;
    }

    public void setMinTextSize(float size) {
        textPaint.setTextSize(size);
    }

    public void setMaxTextSize(float size) {
        maxTextPaint.setTextSize(size);
    }


    private class AdapterImpl extends RecyclerView.Adapter<ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View krcLineView = new KrcLineView(parent.getContext());
            krcLineView.setLayoutParams(
                    new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ViewHolder(krcLineView) {
            };
        }

        @Override
        public int getItemCount() {
            return krcData == null ? 0 : krcData.size();
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (payloads.isEmpty()) {
                onBindViewHolder(holder, position);
                return;
            }
            final KrcLineView krcLineView = (KrcLineView) holder.itemView;
            for (Object payload : payloads) {
                if (payload instanceof Boolean) {
                    krcLineView.setIsCurrentLine((Boolean) payload);
                } else if (payload instanceof Long) {
                    krcLineView.setLineProgress((Long) payload);
                }
            }

        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
            final KrcLineView krcLineView = (KrcLineView) holder.itemView;
            final KrcLineInfo lineInfo = krcData.get(position);
            krcLineView.reset();
            krcLineView.setIsCurrentLine(position == curLineIndex);
            krcLineView.setKrcLineInfo(lineInfo);
            krcLineView.bindPosition = position;
        }
    }

    private class KrcLineView extends View implements AnimatorUpdateListener {

        private static final String TAG = "KrcLineView";

        private KrcLineInfo krcLineInfo;

        private boolean isCurrentLine;

        private long lineProgress;
        private int bindPosition;

        private final int[] currentLineColors = new int[2];
        private final float[] currentLineColorPositions = new float[2];
        private final ValueAnimator curLineTextScaleAnima = new ValueAnimator();
        private Method drawTextMethod;
        // 实现单句过长歌词自动换行的工具类
        private StaticLayout staticLayout;
        private final TextPaint lineTextPaint = new TextPaint();


        public KrcLineView(Context context) {
            this(context, null);
        }

        public KrcLineView(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public KrcLineView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            initView();
            curLineTextScaleAnima.addUpdateListener(this);
            curLineTextScaleAnima.setDuration(textScaleAnimDuration);
            curLineTextScaleAnima.setInterpolator(new LinearInterpolator());
            lineTextPaint.set(textPaint);
        }

        private void initView() {
            setIsCurrentLine(false);
            currentLineColors[0] = currentLineHLTextColor;
            currentLineColors[1] = currentLineTextColor;
        }


        void setKrcLineInfo(KrcLineInfo info) {
            if (info == null || TextUtils.isEmpty(info.text) || info.equals(krcLineInfo)) {
                return;
            }
            krcLineInfo = info;
            float previousWordsWidth = 0;
            if (info.words != null) {
                // 将一句歌词的每个字（Word）组织成一条链，方便后面设置lineProgress通过二分法快速定位到当前歌词。
                for (int i = 0; i < info.words.size(); i++) {
                    final Word word = info.words.get(i);
                    /**
                     * 为了后面能够一步到位算出当前行已唱部分歌词长度，这里需要提前将当前歌词（word）之前的所有歌词的最大总长度记录下来。
                     * @see #calculateHighLightWidth(long)
                     */
                    word.previousWordsWidth = previousWordsWidth;
                    word.textWidth = maxTextPaint.measureText(word.text);
                    previousWordsWidth += word.textWidth;
                    final int next = i + 1;
                    if (next < info.words.size()) {
                        word.next = info.words.get(next);
                    }
                }
            }
            KrcLineView.this.requestLayout();
        }

        void setLineProgress(final long lineProgress) {
            if (krcLineInfo == null || krcLineInfo.words == null || krcLineInfo.words.isEmpty() || !isCurrentLine) {
                return;
            }
            final Word last = krcLineInfo.words.get(krcLineInfo.words.size() - 1);
            if (lineProgress > last.endTimeMs()) {
                return;
            }
            this.lineProgress = lineProgress;
            KrcLineView.this.invalidate();
        }


        void setIsCurrentLine(boolean isCurrentLine) {
            if (this.isCurrentLine == isCurrentLine) {
                return;
            }
            this.isCurrentLine = isCurrentLine;
            final float minTextSize = textPaint.getTextSize();
            final float maxTextSize = maxTextPaint.getTextSize();
            assert maxTextSize >= minTextSize;
            if (maxTextSize > minTextSize) {
                curLineTextScaleAnima.cancel();
                if (isCurrentLine) {
                    curLineTextScaleAnima.setFloatValues(minTextSize, maxTextSize);
                } else {
                    curLineTextScaleAnima.setFloatValues(maxTextSize, minTextSize);
                }
                curLineTextScaleAnima.start();
            }
        }

        void reset() {
            lineProgress = 0;
            bindPosition = 0;
        }

        @SuppressLint("DrawAllocation")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

            if (checkKrcDataInvalid()) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            // 必须按照text最大尺寸 测量
            lineTextPaint.setTextSize(maxTextPaint.getTextSize());
            float contentWidth;
            final int widthMeasureMode = MeasureSpec.getMode(widthMeasureSpec);
            switch (widthMeasureMode) {
                case MeasureSpec.AT_MOST:
                case MeasureSpec.UNSPECIFIED:
                    contentWidth =
                            maxTextPaint.measureText(krcLineInfo.text) + getPaddingStart() + getPaddingEnd();
                    logI("===> onMeasure:width MeasureSpec.AT_MOST,contentWidth: " + contentWidth);
                    break;
                case MeasureSpec.EXACTLY:
                default:
                    contentWidth = MeasureSpec.getSize(widthMeasureSpec);
                    break;
            }
            final int localMaxWordsPerLine = Math.min(maxWordsPerLine, krcLineInfo.words.size());
            float maxWordsWidth = 0;
            for (int i = 0; i < localMaxWordsPerLine; i++) {
                maxWordsWidth += maxTextPaint.measureText(krcLineInfo.words.get(i).text);
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
            logI("===> onMeasure position:" + bindPosition + " object:" + KrcLineView.this.hashCode());
            lineTextPaint.setTextSize(textPaint.getTextSize());
            setMeasuredDimension((int) contentWidth, (int) contentHeight);
        }

        private boolean checkKrcDataInvalid() {
            return krcLineInfo == null || krcLineInfo.words == null;
        }


        @Override
        protected void onDraw(Canvas canvas) {
            if (canvas != null) {
                drawText(canvas);
            }
        }

        private void drawText(@NonNull Canvas canvas) {
            if (checkKrcDataInvalid() || staticLayout == null) {
                return;
            }

            canvas.save();
            final float contentWidth = getWidth() - getPaddingStart() - getPaddingEnd();
            final float translateX = (contentWidth - staticLayout.getWidth()) * 0.5f;
            final float translateY = getPaddingTop();
            canvas.translate(translateX, translateY);
            // draw current line high light text
            if (isCurrentLine) {
                final float totalHlWidth = calculateHighLightWidth(this.lineProgress);
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
                lineTextPaint.setColor(normalTextColor);
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
            if (checkKrcDataInvalid()) {
                return 0;
            }
            if (lineProgress < krcLineInfo.words.get(0).startTimeMs) {
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
    }


    private final RecyclerView.OnScrollListener onScrollListener = new OnScrollListener() {
        // 是否用户主动拖拽。用于区别RecyclerView 其他因素触发的滚动。
        private boolean isUserDragging = false;

        private KrcLineView locatedItemView;

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            switch (newState) {
                case RecyclerView.SCROLL_STATE_DRAGGING:
                    isUserDragging = true;
                    logI("===> onScrollStateChanged: SCROLL_STATE_DRAGGING");
                    tryToShowLocatedView();
                    notifyStartDragging();
                    break;

                case RecyclerView.SCROLL_STATE_IDLE:
                    logI("===> onScrollStateChanged: SCROLL_STATE_IDLE");
                    if (isUserDragging) {
                        isUserDragging = false;
                        tryToHideLocatedViewDelay();
                        notifyStopDragging();
                        locatedItemView = null;
                    }
                    //
                    else {
                        //
                        updateLocateViewTopOffset();
                    }
                    break;
                default:
                    break;

            }
        }

        private void tryToShowLocatedView() {
            removeCallbacks(hideLocatedViewTask);
            if (locatedView == null || locatedView.getVisibility() == View.VISIBLE) {
                return;
            }
            locatedView.setVisibility(View.VISIBLE);
        }


        private void tryToHideLocatedViewDelay() {
            if (locatedView == null || locatedView.getVisibility() != VISIBLE) {
                return;
            }
            postDelayed(hideLocatedViewTask, hideLocatedViewDelayTimeMs);
        }

        private void notifyStartDragging() {
            if (onDraggingListener == null) {
                return;
            }
            final ViewHolder cur = recyclerView.findViewHolderForAdapterPosition(curLineIndex == -1 ? 0 : curLineIndex);
            if (cur != null) {
                locatedItemView = (KrcLineView) cur.itemView;
                onDraggingListener.onStartDragging(KrcView.this, locatedItemView.krcLineInfo,
                        locatedItemView.bindPosition);
            }
        }

        private void notifyStopDragging() {
            if (onDraggingListener == null || locatedItemView == null) {
                return;
            }
            onDraggingListener.onStopDragging(KrcView.this, locatedItemView.krcLineInfo, locatedItemView.bindPosition);
        }

        @Nullable
        private KrcLineView getLocatedItemView() {
            if (locatedView == null || locatedView.getVisibility() != View.VISIBLE) {
                return null;
            }
            final float centerX = locatedView.getLeft() + (locatedView.getWidth() >> 1);
            final float centerY = locatedView.getTop() + (locatedView.getHeight() >> 1);
            final View view = recyclerView.findChildViewUnder(centerX, centerY);
            return view instanceof KrcLineView ? (KrcLineView) view : null;
        }

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            logI("===>onScrolled: ");
            if (isUserDragging) {
                logI("===>onScrolled: isUserDragging");
                notifyDragging();
            }
        }

        private void notifyDragging() {
            if (onDraggingListener == null || locatedItemView == null) {
                return;
            }
            final KrcLineView cur = getLocatedItemView();
            if (cur != null) {
                final boolean positionChanged = locatedItemView != cur;
                if (positionChanged) {
                    locatedItemView = cur;
                }
                onDraggingListener.onDragging(KrcView.this, positionChanged, cur.krcLineInfo,
                        cur.bindPosition);
            }
        }
    };

    private void updateLocateViewTopOffset() {
        if (locatedView == null || locatedView.getVisibility() == View.VISIBLE) {
            return;
        }
        final ViewHolder curVH = recyclerView.findViewHolderForAdapterPosition(
                curLineIndex == -1 ? 0 : curLineIndex);
        if (curVH == null) {
            return;
        }
        final int newLocateViewTopOffset = curVH.itemView.getBottom() - (locatedView.getHeight() >> 1);
        if (newLocateViewTopOffset != locateViewTopOffset) {
            locateViewTopOffset = newLocateViewTopOffset;
            logI("===>updateLocateViewTopOffset: " + locateViewTopOffset);
            requestLayout();
        }
    }

    public interface onDraggingListener {

        /**
         * 开始拖动歌词。类似于 down 事件。{@link RecyclerView#SCROLL_STATE_DRAGGING}
         *
         * @param info 当前行歌词信息。
         * @param position 当前行所在RecyclerView 中的位置。
         */
        default void onStartDragging(@NonNull KrcView krcView, @NonNull KrcLineInfo info, int position) {
        }

        /**
         * 歌词拖拽中。类似于 move 事件。{@link RecyclerView.OnScrollListener#onScrolled}
         *
         * @param positionChanged 位置是否已发生改变。
         * @param info locatedView 中心点所在的歌词行（不一定是当前行）
         * @param position locatedView 中心点所在歌词行的位置。
         */
        default void onDragging(@NonNull KrcView krcView, boolean positionChanged, @NonNull KrcLineInfo info,
                int position) {
        }

        /**
         * 停止拖拽。{@link RecyclerView#SCROLL_STATE_IDLE}
         *
         * @param info locatedView 中心点所在的歌词行（不一定是当前行）
         * @param position locatedView 中心点所在歌词行的位置。
         */
        default void onStopDragging(@NonNull KrcView krcView, @NonNull KrcLineInfo info, int position) {
        }
    }

}
