package eu.aero2x.andromedab

/**
 * Created by Salman on 3/12/17.
 */

object APP_CONSTANTS {
    var SERVER_IP = ""
    var SERVER_API_PORT: Int = 0
    var SERVER_SOCKET_PORT: Int = 0
    var SERVER_PROTECTION_TOKEN = "" //The random token/password you configured in the CONFIG.plist in the MessageProxy. This authenticates this build of the app with the server. Don't share this token!
}
