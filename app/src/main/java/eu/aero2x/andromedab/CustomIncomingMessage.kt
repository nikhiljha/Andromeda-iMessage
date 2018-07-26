package eu.aero2x.andromedab

import android.text.util.Linkify
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.TextView

import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.messages.MessagesListAdapter
import com.stfalcon.chatkit.utils.DateFormatter

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Created by Salman on 2/17/17.
 */

class CustomIncomingMessage(itemView: View) : MessagesListAdapter.IncomingMessageViewHolder<Message>(itemView) {

    override fun onBind(message: Message) {
        super.onBind(message)

        val messageStatus = itemView.findViewById<View>(R.id.messageStatus) as TextView
        val contactDisplayName = itemView.findViewById<View>(R.id.contactDisplayName) as TextView
        val messageText = itemView.findViewById<View>(R.id.messageText) as TextView

        messageText.setTextIsSelectable(true)
        Linkify.addLinks(messageText, Linkify.ALL)
        messageText.linksClickable = true

        if (message.id == Conversation.lastMessageGUID) { //Are we the last back?
            //Horray!
            /*if (message.isDelivered()) {
                if (message.isRead()) {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
                    try {
                        Date parsed = sdf.parse(message.getTimeRead());
                        //We are read
                        messageStatus.setText("Read at " + new SimpleDateFormat("h:mm a").format(parsed));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                }else {
                    //We are delivered but not read
                    messageStatus.setText("Delivered");
                }
            }*/
            messageStatus.text = ""
        } else {
            //Not delivered/no label
            messageStatus.text = ""
            messageStatus.height = 0
        }

        if (Conversation.IDs.split(",".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray().size > 1) { //Do we have more than one other person? No use in labels if it's one dude
            val thisMessageIndex = Conversation.messageDataStore.indexOf(message) //Find ourselves in the stack

            //Do we have enough to safely search and then check if the person above us has the same name.
            if (Conversation.messageDataStore.size > thisMessageIndex + 1 && Conversation.messageDataStore[thisMessageIndex + 1].user.name == message.user.name) {
                //We don't need a label
                contactDisplayName.text = ""

            } else {
                contactDisplayName.text = message.user.name
            }
        } else {
            //We don't need a label since we are alone
            contactDisplayName.text = ""
            //We can destroy the cell since we never re use them in this case
            contactDisplayName.height = 0
        }

        time.text = DateFormatter.format(message.createdAt, "h:mm a")
    }


}
