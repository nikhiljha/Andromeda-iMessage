package eu.aero2x.andromedab

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.AsyncTask
import com.google.android.material.snackbar.Snackbar
import android.view.View

/**
 * Created by Salman on 2/17/17.
 */

object UITools {

    var DATA_NEEDS_REFRESH = 581

    /**
     * Show a dismissible snackbar
     * @param view On view
     * @param message With Message
     */
    fun showDismissableSnackBar(view: View, message: String) {
        val snackBar = Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE)

        snackBar.setAction("Dismiss") { snackBar.dismiss() }
        snackBar.show()
    }

    /**
     * Show a snackbar
     * @param view On view
     * @param message With Message
     * @param duration Snackbar duration
     */
    fun showSnackBar(view: View, message: String, duration: Int) {
        val snackBar = Snackbar.make(view, message, duration)
        snackBar.show()
    }

    /**
     * Showing an alert dialog with info from onCreate is needed however this can sometimes create a race condition which crashes the app if we get a response from the server before the activity is ready
     * https://stackoverflow.com/a/4713487/1166266
     * @param context The activity context
     * @param title The title for the alert
     * @param message The alert message
     */
    fun showAlertDialogSafe(context: Context, viewToWaitForID: Int, title: String, message: String) {
        //Supposedly you can create an activity reference from context
        val activity = context as Activity
        val targetView = activity.findViewById<View>(viewToWaitForID)
        //Check if we have a valid target and the activity isn't being destroyed under us.
        if (targetView != null && activity.isFinishing == false) {

            targetView.post {
                val alertDialog = AlertDialog.Builder(context).create()
                alertDialog.setTitle(title)
                alertDialog.setMessage(message)
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK"
                ) { dialog, which -> dialog.dismiss() }
                alertDialog.show()
            }
        }
    }
}
