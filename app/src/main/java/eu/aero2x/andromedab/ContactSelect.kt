package eu.aero2x.andromedab

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout

import com.android.volley.Response
import com.android.volley.VolleyError
import com.github.javiersantos.appupdater.AppUpdater
import com.github.javiersantos.appupdater.enums.UpdateFrom
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crash.FirebaseCrash
import com.google.firebase.iid.FirebaseInstanceId
import com.stfalcon.chatkit.commons.ImageLoader
import com.stfalcon.chatkit.commons.models.IDialog
import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.commons.models.IUser
import com.stfalcon.chatkit.dialogs.DialogsList
import com.stfalcon.chatkit.dialogs.DialogsListAdapter

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.Date

class ContactSelect : AppCompatActivity() {

    private var dialogsListAdapter: DialogsListAdapter<*>? = null
    internal var conversationList: ArrayList<IDialog<*>>? = null
    internal lateinit var conversationDataSource: ArrayList<JSONObject>
    private var incomingNotificationContact = ""
    private var mFirebaseAnalytics: FirebaseAnalytics? = null
    var socketClient: SocketClient? = null

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        //Check if we were notification launched
        val data = intent.data
        //Do we have a payload AND do we have a contact name?
        if (data != null && data.lastPathSegment != null) {
            Log.d("onNewIntent", "Launching with " + data.lastPathSegment!!)
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "View notification")
            mFirebaseAnalytics!!.logEvent("launch_with_notification", bundle)
            //Store our request and parse out broken characters since that's what the menu is
            incomingNotificationContact = data.lastPathSegment!!.replace("[^\\x00-\\x7F]".toRegex(), "")
            //Check if we need to load first if we've launched
            if (conversationList == null || conversationList!!.size == 0) {
                Log.w("onNewIntent", "We don't have a conversation list! We are currently getting one and we should be called later")
            } else {
                //Force a conversation reload so we actually go into the right one. We don't load twice because of the null check above.
                setupConversations()
            }

        } else {
            Log.d("onNewIntent", "Launched without a URI param")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_select)

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this)
        //Setup our update from github
        AppUpdater(this)
                .setUpdateFrom(UpdateFrom.GITHUB)
                .setGitHubUserAndRepo("shusain93", "Andromeda-iMessage").showEvery(5)
                .start()
        println("Firebase notification token:" + FirebaseInstanceId.getInstance().token!!)
        //Load our config database
        val sharedPreferences = getSharedPreferences("CONFIG", Context.MODE_PRIVATE)
        //Check if we are not yet setup
        if (sharedPreferences.getString("apiIPEndpoint", null) == null) {
            val builder = AlertDialog.Builder(this)
            //you should edit this to fit your needs
            builder.setTitle("Andromeda Configuration")
            builder.setMessage("To use Andromeda you must have a server running OSXMessageProxy. \n\nIf you make a mistake, open the menu up top and choose 'Reset configuration...'")

            val apiIPEndPoint = EditText(this)
            apiIPEndPoint.hint = "your.domain.com or 182.123.321.164"
            val apiPort = EditText(this)
            apiPort.hint = "API port (default:8735)"
            val socketPort = EditText(this)
            socketPort.hint = "Socket port (default:8736)"
            val apiProtectionKey = EditText(this)
            apiProtectionKey.hint = "API key EXACTLY as in server"
            //Check if we have an old stored key so we can load that back
            val oldProtectionKey = sharedPreferences.getString("apiProtectionKey", null)
            if (oldProtectionKey != null) {
                apiProtectionKey.setText(oldProtectionKey)
            }

            //in my example i use TYPE_CLASS_NUMBER for input only numbers
            apiIPEndPoint.inputType = InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            apiProtectionKey.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            apiPort.inputType = InputType.TYPE_CLASS_NUMBER
            socketPort.inputType = InputType.TYPE_CLASS_NUMBER

            val lay = LinearLayout(this)
            lay.orientation = LinearLayout.VERTICAL
            lay.addView(apiIPEndPoint)
            lay.addView(apiPort)
            lay.addView(socketPort)
            lay.addView(apiProtectionKey)
            builder.setView(lay)

            // Set up the buttons
            builder.setPositiveButton("OK") { dialog, whichButton ->
                val givenEndPoint = apiIPEndPoint.text.toString().trim { it <= ' ' }
                val givenKey = apiProtectionKey.text.toString().trim { it <= ' ' }

                var apiPortText = apiPort.text.toString().trim { it <= ' ' }
                if (apiPortText == "") {
                    //No API port given, default
                    apiPortText = "8735"
                }
                var socketPortText = socketPort.text.toString().trim { it <= ' ' }
                if (socketPortText == "") {
                    //No socket port given, default
                    socketPortText = "8736"
                }

                val givenAPIPort = Integer.valueOf(apiPortText)
                val givenSocketPort = Integer.valueOf(socketPortText)

                val editor = getSharedPreferences("CONFIG", Context.MODE_PRIVATE).edit()
                editor.putString("apiIPEndpoint", givenEndPoint)
                editor.putInt("apiPort", givenAPIPort)
                editor.putInt("socketPort", givenSocketPort)
                editor.putString("apiProtectionKey", givenKey)
                //Write sync because we need this done before we can keep going
                editor.commit()
                //We're ready
                prepareView()
            }

            builder.setNegativeButton("Exit") { dialog, whichButton -> finishAffinity() }
            builder.show()
        } else {
            //We have already configured
            prepareView()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_conversations, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        when (item.itemId) {
            R.id.resetConfig -> {
                val alertDialog = AlertDialog.Builder(this@ContactSelect).create()
                alertDialog.setTitle("Are you sure you want to reset the application?")
                alertDialog.setMessage("This will remove the entire application configuration and close the application.")
                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Reset"
                ) { dialog, which ->
                    val bundle = Bundle()
                    bundle.putString(FirebaseAnalytics.Param.VALUE, "Erased settings")
                    mFirebaseAnalytics!!.logEvent("app_menu_reset_settings", bundle)
                    val editor = getSharedPreferences("CONFIG", Context.MODE_PRIVATE).edit()
                    //Nuke all preferences
                    editor.putString("apiIPEndpoint", null)
                    editor.putInt("apiPort", 0)
                    editor.putInt("socketPort", 0)
                    //Force safe instantly.
                    editor.commit()
                    //Close the activity
                    finishAffinity()
                    //Kill ourselves so that it's a completely clean state.
                    System.exit(0)
                }
                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel"
                ) { dialog, which ->
                    val bundle = Bundle()
                    bundle.putString(FirebaseAnalytics.Param.VALUE, "Erase settings canceled")
                    mFirebaseAnalytics!!.logEvent("app_menu", bundle)
                    dialog.dismiss()
                }
                alertDialog.show()
                return true
            }
            R.id.openProjectPage -> {
                val builder = CustomTabsIntent.Builder()
                val customTabsIntent = builder.build()
                customTabsIntent.launchUrl(this@ContactSelect, Uri.parse("https://github.com/shusain93/Andromeda-iMessage"))

                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun prepareView() {
        //Prepare our APP_CONSTANTS
        val sharedPreferences = getSharedPreferences("CONFIG", Context.MODE_PRIVATE)
        APP_CONSTANTS.SERVER_IP = sharedPreferences.getString("apiIPEndpoint", "0.0.0.0")
        APP_CONSTANTS.SERVER_PROTECTION_TOKEN = sharedPreferences.getString("apiProtectionKey", "noStoredProtectionToken")
        APP_CONSTANTS.SERVER_API_PORT = sharedPreferences.getInt("apiPort", 0)
        APP_CONSTANTS.SERVER_SOCKET_PORT = sharedPreferences.getInt("socketPort", 0)

        //Check for our conversation intents
        onNewIntent(this.intent)
        //Setup our conversation UI
        val dialogsListView: DialogsList = findViewById(R.id.dialogsList)

        val imageLoader = ImageLoader { imageView, url ->
            //If you using another library - write here your way to load image
        }

        //Build our adapter
        dialogsListAdapter = DialogsListAdapter<IDialog<IMessage>>(imageLoader)

        dialogsListAdapter!!.setOnDialogClickListener(DialogsListAdapter.OnDialogClickListener<IDialog<*>> { dialog ->
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Trying to show a conversation")
            mFirebaseAnalytics!!.logEvent("show_conversation", bundle)
            val i = Intent(applicationContext, Conversation::class.java)
            i.putExtra("conversationJSONString", conversationDataSource[Integer.valueOf(dialog.id)].toString()) //send our conversation's JSON along
            startActivityForResult(i, UITools.DATA_NEEDS_REFRESH)
        })
        dialogsListView.setAdapter(dialogsListAdapter!!, false)

        RemoteMessagesInterface.messagesEndPointReachable(this, Response.Listener{ response ->
            try {
                val versionObject = JSONObject(response)
                //Log the server version
                val bundle = Bundle()
                bundle.putString(FirebaseAnalytics.Param.VALUE, versionObject.getString("version"))
                mFirebaseAnalytics!!.logEvent("server_version_" + versionObject.getString("version"), bundle)

                val serverVersion = Version(versionObject.getString("version"))


                //Check if our server version is below the app's required
                if (serverVersion.compareTo(Version(BuildConfig.MIN_SERVER_VERSION)) < 0) {
                    UITools.showAlertDialogSafe(this@ContactSelect, R.id.activity_contact_select, "Server version too old", "Your server is running version " + serverVersion.get() + " but the BUILDCONFIG for the application demands that you be running at least " + BuildConfig.MIN_SERVER_VERSION + "\n\nYou can continue to use the application however behavior is entirely undocumented.")
                }
                //We are online!
                UITools.showSnackBar(findViewById(android.R.id.content), "Successfully connected!", Snackbar.LENGTH_LONG)
                //Since we can see the server, setup our contacts
                setupConversations()
            } catch (e: JSONException) {
                UITools.showAlertDialogSafe(this@ContactSelect, R.id.activity_contact_select, "Server version too old", "The server should have responded with a version number JSON at /isUp. You can continue to use the app but it is highly recommended that you update the server ASAP;\nServer said:" + response + "\n" + e.toString())
                //Log the server version
                val bundle = Bundle()
                bundle.putString(FirebaseAnalytics.Param.VALUE, "<1.1.1")
                mFirebaseAnalytics!!.logEvent("server_version", bundle)
                //Since we can see the server, setup our contacts
                setupConversations()
            } catch (e: IllegalArgumentException) {
                UITools.showAlertDialogSafe(this@ContactSelect, R.id.activity_contact_select, "Server version invalid", "The server should have responded with a version number JSON at /isUp but we got:" + response + "\n" + e.toString())
                //Log the server version
                val bundle = Bundle()
                bundle.putString(FirebaseAnalytics.Param.VALUE, response)
                mFirebaseAnalytics!!.logEvent("server_version_cant_read", bundle)
            }
        }, Response.ErrorListener{ error ->
            //We couldn't connect, die.
            val err = if (error.toString() == null) "Generic network error" else error.toString()
            error.printStackTrace()

            UITools.showAlertDialogSafe(this@ContactSelect, R.id.activity_contact_select, "Couldn't connect to endpoint", "The server didn't respond.\n$err")
        })
    }

    /**
     * Try to find a conversation (and then launch it) given incomingNotificationContact
     *
     * ****WARNING*****: THIS SEARCHES FIRST WORD ONLY SO IF YOU HAVE TWO PEOPLE WITH THE SAME FIRST NAME CHARGE THE DAMN THING
     * This is a bug from the way contacts are sent from RemoteMessages. Theoritically we are safe since the most recent match should be the one we want (since it's from a notification, it's NOW)
     *
     * ****WARNING*****: THIS LAUNCHES THE MOST RECENT MATCH OF USER
     * This is because our BBBulletin doesn't specify a conversation instead just a sender so we assume it's the latest given that this is sourced from notifications
     */
    private fun tryToShowConversationWithContactName() {
        Log.d("Contacts", "Trying to show!")
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Trying to show a conversation based on contact name")
        mFirebaseAnalytics!!.logEvent("show_conversation", bundle)
        var foundConversation = false //Are we successful?
        for (i in conversationList!!.indices) { //Integrate all conversationJSONDatabase starting from top. We prioritize latest per WARNINGS above
            //Check if our search term is in the conversation name
            try {
                val conversationLabel = conversationDataSource[i].getString("IDs")
                if (conversationLabel.contains(incomingNotificationContact)) {
                    Log.d("ContactNotifier", "Found at $i")
                    incomingNotificationContact = ""
                    //Record that we found it
                    foundConversation = true

                    //We found it, let's show it
                    val launchIntent = Intent(applicationContext, Conversation::class.java)
                    launchIntent.putExtra("conversationJSONString", conversationDataSource[i].toString()) //send our conversation's JSON along
                    startActivityForResult(launchIntent, UITools.DATA_NEEDS_REFRESH)
                    //And kill the loop
                    break
                }
            } catch (e: JSONException) {
                FirebaseCrash.logcat(Log.WARN, "ContactNotifier", "Couldn't find IDs for $i")
                FirebaseCrash.report(e)
            }

        }

        //Check if we succeed
        if (!foundConversation) {
            incomingNotificationContact = ""
            UITools.showDismissableSnackBar(findViewById(android.R.id.content), "Unable to find conversation!\n$incomingNotificationContact")
        }

    }

    private fun setupConversations() {
        RemoteMessagesInterface.getConversations(this, Response.Listener{ response -> handleConversationBundle(response) }, Response.ErrorListener{ error ->
            //We couldn't connect, die.
            val err = if (error.toString() == null) "Generic network error" else error.toString()
            UITools.showDismissableSnackBar(findViewById(android.R.id.content), "Unable to load conversationJSONDatabase!\n$err")
        })
    }

    /**
     * Take the string response for conversations and prepare it for the UI
     * @param response
     */
    protected fun handleConversationBundle(response: String?) {
        try {
            val conversationJSONDatabase = JSONArray(response)

            //Create our storage
            conversationList = ArrayList()
            //Create our JSON object datasource
            conversationDataSource = ArrayList()
            val conversationCount = conversationJSONDatabase.length()

            for (i in 0 until conversationCount) {
                val conversation = conversationJSONDatabase.getJSONObject(i)
                conversationDataSource.add(i, conversation) //store our conversation
            }

            //Note to future self:: don't remove this sort here. If you do, you break the contact search
            Collections.sort(conversationDataSource, Comparator { t0, t1 ->
                try {
                    return@Comparator Integer.compare(t1.getJSONObject("lastMessage").getInt("date"), t0.getJSONObject("lastMessage").getInt("date"))
                } catch (e: JSONException) {
                    e.printStackTrace()
                    FirebaseCrash.log("Compare failed")
                    FirebaseCrash.report(e)
                    return@Comparator -1
                }
            })

            for (i in 0 until conversationCount) {
                //Grab our conversation ahead of time
                val conversation = conversationDataSource[i]

                conversationList!!.add(object : IDialog<IMessage> {
                    override fun getId(): String {
                        return "" + i
                    }

                    override fun getDialogPhoto(): String? {
                        return null
                    }

                    override fun getDialogName(): String {
                        try {
                            return conversation.getString("display_name")
                        } catch (e: JSONException) {
                            FirebaseCrash.log("Nameless chat error")
                            return "Nameless chat error"
                        }

                    }

                    override fun getUsers(): List<IUser> {
                        val users = ArrayList<IUser>()
                        users.add(object : IUser {
                            override fun getId(): String {
                                return "number"
                            }

                            override fun getName(): String {
                                return "FIRST PERSON"
                            }

                            override fun getAvatar(): String? {
                                return null
                            }
                        })
                        return users
                    }

                    override fun getLastMessage(): IMessage {
                        return object : IMessage {
                            override fun getId(): String? {
                                return null
                            }

                            override fun getText(): String {
                                try {
                                    return conversation.getJSONObject("lastMessage").getString("text")
                                } catch (e: JSONException) {
                                    return ""
                                }

                            }

                            override fun getUser(): IUser {
                                return object : IUser {
                                    override fun getId(): String {
                                        return "number"
                                    }

                                    override fun getName(): String {
                                        return "FIRST PERSON"
                                    }

                                    override fun getAvatar(): String? {
                                        return null
                                    }
                                }
                            }

                            override fun getCreatedAt(): Date {
                                try {
                                    //Convert from cocoa to epoch hence 978307200 and then to ms
                                    return Date((conversation.getJSONObject("lastMessage").getInt("date") + 978307200L) * 1000)
                                } catch (e: JSONException) {
                                    return Date()
                                }

                            }
                        }
                    }

                    override fun setLastMessage(message: IMessage) {

                    }

                    override fun getUnreadCount(): Int {
                        return 0
                    }
                })
            }

            /* Collections.sort(conversationList, new Comparator<IDialog>() {
                @Override
                public int compare(IDialog t0, IDialog t1) {
                        return t1.getLastMessage().getCreatedAt().compareTo(t0.getLastMessage().getCreatedAt());


                }
            });*/
            dialogsListAdapter!!.setItems(conversationList)
            Log.d("Contacts", "Notification: $incomingNotificationContact")
            //Now that we're done, check if we waited to launch a conversation
            if (incomingNotificationContact == "" == false) {
                Log.d("Contacts", "We have a search from the dead!")
                //We have a search!
                tryToShowConversationWithContactName()
            }

        } catch (e: JSONException) {
            FirebaseCrash.log("Couldn't parse conversation json")
            FirebaseCrash.report(e)
            UITools.showDismissableSnackBar(findViewById(android.R.id.content), "JSON error:\n" + e.toString())
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UITools.DATA_NEEDS_REFRESH) {
            Log.d("Conversation", "Came back from conversations, checking for update payload")
            if (data != null && data.hasExtra("latestSocketConversationData")) {
                Log.d("Conversation", "We have a data package to update for")
                val newData = data.extras!!.getString("latestSocketConversationData")
                handleConversationBundle(newData)
            } else {
                Log.d("Conversation", "No latestSocketConversationData to use")
            }
        }
    }

    public override fun onPause() {
        super.onPause()  // Always call the superclass method first
        Log.d("onPauseContacts", "SUSPENDING SOCKET")
        if (socketClient != null && socketClient!!.socketThread != null) {
            socketClient!!.socketThread.cancel(false)
        }
    }

    public override fun onResume() {
        super.onResume()
        if (socketClient != null && socketClient!!.socketThread.isCancelled == false) {
            socketClient!!.socketThread.cancel(false)
        }

        //We need this check because onResume ignores config. Can't connect to an empty int. 0 is default int value.
        if (APP_CONSTANTS.SERVER_SOCKET_PORT != 0) {
            socketClient = SocketClient(APP_CONSTANTS.SERVER_IP, APP_CONSTANTS.SERVER_SOCKET_PORT, SocketResponseHandler { response ->
                runOnUiThread {
                    try {
                        val bundle = JSONObject(response)
                        if (bundle.getString("type") == "conversations") {
                            //New conversation bundle!
                            handleConversationBundle(bundle.getJSONArray("content").toString())
                        } else {
                            //We don't want to handle this, notify children
                        }
                    } catch (e: JSONException) {
                        Log.d("handleSocket", "failed to parse bundle $response")
                        e.printStackTrace()
                    }
                }
            })
        }

    }

}
