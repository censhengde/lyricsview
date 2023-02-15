package com.bytedance.krcview;

import static java.util.Collections.emptyList;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader.TileMode;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LogPrinter;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.LinearInterpolator;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
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
public class KrcView extends RecyclerView {

    private static final String TAG = "KrcView";
    private List<KrcLineInfo> krcData = emptyList();

    private int curLineIndex = -1;
    private int lastLineIndex = -1;
    private long progress = 0L;
    private int maxWordsPerLine = 7;

    @ColorInt
    private int currentLineHLTextColor = 0;

    @ColorInt
    private int currentLineTextColor = 0;

    @ColorInt
    private int normalTextColor = 0;

    private float minTextSize = 0f;
    private float maxTextSize = 0f;
    private float lineSpace = 0f;
    private int currentLineWithTopOffset = 4;

    private final LinearSmoothScroller topSmoothScroller;

    public KrcView(@NonNull Context context) {
        this(context, null);
    }

    public KrcView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KrcView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        topSmoothScroller = new LinearSmoothScroller(getContext()) {
            public int getVerticalSnapPreference() {
                return SNAP_TO_START;
            }

            @Override
            public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                return (boxStart - viewStart) + currentLineWithTopOffset;
            }

            public float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                return super.calculateSpeedPerPixel(displayMetrics) * 12;
            }
        };
        currentLineWithTopOffset = (int) dp2px(100);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        this.setHasFixedSize(true);

        this.setLayoutManager(new LinearLayoutManager(getContext(), VERTICAL, false) {
            @Override
            public void measureChildWithMargins(@NonNull View child, int widthUsed, int heightUsed) {
                super.measureChildWithMargins(child, widthUsed, heightUsed);
                if (getAdapter() == null) {
                    return;
                }
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                final int adapterPosition = lp.getViewAdapterPosition();
                if (adapterPosition < 0) {
                    return;
                }
                if (adapterPosition == 0) {
                    lp.topMargin = currentLineWithTopOffset;
                } else {
                    lp.topMargin = 0;
                }

                if (adapterPosition == getAdapter().getItemCount() - 1) {
                    lp.bottomMargin = KrcView.this.getHeight() - currentLineWithTopOffset;
                } else {
                    lp.bottomMargin = 0;
                }
            }

        });

        final TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.KrcView);
        minTextSize = a.getDimension(R.styleable.KrcView_min_text_size, sp2px(15));
        maxTextSize = a.getDimension(R.styleable.KrcView_max_text_size, sp2px(18));
        assert (maxTextSize >= minTextSize);
        lineSpace = a.getDimension(R.styleable.KrcView_lineSpace, 0f);
        maxWordsPerLine = a.getInt(R.styleable.KrcView_maxWordsPerLine, 10);
        normalTextColor = readAttrColor(a, R.styleable.KrcView_normal_text_color);
        currentLineTextColor = readAttrColor(a, R.styleable.KrcView_current_line_text_color);
        currentLineHLTextColor = readAttrColor(a, R.styleable.KrcView_current_line_highLight_text_color);
        a.recycle();
        if (lineSpace > 0f) {
            addItemDecoration(new ItemDecoration() {
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
    private int readAttrColor(TypedArray a, int index) {
        int color = a.getColor(index, 0);
        if (color == 0) {
            final int colorRes = a.getResourceId(index, 0);
            if (colorRes != 0) {
                color = getResources().getColor(colorRes);
            }
        }
        return color;
    }

    public final float dp2px(final float dp) {
        final float scale = getResources().getDisplayMetrics().density;
        return (dp * scale + 0.5f);
    }

    public final float sp2px(final float sp) {
        final float fontScale = getResources().getDisplayMetrics().scaledDensity;
        return (sp * fontScale + 0.5f);
    }

    public void setProgress(long progress) {
        if (krcData.isEmpty()) {
            return;
        }
        // seek event
        if (progress < this.progress || progress - this.progress > 4000) {
            this.progress = progress;
            curLineIndex = Collections.binarySearch(krcData, progress);
            curLineIndex = Math.max(-1, curLineIndex);
            updateCurrentLineState(progress);
            return;
        }

        this.progress = progress;
        if (progress < krcData.get(0).startTimeMs) {
            return;
        }
        KrcLineInfo lastLine = krcData.get(krcData.size() - 1);
        if (progress > lastLine.endTimeMs()) {
            return;
        }

        curLineIndex = Math.max(0, curLineIndex);
        int loopTime = 0;
        for (int i = curLineIndex; i < krcData.size(); i++) {
            loopTime++;
            if (krcData.get(i).compareTo(progress) == 0) {
                curLineIndex = i;
                break;
            }
        }
        Log.i(TAG, "===> setProgress:遍历次数： " + loopTime);
        // current line is changed
        updateCurrentLineState(progress);

    }

    private void updateCurrentLineState(long progress) {
        if (getAdapter() == null) {
            return;
        }
        if (curLineIndex != lastLineIndex) {
            if (lastLineIndex != -1) {
                getAdapter().notifyItemChanged(lastLineIndex, false);
            }
            if (curLineIndex != -1) {
                getAdapter().notifyItemChanged(curLineIndex, true);
            }
            lastLineIndex = curLineIndex;
            // scroll to current line
            scrollToPositionWithOffset(Math.max(0, curLineIndex));
        }
        if (curLineIndex != -1) {
            getAdapter().notifyItemChanged(curLineIndex, progress - krcData.get(curLineIndex).startTimeMs);
        }
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
        for (int i = 0; i < data.size(); i++) {
            final int next = i + 1;
            if (next < data.size()) {
                data.get(i).nextKrcLineInfo = data.get(next);
            }
        }
        setAdapter(new Adapter());
    }


    private void scrollToPositionWithOffset(int position) {
        if (position < 0 || position >= krcData.size() || getLayoutManager() == null) {
            return;
        }
        topSmoothScroller.setTargetPosition(position);
        getLayoutManager().startSmoothScroll(topSmoothScroller);
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View krcLineView = new KrcLineView(parent.getContext());
            krcLineView.setLayoutParams(
                    new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ViewHolder(krcLineView) {
            };
        }

        @Override
        public int getItemCount() {
            return krcData.size();
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (payloads.isEmpty()) {
                super.onBindViewHolder(holder, position, payloads);
                return;
            }
            final KrcLineView krcLineView = (KrcLineView) holder.itemView;
            for (Object payload : payloads) {
                if (payload instanceof Boolean) {
                    krcLineView.setCurrentLine((Boolean) payload);
                } else if (payload instanceof Long) {
                    krcLineView.setTimeMs((Long) payload);
                }
            }

        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
            final KrcLineView krcLineView = (KrcLineView) holder.itemView;
            final KrcLineInfo lineInfo = krcData.get(position);
            krcLineView.reset();
            krcLineView.setCurrentLine(position == curLineIndex);
            krcLineView.setKrcLineInfo(lineInfo);
        }
    }

    private class KrcLineView extends View implements AnimatorUpdateListener {

        private static final String TAG = "KrcLineView";

        private KrcLineInfo krcLineInfo;
        private final TextPaint currentLineTextPaint = new TextPaint();
        private final Paint maxTextSizePaint = new Paint();
        private boolean isCurrentLine;

        private long timeMs;

        private final int[] currentLineColors = new int[2];
        private final float[] currentLineColorPositions = new float[2];
        private final ValueAnimator curLineTextScaleAnima = new ValueAnimator();
        private Method drawTextMethod;
        private StaticLayout staticLayout;


        public KrcLineView(Context context) {
            this(context, null);
        }

        public KrcLineView(Context context, AttributeSet attrs) {
            super(context, attrs);
            initView();
            curLineTextScaleAnima.addUpdateListener(this);
            curLineTextScaleAnima.setDuration(200);
            curLineTextScaleAnima.setInterpolator(new LinearInterpolator());
            maxTextSizePaint.setTextSize(maxTextSize);
        }

        private void initView() {
            setCurrentLine(false);
            currentLineTextPaint.setDither(true);
            currentLineTextPaint.setAntiAlias(true);
            currentLineColors[0] = currentLineHLTextColor;
            currentLineColors[1] = currentLineTextColor;
        }


        public void setKrcLineInfo(KrcLineInfo info) {
            if (info == null || TextUtils.isEmpty(info.text)) {
                return;
            }
            krcLineInfo = info;
            float previousWordsWidth = 0;
            if (info.words != null) {
                for (int i = 0; i < info.words.size(); i++) {
                    final Word word = info.words.get(i);
                    word.previousWordsWidth = previousWordsWidth;
                    word.textWidth = maxTextSizePaint.measureText(word.text);
                    previousWordsWidth += word.textWidth;
                    final int next = i + 1;
                    if (next < info.words.size()) {
                        word.next = info.words.get(next);
                    }
                }
            }
            KrcLineView.this.requestLayout();
        }

        public void setTimeMs(final long timeMs) {
            if (krcLineInfo == null || krcLineInfo.words == null || krcLineInfo.words.isEmpty() || !isCurrentLine) {
                return;
            }
            this.timeMs = timeMs;
            invalidate();
        }


        public void setCurrentLine(boolean isCurrentLine) {
            if (this.isCurrentLine == isCurrentLine) {
                return;
            }
            this.isCurrentLine = isCurrentLine;
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

        public void reset() {
            timeMs = 0;
        }

        @SuppressLint("DrawAllocation")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (checkKrcDataInvalid()) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            currentLineTextPaint.setTextSize(maxTextSize);

            float contentWidth;
            final int widthMeasureMode = MeasureSpec.getMode(widthMeasureSpec);
            switch (widthMeasureMode) {
                case MeasureSpec.AT_MOST:
                case MeasureSpec.UNSPECIFIED:
                    contentWidth =
                            currentLineTextPaint.measureText(krcLineInfo.text) + getPaddingStart() + getPaddingEnd();
                    Log.i(TAG, "===> onMeasure:width MeasureSpec.AT_MOST,contentWidth: " + contentWidth);
                    break;
                case MeasureSpec.EXACTLY:
                default:
                    contentWidth = MeasureSpec.getSize(widthMeasureSpec);
                    break;
            }
            final int localMaxWordsPerLine = Math.min(maxWordsPerLine, krcLineInfo.words.size());
            float maxWordsWidth = 0;
            for (int i = 0; i < localMaxWordsPerLine; i++) {
                maxWordsWidth += currentLineTextPaint.measureText(krcLineInfo.words.get(i).text);
            }
            staticLayout = new StaticLayout(krcLineInfo.text, currentLineTextPaint, (int) maxWordsWidth,
                    Layout.Alignment.ALIGN_CENTER, 1f, 0.0f, false);

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
            currentLineTextPaint.setTextSize(minTextSize);
            Log.i(TAG, "===> onMeasure:width:" + contentWidth + " height : " + contentHeight);
            setMeasuredDimension((int) contentWidth, (int) contentHeight);
        }

        private boolean checkKrcDataInvalid() {
            return krcLineInfo == null || krcLineInfo.words == null;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (canvas != null) {
                drawKrc2(canvas);
            }
        }

        private void drawKrc2(@NonNull Canvas canvas) {
            if (checkKrcDataInvalid() || staticLayout == null) {
                return;
            }
            if (currentLineTextPaint.getTextSize() < minTextSize) {
                currentLineTextPaint.setTextSize(minTextSize);
            }

            canvas.save();
            final float contentWidth = getWidth() - getPaddingStart() - getPaddingEnd();
            final float translateX = (contentWidth - staticLayout.getWidth()) * 0.5f;
            final float translateY = getPaddingTop();
            canvas.translate(translateX, translateY);
            // draw current line high light text
            if (isCurrentLine) {
                final float totalHlWidth = calculateHighLightWidth(this.timeMs);
                float totalTextWidth = 0;
                for (int i = 0; i < staticLayout.getLineCount(); i++) {
                    currentLineTextPaint.setColor(currentLineTextColor);
                    currentLineTextPaint.setColor(currentLineHLTextColor);
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
                    currentLineTextPaint.setShader(linearGradient);
                    drawLineText(canvas, i);
                }

            }
            // draw normal text
            else {
                currentLineTextPaint.setShader(null);
                currentLineTextPaint.setColor(normalTextColor);
                for (int i = 0; i < staticLayout.getLineCount(); i++) {
                    drawLineText(canvas, i);
                }
            }
            canvas.restore();
        }

        @SuppressLint("SoonBlockedPrivateApi")
        private void drawLineText(Canvas canvas, int line) {
            if (staticLayout == null || canvas == null) {
                return;
            }
            try {
                if (VERSION.SDK_INT >= VERSION_CODES.P) {
                    HiddenApiBypass.invoke(Layout.class, staticLayout, "drawText", canvas, line, line);
                } else {
                    if (drawTextMethod == null) {
                        drawTextMethod = Layout.class.getDeclaredMethod("drawText", Canvas.class, int.class, int.class);
                        drawTextMethod.setAccessible(true);
                    }
                    drawTextMethod.invoke(staticLayout, canvas, line, line);
                }

            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        private float calculateHighLightWidth(long timeMs) {
            if (checkKrcDataInvalid()) {
                return 0;
            }
            if (timeMs < krcLineInfo.words.get(0).startTimeMs) {
                return 0;
            }

            final int curWordIndex = Collections.binarySearch(krcLineInfo.words, timeMs);
            if (curWordIndex < 0) {
                return 0;
            }
            final Word curWord = krcLineInfo.words.get(curWordIndex);
            if (curWord.duration == 0L) {
                return curWord.previousWordsWidth;
            }

            final long cutoffDuration = Math.min(curWord.duration, timeMs - curWord.startTimeMs);
            final float curWordWidth = curWord.textWidth;
            final float curWordHLWidth = Math.min(curWordWidth, curWordWidth / curWord.duration * cutoffDuration);
            return curWord.previousWordsWidth + curWordHLWidth;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            float size = (float) animation.getAnimatedValue();
            currentLineTextPaint.setTextSize(size);
            invalidate();
        }
    }

}
