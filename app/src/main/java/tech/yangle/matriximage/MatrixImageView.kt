package tech.yangle.matriximage

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.Bitmap.createScaledBitmap
import android.graphics.BitmapFactory.decodeResource
import android.util.AttributeSet
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import tech.yangle.matriximage.utils.MatrixImageUtils
import tech.yangle.matriximage.utils.MatrixImageUtils.TouchMode.*
import tech.yangle.matriximage.utils.MatrixImageUtils.callRotation
import tech.yangle.matriximage.utils.MatrixImageUtils.getDistanceOf2Points
import tech.yangle.matriximage.utils.MatrixImageUtils.getImageRectF
import tech.yangle.matriximage.utils.MatrixImageUtils.getTouchMode
import tech.yangle.matriximage.utils.coroutineDelay
import kotlin.math.abs

/**
 * 支持移动、缩放、旋转功能的ImageView
 * <p>
 * Created by yangle on 2021/8/10.
 * Website：http://www.yangle.tech
 */
class MatrixImageView : AppCompatImageView {

    // 控件宽度
    private var mWidth = 0

    // 控件高度
    private var mHeight = 0

    // 第一次绘制
    private var mFirstDraw = true

    // 是否显示控制框
    private var mShowFrame = false

    // 当前Image矩阵
    private var mImgMatrix = Matrix()

    // 画笔
    private lateinit var mPaint: Paint

    // 触摸模式
    private var touchMode: MatrixImageUtils.TouchMode? = null

    // 第二根手指是否按下
    private var mIsPointerDown = false

    // 按下点x坐标
    private var mDownX = 0f

    // 按下点y坐标
    private var mDownY = 0f

    // 上一次的触摸点x坐标
    private var mLastX = 0f

    // 上一次的触摸点y坐标
    private var mLastY = 0f

    // 旋转角度
    private var mDegree: Float = 0.0f

    // 旋转图标
    private lateinit var mRotateIcon: Bitmap

    // 图片控制框颜色
    private var mFrameColor = Color.parseColor("#1677FF")

    // 连接线宽度
    private var mLineWidth = dp2px(context, 2f)

    // 缩放控制点半径
    var mScaleDotRadius = dp2px(context, 5f)

    // 旋转控制点半径
    var mRotateDotRadius = dp2px(context, 12f)

    // 按下监听
    private var mDownClickListener: ((view: View, pointF: PointF) -> Unit)? = null

    // 长按监听
    private var mLongClickListener: ((view: View, pointF: PointF) -> Unit)? = null

    // 移动监听
    private var mMoveListener: ((view: View, pointF: PointF) -> Unit)? = null

