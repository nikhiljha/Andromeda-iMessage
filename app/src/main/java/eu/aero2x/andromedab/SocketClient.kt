package eu.aero2x.andromedab

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.util.Log

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

/**
 * A client for the OSX Message Proxy socket server (exists only in v1.2+)
 */

class SocketClient
/**
 * Create a socket client for OSX Message Proxy v1.2+
 * @param ipIn The raw IP (i.e. 184.123.143.102)
 * @param portIn The port the socket servr is running at (usually 8736)
 * @param responseHandlerIn A response handler for the socket
 */
(//Our socket parameters
        private val IP: String, private val PORT: Int, var responseHandler: SocketResponseHandler) {
    lateinit var socketThread: AsyncTask<*, *, *>

    init {
        startSocket()
    }

    private fun startSocket() {
        val isCancelled = false
        socketThread = @SuppressLint("StaticFieldLeak")
        object : AsyncTask<String,String,String>() {
            override fun doInBackground(vararg p0: String?): String? {
                //Keep going until we're canceled
                while (!isCancelled()) {
                    try {
                        Log.d("Socket", "Starting")
                        val socket = Socket(IP, PORT)
                        socket.soTimeout = 30 * 1000 //We should be sending heartbeat every 2 seconds on poll so this shouldn't be a concern.
                        val socketReader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val socketWriter = PrintWriter(socket.getOutputStream(), true)

                        var isAuthenticated = false
                        while (socket.isConnected && !isCancelled()) {
                            val response = socketReader.readLine()
                            Log.d("SocketPrep", response)
                            //If we've already successfully logged in, auto pass messages to our handler
                            if (isAuthenticated && response != "TCPALIVE") {
                                responseHandler.handleResponse(response)
                            } else if (response == "ACK") {
                                //Write our token. We have to do this ASAP or else we are failed
                                socketWriter.println(APP_CONSTANTS.SERVER_PROTECTION_TOKEN)
                            } else if (response == "READY") {
                                Log.d("SocketPrep", "Socket server has accepted our login! Ready")
                                //We're ready, so set our ready so we don't handle these in our prepare anymore.
                                isAuthenticated = true

                            }//Check if we got our socket alive and it's ready for our token
                        }
                        //Try to close just in case
                        socket.close()
                    } catch (e: Exception) {
                        Log.d("SocketPrep", "Failed, shutting down! ---> Will retry in 5 seconds")
                        try {
                            Thread.sleep(5000)
                        } catch (e1: InterruptedException) {
                        }

                        e.printStackTrace()
                    }

                }
                return null
            }
        }
        socketThread.execute()
    }
}
