package eu.aero2x.andromedab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ImageView

import com.android.volley.Response
import com.android.volley.VolleyError
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crash.FirebaseCrash
import com.squareup.picasso.Picasso
import com.stfalcon.chatkit.commons.ImageLoader
import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.commons.models.IUser
import com.stfalcon.chatkit.messages.MessageInput
import com.stfalcon.chatkit.messages.MessagesList
import com.stfalcon.chatkit.messages.MessagesListAdapter

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.util.ArrayList
import java.util.Date

class Conversation : AppCompatActivity(), MessagesListAdapter.OnMessageClickListener<CustomMessage> {
    internal lateinit var messagesListAdapter: MessagesListAdapter<*>
    internal lateinit var hash: String
    internal lateinit var displayName: String
    internal var hashManualCustomName = false

    internal lateinit var parentConversation: JSONObject
    var socketClient: SocketClient? = null
    private var mFirebaseAnalytics: FirebaseAnalytics? = null

    private var latestConversationData = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)
        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this)


        //Start to pull out our data
        val extras = intent.extras
        if (extras != null) {
            try {
                val conversation = JSONObject(extras.getString("conversationJSONString"))
                parentConversation = conversation
                hash = conversation.getString("chat_id")
                IDs = conversation.getString("IDs")
                displayName = conversation.getString("display_name")
                hashManualCustomName = conversation.getBoolean("has_manual_display_name")

                title = displayName //and setup our title!
            } catch (e: JSONException) {
                e.printStackTrace()
                UITools.showDismissableSnackBar(findViewById(android.R.id.content), "Intent Parse Error\n" + e.message)
            }


            val messagesList = findViewById(R.id.messagesList) as MessagesList
            val imageLoader = ImageLoader { imageView, url -> Picasso.with(this@Conversation).load(url).into(imageView) }

            val holdersConfig = MessagesListAdapter.HoldersConfig()
            holdersConfig.setOutcoming(CustomOutgoingMessage::class.java, R.layout.custom_outgoing_message_holder)
            holdersConfig.setIncoming(CustomIncomingMessage::class.java, R.layout.custom_incoming_message_holder)
            messagesListAdapter = MessagesListAdapter<IMessage>("0", holdersConfig, imageLoader) //0 here is the sender id which is always 0 with RemoteMessages
            //Enable our tap to open images in browser
            (messagesListAdapter as MessagesListAdapter<IMessage>).setOnMessageClickListener(this)
            messagesList.setAdapter(messagesListAdapter)

            //Now we're ready, setup!
        } else {
            UITools.showDismissableSnackBar(findViewById(android.R.id.content), "Missing conversation hash, did you launch normally?")
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Missing conversation hash, did you launch normally?")
            mFirebaseAnalytics!!.logEvent("missing_conversation_hash", bundle)
        }

        //Setup our input bar
        val inputView = findViewById(R.id.input) as MessageInput
        inputView.setInputListener { input ->
            //Create our pseudo message string
            val messageString = "{\n" +
                    "    \"sender\" : \"0\",\n" +
                    "    \"human_name\" : \" \",\n" +
                    "    \"date_read\" : 0,\n" +
                    "    \"message_id\" : 0000,\n" +
                    "    \"date\" : " + (System.currentTimeMillis() / 1000 - 978307200L) + ",\n" + //Convert epoch to cocoa

                    "    \"text\" : \"" + input.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\",\n" + //manually escape for JSON. It's bad but unexploitable

                    "    \"is_from_me\" : 1,\n" +
                    "    \"error\" : -23813,\n" +
                    "    \"guid\" : \"notSent" + (Math.random() * 2000).toInt() + "\",\n" + //random bit so we can be sure we replace the right GUID

                    "    \"date_delivered\" : 0,\n" +
                    "    \"is_sent\" : 0,\n" +
                    "    \"has_attachments\" : false\n " +
                    "  }"
            println("TIMESTAMP:" + (System.currentTimeMillis() / 1000 - 978307200L))
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Send message")
            mFirebaseAnalytics!!.logEvent("message_send", bundle)
            try {
                val messageBundle = JSONObject(messageString)
                //"convert" our json into messages. This is wasteful but updates everything I've already set up to keep last back properly
                val newMessages = parseMessageBundle(messageBundle)
                //Add it to the view
                messagesListAdapter.addToStart(newMessages[0], true)
                messageDataStore.add(0, newMessages[0])
                val recipients: String
                if (hashManualCustomName) {
                    recipients = IDs
                    println("WE HAVE A CUSTOM NAME $IDs")
                } else {
                    recipients = displayName
                    println("NO HAVE A CUSTOM NAME $displayName")
                }

                RemoteMessagesInterface.sendMessage(recipients, input.toString(), parentConversation.getBoolean("has_manual_display_name"), this@Conversation, Response.Listener{
                    //The message sent!
                    UITools.showSnackBar(findViewById(android.R.id.content), "Dispatched!", Snackbar.LENGTH_SHORT)
                }, Response.ErrorListener { error ->
                    //We couldn't connect, die.
                    val err = if (error.toString() == null) "Generic network error" else error.toString()
                    UITools.showDismissableSnackBar(findViewById(android.R.id.content), "Unable to send message! Copied!\n$err")
                    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboardManager.primaryClip = ClipData.newPlainText("Copied message", input.toString())
                })
            } catch (e: JSONException) {
                //We should never get here. The json is manually generated and works
                e.printStackTrace()
                FirebaseCrash.log("JSON message bundle generator broke:$messageString")
                FirebaseCrash.report(e)
                UITools.showDismissableSnackBar(findViewById(android.R.id.content), "Somethings really broke\nThe JSON send generator broke!")
            }

            true
        }
    }


    public override fun onPause() {
        super.onPause()  // Always call the superclass method first
        Log.d("onPauseConversation", "SUSPENDING SOCKET")
        if (socketClient != null && socketClient!!.socketThread != null) {
            socketClient!!.socketThread.cancel(false)
        }
    }

    override fun onBackPressed() {
        val bundle = Bundle()
        if (latestConversationData != "") {
            bundle.putString("latestSocketConversationData", latestConversationData)
        }
        val mIntent = Intent()
        mIntent.putExtras(bundle)
        setResult(UITools.DATA_NEEDS_REFRESH, mIntent)
        super.onBackPressed()
    }

    public override fun onResume() {
        super.onResume()

        //Get our messages here. This is a very good place to reload messages since we get called at the end of onCreate
        //but we also get called when we come back from sleep or backgrounding so we ought to refresh anyways
        messagesListAdapter.clear()
        messagesListAdapter.notifyDataSetChanged()
        setupMessages()

        if (socketClient != null && socketClient!!.socketThread.isCancelled == false) {
            socketClient!!.socketThread.cancel(false)
        }

        socketClient = SocketClient(APP_CONSTANTS.SERVER_IP, APP_CONSTANTS.SERVER_SOCKET_PORT, SocketResponseHandler { response ->
            runOnUiThread {
                try {
                    val bundle = JSONObject(response)
                    if (bundle.getString("type") == "newMessage") {
                        //New message
                        val messageBundle = bundle.getJSONArray("content").getJSONObject(0)
                        if (messageBundle.getInt("chat_id") == Integer.parseInt(hash)) {
                            //We have a message from our conversation...
                            val newMessages = parseMessageBundle(messageBundle)
                            messageDataStore.addAll(newMessages) //Build our new ones in
                            for (m in newMessages) {
                                messagesListAdapter.addToStart(m, true) //reverse true to get latest at bottom
                            }

                        }
                    } else if (bundle.getString("type") == "messageSent") {
                        //We Sent!
                        val messageBundle = bundle.getJSONArray("content").getJSONObject(0)
                        println(Integer.parseInt(hash).toString() + " " + messageBundle.getInt("chat_id"))
                        if (messageBundle.getInt("chat_id") == Integer.parseInt(hash)) {
                            for (m in messageDataStore) {
                                println(m.text.trim { it <= ' ' } + "==" + messageBundle.getString("text"))
                                if (m.id.contains("notSent") && m.text.trim { it <= ' ' } == messageBundle.getString("text").trim { it <= ' ' }) {
                                    println("!!! match")
                                    val newMessages = parseMessageBundle(messageBundle)
                                    messagesListAdapter.update(m.id, newMessages[0])
                                    for (updatePayloadMessage in newMessages) {
                                        messagesListAdapter.update(m.id, updatePayloadMessage)
                                    }
                                    break
                                }
                            }
                        }
                    } else if (bundle.getString("type") == "messageSendFailure") {
                        //We couldn't send
                    } else if (bundle.getString("type") == "conversations") {
                        //Since we close the parent socket each time we restart we need to keep tabs on the conversations for them, which means letting the parent know about knew conversation changes
                        latestConversationData = bundle.getJSONArray("content").toString()
                    }
                } catch (e: JSONException) {
                    Log.d("handleSocket", "failed to parse bundle $response")
                    e.printStackTrace()
                }
            }
        })

    }

    private fun setupMessages() {
        //Now let's nab our messages. Setup data storage. We have to set this up ASAP because if we wait someone (will and has) tried to send messages before anything else loaded
        messageDataStore = ArrayList()
        RemoteMessagesInterface.getMessagesForConversation(Integer.valueOf(hash), this, Response.Listener{ response ->
            //We have our json messages, now we just need to parse it
            try {
                val messageBundle = JSONArray(response)

                //Stuff our messages
                for (i in 0 until messageBundle.length()) {
                    messageDataStore.addAll(parseMessageBundle(messageBundle.getJSONObject(i))) //Build our message list
                }
                messagesListAdapter.addToEnd(messageDataStore, true) //reverse true to get latest at bottom
            } catch (exception: JSONException) {
                UITools.showDismissableSnackBar(findViewById(android.R.id.content), "Unable to parse json data\n" + exception.toString())
                FirebaseCrash.log("Couldn't parse messagebundle:$response")
                FirebaseCrash.report(exception)
                exception.printStackTrace()
                Log.w("MessageParserDis", "Could parse message " + exception.toString())
            }
        }, Response.ErrorListener{ error ->
            val err = if (error.toString() == null) "Generic network error" else error.toString()
            UITools.showDismissableSnackBar(findViewById(android.R.id.content), "Unable to get messages!\n$err")
        })
    }

    private fun parseMessageBundle(messageBundle: JSONObject): ArrayList<Message> {
        val messages = ArrayList<Message>()
        try {
            //We have a message. Let's add it.
            messages.add(object : CustomMessage() {
                override//No time read, null
                val timeRead: String?
                    get() {
                        try {
                            return "" + messageBundle.getInt("date_read")
                        } catch (e: JSONException) {
                            return null
                        }

                    }

                override//if this we have a date value and it's positive we've been read
                val isRead: Boolean
                    get() {
                        try {

                            return messageBundle.getInt("date_read") > 0
                        } catch (e: JSONException) {
                            return false
                        }

                    }
                override val isDelivered: Boolean
                    get() {
                        try {
                            return messageBundle.getInt("date_delivered") > 0
                        } catch (e: JSONException) {
                            return false
                        }

                    }

                override val isSent: Boolean
                    get() {
                        try {
                            return messageBundle.getInt("error") == 0 || messageBundle.getInt("is_sent") == 1
                        } catch (e: JSONException) {
                            return false
                        }

                    }

                override fun getId(): String {
                    try {
                        return messageBundle.getString("guid")
                    } catch (e: JSONException) {
                        return "no id error"
                    }

                }

                override fun getText(): String {
                    var message = ""
                    try {
                        //Build our message
                        if (messageBundle.getBoolean("has_attachments")) {
                            //Build our url to show the attachment in the browser
                            message = Uri.parse(RemoteMessagesInterface.API_URL + "/attachment").buildUpon().appendQueryParameter("id", messageBundle.getString("attachment_id")).appendQueryParameter("t", APP_CONSTANTS.SERVER_PROTECTION_TOKEN).toString()
                        }

                        val messageText = messageBundle.getString("text")

                        if (messageText != "") {
                            if (messageBundle.getBoolean("has_attachments")) {
                                //Text + attachment
                                message = message + "\n" + messageText
                            } else {
                                //We have text and there is no attachment
                                message = messageText
                            }
                        }//No text, nothing to do

                    } catch (e: JSONException) {
                        e.printStackTrace()
                        message = "Couldn't read message text!"
                    }

                    return message

                }

                override fun getUser(): IUser {
                    return object : IUser {
                        override fun getId(): String {
                            try {
                                return if (messageBundle.getInt("is_from_me") == 1) {
                                    "0"
                                } else {
                                    messageBundle.getString("sender")
                                }
                            } catch (e: JSONException) {
                                Log.w("Conversation,getID", "No part id")
                                return "no part id error"
                            }

                        }

                        override fun getName(): String {
                            val id = id
                            try {
                                return messageBundle.getString("human_name")
                            } catch (e: JSONException) {
                                return "???:$id"
                            }

                        }

                        override fun getAvatar(): String? {
                            return null
                        }
                    }

                }

                override fun getCreatedAt(): Date {
                    try {
                        return Date((messageBundle.getInt("date") + 978307200L) * 1000)
                    } catch (e: JSONException) {
                        Log.w("Conversation,getDate", "No date")
                        return Date()
                    }

                }

                override fun getImageUrl(): String? {
                    try {
                        //Check if we have a resource that can by loaded inline
                        if (messageBundle.getString("uti").contains("png") || messageBundle.getString("uti").contains("jpeg")) {
                            //We have a valid loadable image. Let's build the uri
                            return RemoteMessagesInterface.API_URL + "/attachment?id=" + messageBundle.getString("attachment_id") + "&t=" + APP_CONSTANTS.SERVER_PROTECTION_TOKEN
                        } else {
                            Log.d("GetImageURL", "Didn't support the image type: " + messageBundle.getString("uri"))
                        }
                    } catch (e: JSONException) {
                    }

                    //If we're here we didn't have a valid image.
                    return null
                }
            })

            //Store our last back GUID so we know who to display labels on
            val finalMessage = messageBundle.getString("guid")
            //This works because we will ALWAYS parse our messages through this function and so the last one parsed will always be our "lastback"
            lastMessageGUID = finalMessage

            return messages
        } catch (e: JSONException) {
            UITools.showDismissableSnackBar(findViewById(android.R.id.content), "JSON error:\n" + e.toString())
            return messages
        }

    }

    override fun onMessageClick(message: CustomMessage) {
        //Grab our URL if we have it
        val imageUrlIfExsists = message.imageUrl
        if (imageUrlIfExsists != null) {
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Tapped image to launch it in browser")
            mFirebaseAnalytics!!.logEvent("message_image_tap", bundle)
            //We have an image. They've tapped the image so we want to open it in the browser
            val builder = CustomTabsIntent.Builder()
            val customTabsIntent = builder.build()
            customTabsIntent.launchUrl(this@Conversation, Uri.parse(imageUrlIfExsists))
        }
    }

    companion object {
        var lastMessageGUID = "" //The "last back" message which will have our delivery status, etc
        lateinit var IDs: String
        lateinit var messageDataStore: ArrayList<Message> //Hold all our message data
    }
}
