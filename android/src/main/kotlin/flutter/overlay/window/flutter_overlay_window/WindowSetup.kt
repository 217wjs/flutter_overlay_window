package flutter.overlay.window.flutter_overlay_window

import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import io.flutter.plugin.common.BasicMessageChannel

object WindowSetup {
    var height: Int = WindowManager.LayoutParams.MATCH_PARENT
    var width: Int = WindowManager.LayoutParams.MATCH_PARENT
    var flag: Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    var gravity: Int = Gravity.CENTER
    var messenger: BasicMessageChannel<Object>? = null
    var overlayTitle = "Overlay is activated"
    var overlayContent = "Tap to edit settings or disable"
    var positionGravity = "none"
    var notificationVisibility: Int = NotificationCompat.VISIBILITY_PRIVATE
    var enableDrag = false
    fun setNotificationVisibility(name: String) {
        if (name.equalsIgnoreCase("visibilityPublic")) {
            notificationVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        if (name.equalsIgnoreCase("visibilitySecret")) {
            notificationVisibility = NotificationCompat.VISIBILITY_SECRET
        }
        if (name.equalsIgnoreCase("visibilityPrivate")) {
            notificationVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
    }

    fun setFlag(name: String) {
        if (name.equalsIgnoreCase("flagNotFocusable") || name.equalsIgnoreCase("defaultFlag")) {
            flag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (name.equalsIgnoreCase("flagNotTouchable") || name.equalsIgnoreCase("clickThrough")) {
            flag =
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        if (name.equalsIgnoreCase("flagNotTouchModal") || name.equalsIgnoreCase("focusPointer")) {
            flag = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
    }

    fun setGravityFromAlignment(alignment: String) {
        if (alignment.equalsIgnoreCase("topLeft")) {
            gravity = Gravity.TOP or Gravity.LEFT
            return
        }
        if (alignment.equalsIgnoreCase("topCenter")) {
            gravity = Gravity.TOP
        }
        if (alignment.equalsIgnoreCase("topRight")) {
            gravity = Gravity.TOP or Gravity.RIGHT
            return
        }
        if (alignment.equalsIgnoreCase("centerLeft")) {
            gravity = Gravity.CENTER or Gravity.LEFT
            return
        }
        if (alignment.equalsIgnoreCase("center")) {
            gravity = Gravity.CENTER
        }
        if (alignment.equalsIgnoreCase("centerRight")) {
            gravity = Gravity.CENTER or Gravity.RIGHT
            return
        }
        if (alignment.equalsIgnoreCase("bottomLeft")) {
            gravity = Gravity.BOTTOM or Gravity.LEFT
            return
        }
        if (alignment.equalsIgnoreCase("bottomCenter")) {
            gravity = Gravity.BOTTOM
        }
        if (alignment.equalsIgnoreCase("bottomRight")) {
            gravity = Gravity.BOTTOM or Gravity.RIGHT
            return
        }
    }
}