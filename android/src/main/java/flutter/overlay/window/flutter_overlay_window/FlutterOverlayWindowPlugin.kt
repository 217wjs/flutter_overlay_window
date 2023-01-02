package flutter.overlay.window.flutter_overlay_window

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.FlutterEngineGroup
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BasicMessageChannel
import io.flutter.plugin.common.JSONMessageCodec
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

class FlutterOverlayWindowPlugin : FlutterPlugin, ActivityAware, BasicMessageChannel.MessageHandler,
    MethodCallHandler, PluginRegistry.ActivityResultListener {
    private var channel: MethodChannel? = null
    private var context: Context? = null
    private var mActivity: Activity? = null
    private var messenger: BasicMessageChannel<Object>? = null
    private var pendingResult: Result? = null
    val REQUEST_CODE_FOR_OVERLAY_PERMISSION = 1248
    @Override
    fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPluginBinding) {
        context = flutterPluginBinding.getApplicationContext()
        channel =
            MethodChannel(flutterPluginBinding.getBinaryMessenger(), OverlayConstants.CHANNEL_TAG)
        channel.setMethodCallHandler(this)
        messenger = BasicMessageChannel(
            flutterPluginBinding.getBinaryMessenger(), OverlayConstants.MESSENGER_TAG,
            JSONMessageCodec.INSTANCE
        )
        messenger.setMessageHandler(this)
        WindowSetup.messenger = messenger
        WindowSetup.messenger.setMessageHandler(this)

        // App간 통신
        val intent: Intent =
            context.getPackageManager().getLaunchIntentForPackage("com.hwego.flutterRider")
        intent.setClassName("com.hwego.flutterRider", "com.hwego.flutterRider.OverlayReceiver")
        context.sendBroadcast(intent)
    }

    @Override
    fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        pendingResult = result
        if (call.method.equals("checkPermission")) {
            result.success(checkOverlayPermission())
        } else if (call.method.equals("wakeUp")) {
            val launchIntent: Intent =
                context.getPackageManager().getLaunchIntentForPackage("com.hwego.flutterRider")
            val broadcastIntent = Intent("OVERLAY")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                broadcastIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                broadcastIntent.putExtra("data", 200)
                context.sendBroadcast(broadcastIntent)
                context.startActivity(launchIntent)
                result.success("wakeUp")
            } else {
                result.success("Failed to wakeUp")
            }
        } else if (call.method.equals("break")) {
            val broadcastIntent = Intent("OVERLAY")
            broadcastIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            broadcastIntent.putExtra("data", 300)
            context.sendBroadcast(broadcastIntent)
            result.success("break")
        } else if (call.method.equals("work")) {
            val broadcastIntent = Intent("OVERLAY")
            broadcastIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            broadcastIntent.putExtra("data", 400)
            context.sendBroadcast(broadcastIntent)
            result.success("work")
        } else if (call.method.equals("start")) {
            val broadcastIntent = Intent("OVERLAY")
            broadcastIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            broadcastIntent.putExtra("data", 500)
            context.sendBroadcast(broadcastIntent)
            result.success("start")
        } else if (call.method.equals("requestPermission")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.setData(Uri.parse("package:" + mActivity.getPackageName()))
                mActivity.startActivityForResult(intent, REQUEST_CODE_FOR_OVERLAY_PERMISSION)
            } else {
                result.success(true)
            }
        } else if (call.method.equals("showOverlay")) {
            if (!checkOverlayPermission()) {
                result.error("PERMISSION", "overlay permission is not enabled", null)
                return
            }
            val height: Integer = call.argument("height")
            val width: Integer = call.argument("width")
            val alignment: String = call.argument("alignment")
            val flag: String = call.argument("flag")
            val overlayTitle: String = call.argument("overlayTitle")
            val overlayContent: String = call.argument("overlayContent")
            val notificationVisibility: String = call.argument("notificationVisibility")
            val enableDrag: Boolean = call.argument("enableDrag")
            val positionGravity: String = call.argument("positionGravity")
            WindowSetup.width = if (width != null) width else -1
            WindowSetup.height = if (height != null) height else -1
            WindowSetup.enableDrag = enableDrag
            WindowSetup.setGravityFromAlignment(alignment ?: "center")
            WindowSetup.setFlag(flag ?: "flagNotFocusable")
            WindowSetup.overlayTitle = overlayTitle
            WindowSetup.overlayContent = overlayContent ?: ""
            WindowSetup.positionGravity = positionGravity
            WindowSetup.setNotificationVisibility(notificationVisibility)
            val intent = Intent(context, OverlayService::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startService(intent)
            result.success(null)
        } else if (call.method.equals("isOverlayActive")) {
            result.success(OverlayService.isRunning)
            return
        } else if (call.method.equals("closeOverlay")) {
            if (OverlayService.isRunning) {
                val i = Intent(context, OverlayService::class.java)
                i.putExtra(OverlayService.INTENT_EXTRA_IS_CLOSE_WINDOW, true)
                context.startService(i)
                result.success(true)
            }
            return
        } else {
            result.notImplemented()
        }
    }

    @Override
    fun onDetachedFromEngine(@NonNull binding: FlutterPluginBinding?) {
        channel.setMethodCallHandler(null)
        WindowSetup.messenger.setMessageHandler(null)
    }

    @Override
    fun onAttachedToActivity(@NonNull binding: ActivityPluginBinding) {
        mActivity = binding.getActivity()
        val enn = FlutterEngineGroup(context)
        val dEntry: DartExecutor.DartEntrypoint = DartEntrypoint(
            FlutterInjector.instance().flutterLoader().findAppBundlePath(),
            "overlayMain"
        )
        val engine: FlutterEngine = enn.createAndRunEngine(context, dEntry)
        FlutterEngineCache.getInstance().put(OverlayConstants.CACHED_TAG, engine)
        binding.addActivityResultListener(this)
    }

    @Override
    fun onDetachedFromActivityForConfigChanges() {
    }

    @Override
    fun onReattachedToActivityForConfigChanges(@NonNull binding: ActivityPluginBinding) {
        mActivity = binding.getActivity()
    }

    @Override
    fun onDetachedFromActivity() {
    }

    @Override
    fun onMessage(@Nullable message: Object?, @NonNull reply: BasicMessageChannel.Reply?) {
        val overlayMessageChannel = BasicMessageChannel(
            FlutterEngineCache.getInstance().get(OverlayConstants.CACHED_TAG)
                .getDartExecutor(),
            OverlayConstants.MESSENGER_TAG, JSONMessageCodec.INSTANCE
        )
        overlayMessageChannel.send(message, reply)
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    @Override
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == REQUEST_CODE_FOR_OVERLAY_PERMISSION) {
            pendingResult.success(checkOverlayPermission())
            return true
        }
        return false
    }
}