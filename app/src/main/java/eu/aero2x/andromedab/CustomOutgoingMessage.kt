package eu.aero2x.andromedab

import android.text.util.Linkify
import android.util.TypedValue
import android.view.View
import android.widget.TextView

import com.stfalcon.chatkit.messages.MessagesListAdapter
import com.stfalcon.chatkit.utils.DateFormatter

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Created by Salman on 2/17/17.
 */

class CustomOutgoingMessage(itemView: View) : MessagesListAdapter.OutcomingMessageViewHolder<Message>(itemView) {

    override fun onBind(message: Message) {
        super.onBind(message)

        val messageStatus = itemView.findViewById<View>(R.id.messageStatus) as TextView
        val messageText = itemView.findViewById<View>(R.id.messageText) as TextView

        messageText.setTextIsSelectable(true)
        Linkify.addLinks(messageText, Linkify.ALL)
        messageText.linksClickable = true

        if (message.id == Conversation.lastMessageGUID) { //Are we the last back?
            //Horray!
            if (message.isSent) {
                if (message.isDelivered) {
                    if (message.isRead) {
                        try {
                            //This date bundle is in cocoa time so we need to convert it
                            val epochTimeRead = (Integer.valueOf(message.timeRead) + 978307200L) * 1000

                            //We are read
                            messageStatus.text = "Read at " + SimpleDateFormat("h:mm a").format(Date(epochTimeRead))
                        } catch (e: NumberFormatException) {
                            e.printStackTrace()
                        }

                    } else {
                        //We are delivered but not read
                        messageStatus.text = "Delivered"
                    }
                } else {
                    //Are we last back BUT not yet sent?
                    //Not delivered/no label
                    messageStatus.text = "Sent"
                }
            } else {
                //Message has not yet been sent
                messageStatus.text = "Sending..."
            }
        } else {
            //We're not last back so no label
            messageStatus.text = ""
        }


        time.text = DateFormatter.format(message.createdAt, "h:mm a")
    }


}
