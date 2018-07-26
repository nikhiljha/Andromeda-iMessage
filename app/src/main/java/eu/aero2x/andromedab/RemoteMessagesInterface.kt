package eu.aero2x.andromedab

import android.content.Context
import android.util.Log

import com.android.internal.http.multipart.MultipartEntity
import com.android.internal.http.multipart.Part
import com.android.volley.AuthFailureError
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley

import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.HashMap

/**
 * Created by Salman on 11/27/16.
 */

object RemoteMessagesInterface {
    val API_URL = "http://" + APP_CONSTANTS.SERVER_IP + ":" + APP_CONSTANTS.SERVER_API_PORT //API URL WITHOUT THE TRAILING SLASH. Example: http://yourdomain:port
    val API_PROTECTION_TOKEN = APP_CONSTANTS.SERVER_PROTECTION_TOKEN //The API protection key
    fun messagesEndPointReachable(context: Context, onResponse: Response.Listener<String>, onError: Response.ErrorListener) {
        // Instantiate the RequestQueue.
        val queue = Volley.newRequestQueue(context)
        //                  Send an invalid request which should return (Love, Jess)
        val stringRequester = StringRequest(Request.Method.GET, "$API_URL/isUp?t=$API_PROTECTION_TOKEN", onResponse, onError)

        // Add the request to the RequestQueue.
        queue.add(stringRequester)
    }

    fun getConversations(context: Context, onResponse: Response.Listener<String>, onError: Response.ErrorListener) {
        // Instantiate the RequestQueue.
        val queue = Volley.newRequestQueue(context)

        val stringRequester = StringRequest(Request.Method.GET, "$API_URL/conversations?t=$API_PROTECTION_TOKEN", onResponse, onError)

        // change timeout to a more reasonable 10 seconds
        stringRequester.retryPolicy = DefaultRetryPolicy(
                10000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)

        // Add the request to the RequestQueue.
        queue.add(stringRequester)
    }


    fun getMessagesForConversation(conversationID: Int, context: Context, onResponse: Response.Listener<String>, onError: Response.ErrorListener) {
        // Instantiate the RequestQueue.
        val queue = Volley.newRequestQueue(context)
        val stringRequester = object : StringRequest(Request.Method.POST, "$API_URL/messages", onResponse, onError) {
            @Throws(AuthFailureError::class)
            public override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                //afterID is how we send what we want in our request
                params["conversationID"] = "" + conversationID
                params["t"] = APP_CONSTANTS.SERVER_PROTECTION_TOKEN

                return params
            }
        }

        // change timeout to a more reasonable 10 seconds
        stringRequester.retryPolicy = DefaultRetryPolicy(
                10000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)

        // Add the request to the RequestQueue.
        queue.add(stringRequester)

    }

    fun sendMessage(recipients: String, message: String, hasCustomName: Boolean, context: Context, onResponse: Response.Listener<String>, onError: Response.ErrorListener) {
        val queue = Volley.newRequestQueue(context)
        val stringRequester = object : StringRequest(Request.Method.POST, "$API_URL/send", onResponse, onError) {

            @Throws(com.android.volley.AuthFailureError::class)
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["participants"] = recipients
                params["message"] = message
                params["hasCustomName"] = if (hasCustomName) "true" else "false"
                params["t"] = APP_CONSTANTS.SERVER_PROTECTION_TOKEN
                return params
            }
        }
        // Add the request to the RequestQueue.
        queue.add(stringRequester)
    }


}
