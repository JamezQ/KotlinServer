package server

import client.Client
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.properties.Delegates

/**
 *
 *
 * @author James McClain
 */
class Server(val port: Int,
             private val connectHandler: (client: Client) -> Unit,
             private val readHandler: (message: String, client: Client) -> Unit,
             disconnectCallback: (reason: String, client: Client) -> Unit) {

    private val singleThreadedExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val disconnectCallback: (String, Client) -> Unit = { reason: String, client: Client ->
        disconnectCallback(reason, client)
        //println("SERVER: Client disconnected")
        clientList.remove(client)
    }
    val userSize: Int
        get() = clientList.size
    private val server: AsynchronousServerSocketChannel =
            AsynchronousServerSocketChannel.open().bind(InetSocketAddress(port))
    /**
     * Keep a list of clients
     */
    private val clientList: MutableList<Client> = Collections.synchronizedList(ArrayList<Client>())

    init {
        if (!server.isOpen) {
            throw Exception("Server was unable to start")
        }
        println("Sever started...")
        // Later we can add stuff to allow for a safe shutdown
        acceptLoop()
    }

    private fun acceptLoop() {
        server.accept<Nothing>(
                null,
                object : CompletionHandler<AsynchronousSocketChannel, Nothing> {
                    override fun completed(result: AsynchronousSocketChannel, attachment: Nothing?) {
                        // We have a new open channel
                        // being accepting another now
                        server.accept<Nothing>(null, this)
                        // Lets now handle the channel we just accepted.
                        val client =
                                Client.create(result,
                                        singleThreadedExecutor, connectHandler, readHandler, disconnectCallback)
                        clientList.add(client)
                    }

                    override fun failed(exc: Throwable, attachment: Nothing?) {
                        println("This happened some how during accept: $exc")
                    }
                }
        )
    }

    override fun toString(): String {
        return "Port: $port\n${clientList.size} users:\n" + clientList.fold("", { acc, client ->
            "$acc    $client\n"
        })
    }
}