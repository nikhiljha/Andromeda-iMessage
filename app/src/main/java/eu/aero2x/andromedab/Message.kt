package eu.aero2x.andromedab

import com.stfalcon.chatkit.commons.models.IMessage

import java.util.Date

/**
 * An extension of the IMessage interface
 * I've added more iMessage required features like delivery status and read status.
 */

interface Message : IMessage {
    /**
     * Returns the read status
     * @return has this message been read
     */
    val isRead: Boolean

    /**
     * If the message has been read, when?
     * @return The time string given by remotemessages for read.
     */
    val timeRead: String

    /**
     * Has the message delivered yet?
     * @return Delivery status
     */
    val isDelivered: Boolean

    /**
     * Has the message been sent on the server?
     * @return
     */
    val isSent: Boolean
}
