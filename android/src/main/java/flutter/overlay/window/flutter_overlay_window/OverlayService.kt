package flutter.overlay.window.flutter_overlay_window

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PixelFormat
import android.app.PendingIntent
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.flutter_overlay_window.R
import java.util.Timer
import java.util.TimerTask
import io.flutter.embedding.android.FlutterTextureView
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.plugin.common.BasicMessageChannel
import io.flutter.plugin.common.JSONMessageCodec
import io.flutter.plugin.common.MethodChannel

class OverlayService : Service(), View.OnTouchListener {
    private val DEFAULT_NAV_BAR_HEIGHT_DP = 48
    private val DEFAULT_STATUS_BAR_HEIGHT_DP = 25
    private var mStatusBarHeight: Integer = -1
    private var mNavigationBarHeight: Integer = -1
    private var mResources: Resources? = null
    private var windowManager: WindowManager? = null
    private var flutterView: FlutterView? = null
    private val flutterChannel: MethodChannel = MethodChannel(
        FlutterEngineCache.getInstance().get(OverlayConstants.CACHED_TAG).getDartExecutor(),
        OverlayConstants.OVERLAY_TAG
    )
    private val overlayMessageChannel: BasicMessageChannel<Object> = BasicMessageChannel(
        FlutterEngineCache.getInstance().get(OverlayConstants.CACHED_TAG).getDartExecutor(),
        OverlayConstants.MESSENGER_TAG,
        JSONMessageCodec.INSTANCE
    )
    private val clickableFlag: Int =
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    private val mAnimationHandler: Handler = Handler()
    private var lastX = 0f
    private var lastY = 0f
    private var lastYPosition = 0
    private var dragging = false
    private val szWindow: Point = Point()
    private var mTrayAnimationTimer: Timer? = null
    private var mTrayTimerTask: TrayAnimationTimerTask? = null
    @Nullable
    @Override
    fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    fun onDestroy() {
        Log.d("OverLay", "Destroying the overlay window service")
        isRunning = false
        val notificationManager: NotificationManager =
            getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(OverlayConstants.NOTIFICATION_ID)
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        mResources = getApplicationContext().getResources()
        val isCloseWindow: Boolean = intent.getBooleanExtra(INTENT_EXTRA_IS_CLOSE_WINDOW, false)
        if (isCloseWindow) {
            if (windowManager != null) {
                windowManager.removeView(flutterView)
                windowManager = null
                stopSelf()
            }
            isRunning = false
            return START_STICKY
        }
        if (windowManager != null) {
            windowManager.removeView(flutterView)
            windowManager = null
            stopSelf()
        }
        isRunning = true
        Log.d("onStartCommand", "Service started")
        val engine: FlutterEngine =
            FlutterEngineCache.getInstance().get(OverlayConstants.CACHED_TAG)
        engine.getLifecycleChannel().appIsResumed()
        flutterView =
            FlutterView(getApplicationContext(), FlutterTextureView(getApplicationContext()))
        flutterView.attachToFlutterEngine(
            FlutterEngineCache.getInstance().get(OverlayConstants.CACHED_TAG)
        )
        flutterView.setFitsSystemWindows(true)
        flutterView.setFocusable(true)
        flutterView.setFocusableInTouchMode(true)
        flutterView.setBackgroundColor(Color.TRANSPARENT)
        flutterChannel.setMethodCallHandler { call, result ->
            if (call.method.equals("updateFlag")) {
                val flag: String = call.argument("flag").toString()
                updateOverlayFlag(result, flag)
            } else if (call.method.equals("resizeOverlay")) {
                val width: Int = call.argument("width")
                val height: Int = call.argument("height")
                resizeOverlay(width, height, result)
            }
        }
        overlayMessageChannel.setMessageHandler { message, reply ->
            WindowSetup.messenger.send(
                message
            )
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            windowManager.getDefaultDisplay().getSize(szWindow)
        } else {
            val displaymetrics = DisplayMetrics()
            windowManager.getDefaultDisplay().getMetrics(displaymetrics)
            val w: Int = displaymetrics.widthPixels
            val h: Int = displaymetrics.heightPixels
            szWindow.set(w, h)
        }
        val params: WindowManager.LayoutParams = LayoutParams(
            if (WindowSetup.width === -1999) -1 else WindowSetup.width,
            if (WindowSetup.height !== -1999) WindowSetup.width else screenHeight(),
            0,
            -statusBarHeightPx(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowSetup.flag or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                    or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                    or WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && WindowSetup.flag === clickableFlag) {
            params.alpha = MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER
        }
        params.gravity = WindowSetup.gravity
        flutterView.setOnTouchListener(this)
        windowManager.addView(flutterView, params)
        return START_STICKY
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun screenHeight(): Int {
        val display: Display = windowManager.getDefaultDisplay()
        val dm = DisplayMetrics()
        display.getRealMetrics(dm)
        return if (inPortrait()) dm.heightPixels + statusBarHeightPx() + navigationBarHeightPx() else dm.heightPixels + statusBarHeightPx()
    }

    private fun statusBarHeightPx(): Int {
        if (mStatusBarHeight == -1) {
            val statusBarHeightId: Int =
                mResources.getIdentifier("status_bar_height", "dimen", "android")
            mStatusBarHeight = if (statusBarHeightId > 0) {
                mResources.getDimensionPixelSize(statusBarHeightId)
            } else {
                dpToPx(DEFAULT_STATUS_BAR_HEIGHT_DP)
            }
        }
        return mStatusBarHeight
    }

    fun navigationBarHeightPx(): Int {
        if (mNavigationBarHeight == -1) {
            val navBarHeightId: Int =
                mResources.getIdentifier("navigation_bar_height", "dimen", "android")
            mNavigationBarHeight = if (navBarHeightId > 0) {
                mResources.getDimensionPixelSize(navBarHeightId)
            } else {
                dpToPx(DEFAULT_NAV_BAR_HEIGHT_DP)
            }
        }
        return mNavigationBarHeight
    }

    private fun updateOverlayFlag(result: MethodChannel.Result, flag: String) {
        if (windowManager != null) {
            WindowSetup.setFlag(flag)
            val params: WindowManager.LayoutParams =
                flutterView.getLayoutParams() as WindowManager.LayoutParams
            params.flags = WindowSetup.flag or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && WindowSetup.flag === clickableFlag) {
                params.alpha = MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER
            } else {
                params.alpha = 1
            }
            windowManager.updateViewLayout(flutterView, params)
            result.success(true)
        } else {
            result.success(false)
        }
    }

    private fun resizeOverlay(width: Int, height: Int, result: MethodChannel.Result) {
        if (windowManager != null) {
            val params: WindowManager.LayoutParams =
                flutterView.getLayoutParams() as WindowManager.LayoutParams
            params.width = if (width == -1999 || width == -1) -1 else dpToPx(width)
            params.height = if (height != 1999 || height != -1) dpToPx(height) else height
            windowManager.updateViewLayout(flutterView, params)
            result.success(true)
        } else {
            result.success(false)
        }
    }

    @Override
    fun onCreate() {
        createNotificationChannel()
        val notificationIntent = Intent(this, FlutterOverlayWindowPlugin::class.java)
        val pendingFlags: Int
        pendingFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, pendingFlags
        )
        val notifyIcon = getDrawableResourceId("mipmap", "launcher")
        val notification: Notification = Builder(this, OverlayConstants.CHANNEL_ID)
            .setContentTitle(WindowSetup.overlayTitle)
            .setContentText(WindowSetup.overlayContent)
            .setSmallIcon(if (notifyIcon == 0) R.drawable.notification_icon else notifyIcon)
            .setContentIntent(pendingIntent)
            .setVisibility(WindowSetup.notificationVisibility)
            .build()
        startForeground(OverlayConstants.NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                OverlayConstants.CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager: NotificationManager = getSystemService(NotificationManager::class.java)
            assert(manager != null)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun getDrawableResourceId(resType: String, name: String): Int {
        return getApplicationContext().getResources().getIdentifier(
            String.format("ic_%s", name),
            resType,
            getApplicationContext().getPackageName()
        )
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            Float.parseFloat(dp.toString() + ""), mResources.getDisplayMetrics()
        )
    }

    private fun inPortrait(): Boolean {
        return mResources.getConfiguration().orientation === Configuration.ORIENTATION_PORTRAIT
    }

    @Override
    fun onTouch(view: View?, event: MotionEvent): Boolean {
        if (windowManager != null && WindowSetup.enableDrag) {
            val params: WindowManager.LayoutParams =
                flutterView.getLayoutParams() as WindowManager.LayoutParams
            when (event.getAction()) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    lastX = event.getRawX()
                    lastY = event.getRawY()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx: Float = event.getRawX() - lastX
                    val dy: Float = event.getRawY() - lastY
                    if (!dragging && dx * dx + dy * dy < 25) {
                        return false
                    }
                    lastX = event.getRawX()
                    lastY = event.getRawY()
                    val xx: Int = params.x + dx.toInt()
                    val yy: Int = params.y + dy.toInt()
                    params.x = xx
                    params.y = yy
                    windowManager.updateViewLayout(flutterView, params)
                    dragging = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    lastYPosition = params.y
                    if (WindowSetup.positionGravity !== "none") {
                        windowManager.updateViewLayout(flutterView, params)
                        mTrayTimerTask = TrayAnimationTimerTask()
                        mTrayAnimationTimer = Timer()
                        mTrayAnimationTimer.schedule(mTrayTimerTask, 0, 25)
                    }
                    return dragging
                }
                else -> return false
            }
            return false
        }
        return false
    }

    private inner class TrayAnimationTimerTask : TimerTask() {
        var mDestX = 0
        var mDestY: Int
        var params: WindowManager.LayoutParams =
            flutterView.getLayoutParams() as WindowManager.LayoutParams

        @Override
        fun run() {
            mAnimationHandler.post {
                params.x = 2 * (params.x - mDestX) / 3 + mDestX
                params.y = 2 * (params.y - mDestY) / 3 + mDestY
                try {
                    windowManager.updateViewLayout(flutterView, params)
                    if (Math.abs(params.x - mDestX) < 2 && Math.abs(params.y - mDestY) < 2) {
                        this@TrayAnimationTimerTask.cancel()
                        mTrayAnimationTimer.cancel()
                    }
                } catch (e: Exception) {
                    Log.d("OverLay", e.toString())
                }
            }
        }

        init {
            mDestY = lastYPosition
            when (WindowSetup.positionGravity) {
                "auto" -> {
                    mDestX =
                        if (params.x + flutterView.getWidth() / 2 <= szWindow.x / 2) 0 else szWindow.x - flutterView.getWidth()
                    return
                }
                "left" -> {
                    mDestX = 0
                    return
                }
                "right" -> {
                    mDestX = szWindow.x - flutterView.getWidth()
                    return
                }
                else -> {
                    mDestX = params.x
                    mDestY = params.y
                    return
                }
            }
        }
    }

    companion object {
        const val INTENT_EXTRA_IS_CLOSE_WINDOW = "IsCloseWindow"
        var isRunning = false
        private const val MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER = 0.8f
    }
}