    // 长按监听计时任务
    private var mLongClickJob: Job? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        setAttribute(attrs)
        init()
    }

    private fun setAttribute(attrs: AttributeSet?) {
        if (attrs == null) {
            return
        }
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.MatrixImageView)
        val indexCount = typedArray.indexCount
        for (i in 0 until indexCount) {
            when (val attr = typedArray.getIndex(i)) {
                R.styleable.MatrixImageView_fcLineWidth -> { // 连接线宽度
                    mLineWidth = typedArray.getDimension(attr, mLineWidth)
                }
                R.styleable.MatrixImageView_fcLineWidthOffset -> { // 连接线宽度，小数部分
                    mLineWidth += typedArray.getDimension(attr, 0f)
                }
                R.styleable.MatrixImageView_fcScaleDotRadius -> { // 缩放控制点半径
                    mScaleDotRadius = typedArray.getDimension(attr, mScaleDotRadius)
                }
                R.styleable.MatrixImageView_fcScaleDotRadiusOffset -> { // 缩放控制点半径，小数部分
                    mScaleDotRadius += typedArray.getDimension(attr, 0f)
                }
                R.styleable.MatrixImageView_fcRotateDotRadius -> { // 旋转控制点半径
                    mRotateDotRadius = typedArray.getDimension(attr, mRotateDotRadius)
                }
                R.styleable.MatrixImageView_fcRotateDotRadiusOffset -> { // 旋转控制点半径，小数部分
                    mRotateDotRadius += typedArray.getDimension(attr, 0f)
                }
                R.styleable.MatrixImageView_fcFrameColor -> { // 图片控制框颜色
                    mFrameColor = typedArray.getColor(attr, mFrameColor)
                }
            }
        }
        typedArray.recycle()
    }

    private fun init() {
        mPaint = Paint()
        mPaint.isAntiAlias = true
        mPaint.strokeWidth = mLineWidth
        mPaint.color = mFrameColor
        mPaint.style = Paint.Style.FILL

        // Matrix模式
        scaleType = ScaleType.MATRIX

        // 旋转图标
        val rotateIcon = decodeResource(resources, R.mipmap.ic_mi_rotate)
        val rotateIconWidth = (mRotateDotRadius * 1.6f).toInt()
        mRotateIcon = createScaledBitmap(rotateIcon, rotateIconWidth, rotateIconWidth, true)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        this.mWidth = w
        this.mHeight = h
    }

    override fun draw(canvas: Canvas?) {
        super.draw(canvas)
        if (canvas == null || drawable == null) {
            return
        }

        val imgRect = getImageRectF(this)
        // 左上角x坐标
        val left = imgRect.left
        // 左上角y坐标
        val top = imgRect.top
        // 右下角x坐标
        val right = imgRect.right
        // 右下角y坐标
        val bottom = imgRect.bottom

        // 图片移动到控件中心
        if (mFirstDraw) {
            mFirstDraw = false
            val centerX = (mWidth / 2).toFloat()
            val centerY = (mHeight / 2).toFloat()
            val imageWidth = right - left
            val imageHeight = bottom - top
            mImgMatrix.postTranslate(centerX - imageWidth / 2, centerY - imageHeight / 2)
            // 如果图片较大，缩放0.5倍
            if (imageWidth > width || imageHeight > height) {
                mImgMatrix.postScale(0.5f, 0.5f, centerX, centerY)
            }
            imageMatrix = mImgMatrix
        }

        // 不绘制控制框
        if (!mShowFrame) {
            return
        }

        // 上边框
        canvas.drawLine(left, top, right, top, mPaint)
        // 下边框
        canvas.drawLine(left, bottom, right, bottom, mPaint)
        // 左边框
        canvas.drawLine(left, top, left, bottom, mPaint)
        // 右边框
        canvas.drawLine(right, top, right, bottom, mPaint)

        // 左上角控制点，等比缩放
        canvas.drawCircle(left, top, mScaleDotRadius, mPaint)
        // 右上角控制点，等比缩放
        canvas.drawCircle(right, top, mScaleDotRadius, mPaint)
        // 左中间控制点，横向缩放
        canvas.drawCircle(left, top + (bottom - top) / 2, mScaleDotRadius, mPaint)
        // 右中间控制点，横向缩放
        canvas.drawCircle(right, top + (bottom - top) / 2, mScaleDotRadius, mPaint)
        // 左下角控制点，等比缩放
        canvas.drawCircle(left, bottom, mScaleDotRadius, mPaint)
        // 右下角控制点，等比缩放
        canvas.drawCircle(right, bottom, mScaleDotRadius, mPaint)
        // 下中间控制点，竖向缩放
        val middleX = (right - left) / 2 + left
        canvas.drawCircle(middleX, bottom, mScaleDotRadius, mPaint)
        // 上中间控制点，旋转
        val rotateLine = mRotateDotRadius / 3
        canvas.drawLine(middleX, top - rotateLine, middleX, top, mPaint)
        canvas.drawCircle(middleX, top - rotateLine - mRotateDotRadius, mRotateDotRadius, mPaint)
        // 上中间控制点，旋转图标
        canvas.drawBitmap(
            mRotateIcon,
            middleX - mRotateIcon.width / 2,
            top - rotateLine - mRotateDotRadius - mRotateIcon.width / 2,
            mPaint
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || drawable == null) {
            return super.onTouchEvent(event)
        }
        // x坐标
        val x = event.x
        // y坐标
        val y = event.y
        // 图片显示区域
        val imageRect = getImageRectF(this)
        // 图片中心点x坐标
        val centerX = (imageRect.right - imageRect.left) / 2 + imageRect.left
        // 图片中心点y坐标
        val centerY = (imageRect.bottom - imageRect.top) / 2 + imageRect.top

        when (event.action.and(ACTION_MASK)) {
            ACTION_DOWN -> {
                // 按下监听
                mDownClickListener?.invoke(this, PointF(x, y))
                // 判断是否在图片实际显示区域内
                touchMode = getTouchMode(this, x, y)
                if (touchMode == TOUCH_OUTSIDE) {
                    mShowFrame = false
                    invalidate()
                    return super.onTouchEvent(event)
                }
                mDownX = x
                mDownY = y
                mLastX = x
                mLastY = y
                // 旋转控制点，点击后以图片中心为基准，计算当前旋转角度
                if (touchMode == TOUCH_ROTATE) {
                    // 旋转角度
                    mDegree = callRotation(centerX, centerY, x, y)
                }
                mShowFrame = true
                invalidate()

                // 长按监听计时
                mLongClickJob = coroutineDelay(Main, 500) {
                    val offsetX = abs(x - mLastX)
                    val offsetY = abs(y - mLastY)
                    val offset = dp2px(context, 10f)
                    if (offsetX <= offset && offsetY <= offset) {
                        mLongClickListener?.invoke(this, PointF(x, y))
                    }
                }
                return true
            }
            ACTION_CANCEL -> {
                mLongClickJob?.cancel()
            }
            ACTION_POINTER_DOWN -> {
                mLongClickJob?.cancel()
                mDegree = callRotation(event)
                mIsPointerDown = true
                return true
            }
            ACTION_MOVE -> {
                // 旋转事件
                if (event.pointerCount == 2) {
                    if (!mIsPointerDown) {
                        return true
                    }
                    val rotate = callRotation(event)
                    val rotateNow = rotate - mDegree
                    mDegree = rotate
                    mImgMatrix.postRotate(rotateNow, centerX, centerY)
                    imageMatrix = mImgMatrix
                    return true
                }
                if (mIsPointerDown) {
                    return true
                }
                // 移动、缩放事件
                touchMove(x, y, imageRect)
                mLastX = x
                mLastY = y
                invalidate()
                val offsetX = abs(x - mDownX)
                val offsetY = abs(y - mDownY)
                val offset = dp2px(context, 10f)
                if (offsetX > offset || offsetY > offset) {
                    mMoveListener?.invoke(this, PointF(x, y))
                }
                return true
            }
            ACTION_UP -> {
                mLongClickJob?.cancel()
                touchMode = null
                mIsPointerDown = false
                mDegree = 0f
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 手指移动
     *
     * @param x         x坐标
     * @param y         y坐标
     * @param imageRect 图片显示区域
     */
    private fun touchMove(x: Float, y: Float, imageRect: RectF) {
        // 左上角x坐标
        val left = imageRect.left
        // 左上角y坐标
        val top = imageRect.top
        // 右下角x坐标
        val right = imageRect.right
        // 右下角y坐标
        val bottom = imageRect.bottom
        // 总的缩放距离，斜角
        val totalTransOblique = getDistanceOf2Points(left, top, right, bottom)
        // 总的缩放距离，水平
        val totalTransHorizontal = getDistanceOf2Points(left, top, right, top)
        // 总的缩放距离，垂直
        val totalTransVertical = getDistanceOf2Points(left, top, left, bottom)
        // 当前缩放距离
        val scaleTrans = getDistanceOf2Points(mLastX, mLastY, x, y)
        // 缩放系数，x轴方向
        val scaleFactorX: Float
        // 缩放系数，y轴方向
        val scaleFactorY: Float
        // 缩放基准点x坐标
        val scaleBaseX: Float
        // 缩放基准点y坐标
        val scaleBaseY: Float

        when (touchMode) {
            TOUCH_IMAGE -> {
                mImgMatrix.postTranslate(x - mLastX, y - mLastY)
                imageMatrix = mImgMatrix
                return
            }
            TOUCH_ROTATE -> {
                // 图片中心点x坐标
                val centerX = (imageRect.right - imageRect.left) / 2 + imageRect.left
                // 图片中心点y坐标
                val centerY = (imageRect.bottom - imageRect.top) / 2 + imageRect.top
                // 旋转角度
                val rotate = callRotation(centerX, centerY, x, y)
                val rotateNow = rotate - mDegree
                mDegree = rotate
                mImgMatrix.postRotate(rotateNow, centerX, centerY)
                imageMatrix = mImgMatrix
                return
            }
            TOUCH_CONTROL_1 -> {
                // 缩小
                scaleFactorX = if (x - mLastX > 0) {
                    (totalTransOblique - scaleTrans) / totalTransOblique
                } else {
                    (totalTransOblique + scaleTrans) / totalTransOblique
                }
                scaleFactorY = scaleFactorX
                // 右下角
                scaleBaseX = imageRect.right
                scaleBaseY = imageRect.bottom
            }
            TOUCH_CONTROL_2 -> {
                // 缩小
                scaleFactorX = if (x - mLastX < 0) {
                    (totalTransOblique - scaleTrans) / totalTransOblique
                } else {
                    (totalTransOblique + scaleTrans) / totalTransOblique
                }
                scaleFactorY = scaleFactorX
                // 左下角
                scaleBaseX = imageRect.left
                scaleBaseY = imageRect.bottom
            }
            TOUCH_CONTROL_3 -> {
                // 缩小
                scaleFactorX = if (x - mLastX > 0) {
                    (totalTransOblique - scaleTrans) / totalTransOblique
                } else {
                    (totalTransOblique + scaleTrans) / totalTransOblique
                }
                scaleFactorY = scaleFactorX
                // 右上角
                scaleBaseX = imageRect.right
                scaleBaseY = imageRect.top
            }
            TOUCH_CONTROL_4 -> {
                // 缩小
                scaleFactorX = if (x - mLastX < 0) {
                    (totalTransOblique - scaleTrans) / totalTransOblique
                } else {
                    (totalTransOblique + scaleTrans) / totalTransOblique
                }
                scaleFactorY = scaleFactorX
                // 左上角
                scaleBaseX = imageRect.left
                scaleBaseY = imageRect.top
            }
            TOUCH_CONTROL_5 -> {
                // 缩小
                scaleFactorX = if (x - mLastX > 0) {
                    (totalTransHorizontal - scaleTrans) / totalTransHorizontal
                } else {
                    (totalTransHorizontal + scaleTrans) / totalTransHorizontal
                }
                scaleFactorY = 1f
                // 右上角
                scaleBaseX = imageRect.right
                scaleBaseY = imageRect.top
            }
            TOUCH_CONTROL_6 -> {
                // 缩小
                scaleFactorX = if (x - mLastX < 0) {
                    (totalTransHorizontal - scaleTrans) / totalTransHorizontal
                } else {
                    (totalTransHorizontal + scaleTrans) / totalTransHorizontal
                }
                scaleFactorY = 1f
                // 左上角
                scaleBaseX = imageRect.left
                scaleBaseY = imageRect.top
            }
            TOUCH_CONTROL_7 -> {
                // 缩小
                scaleFactorX = 1f
                scaleFactorY = if (y - mLastY < 0) {
                    (totalTransVertical - scaleTrans) / totalTransVertical
                } else {
                    (totalTransVertical + scaleTrans) / totalTransVertical
                }
                // 左上角
                scaleBaseX = imageRect.left
                scaleBaseY = imageRect.top
            }
            else -> {
                return
            }
        }

        // 最小缩放值限制
        val scaleMatrix = Matrix(mImgMatrix)
        scaleMatrix.postScale(scaleFactorX, scaleFactorY, scaleBaseX, scaleBaseY)
        val scaleRectF = getImageRectF(this, scaleMatrix)
        if (scaleRectF.right - scaleRectF.left < mScaleDotRadius * 6
            || scaleRectF.bottom - scaleRectF.top < mScaleDotRadius * 6
        ) {
            return
        }
        // 缩放
        mImgMatrix.postScale(scaleFactorX, scaleFactorY, scaleBaseX, scaleBaseY)
        imageMatrix = mImgMatrix
    }

    /**
     * 隐藏控制框
     */
    fun hideControlFrame() {
        mShowFrame = false
        invalidate()
    }

    /**
     * 设置按下监听
     *
     * @param listener 监听回调
     */
    fun setOnImageDownClickListener(listener: (view: View, pointF: PointF) -> Unit) {
        this.mDownClickListener = listener
    }

    /**
     * 设置长按监听
     *
     * @param listener 监听回调
     */
    fun setOnImageLongClickListener(listener: (view: View, pointF: PointF) -> Unit) {
        this.mLongClickListener = listener
    }

    /**
     * 设置移动监听
     *
     * @param listener 监听回调
     */
    fun setOnImageMoveListener(listener: (view: View, pointF: PointF) -> Unit) {
        this.mMoveListener = listener
    }

    /**
     * dp转px
     *
     * @param context 上下文
     * @param dp      dp单位值
     * @return px单位制
     */
    private fun dp2px(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
    }
}