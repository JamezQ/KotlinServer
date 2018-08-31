package client

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.*
import java.util.concurrent.ExecutorService

/**
 *
 *
 * @author James McClain
 */
class Client private constructor(private val channel: AsynchronousSocketChannel,
                                 private val singleThreadedExecutor: ExecutorService,
                                 private val readHandler: (message: String, client: Client) -> Unit,
                                 private val disconnectCallback: (reason: String, client: Client) -> Unit) {
    init {
        if (!channel.isOpen) {
            throw Exception("Cannot start a client with a closed channel")
        }
    }

    private val writeQueue: Queue<String> = LinkedList<String>()
    
    val id = Factory.id++
    
    private val propertyList = HashMap<String, String>()
    operator fun get(prop: String): String? {
        return propertyList[prop]
    }
    operator fun set(prop: String, value: String) {
        propertyList[prop] = value
    }
    
    private val currentlyWriting: Boolean
        get() = writeQueue.isNotEmpty()

    /**
     * Send a message to this client.Client
     *
     * @param message
     */
    fun send(message: String) {
        singleThreadedExecutor.submit {
            // If a write loop is happening, we can just add to the queue and it will get to it
            if (currentlyWriting) {
                writeQueue.add(message)
            } else { // We need to start a new write loop.
                writeQueue.add(message)
                sendLoop()
            }
        }
    }

    fun sendln(message: String) = send(message + "\n")

    /**
     * Checks if an element is in the write queue, if so, this will continue sending items in the queue until
     * the queue is empty.
     */
    private fun sendLoop() {
        singleThreadedExecutor.submit {
            if (writeQueue.isNotEmpty()) {
                val currentBuffer = ByteBuffer.wrap(writeQueue.peek().toByteArray())
                channel.write<Nothing>(currentBuffer, null, object : CompletionHandler<Int, Nothing> {
                    override fun completed(result: Int, attachment: Nothing?) {
                        if (currentBuffer.hasRemaining()) {
                            // continue to write
                            println("We didn't write in one go, we have ${currentBuffer.remaining()} left.")
                            channel.write<Nothing>(currentBuffer, null, this)
                        } else {
                            // We finished writing this one
                            // We have to interact with writeQueue via the executor
                            singleThreadedExecutor.submit {
                                writeQueue.remove()
                                sendLoop()
                            }
                        }
                    }

                    override fun failed(exc: Throwable?, attachment: Nothing?) {
                        println("This happened somehow: $exc")
                    }
                })
            } else {
                //println("Nothing to write, write loop ending. currentlyWriting=$currentlyWriting")
            }
        }
    }

    // Will read over and over until EOF is reached, at which point disconnectCallback will be called.
    // When "\n" is reached, we will call readHandler
    private fun readLoop() {
        val readBuffer = ByteBuffer.allocate(256)
        var message: String = ""
        channel.read<Nothing>(readBuffer, null, object : CompletionHandler<Int, Nothing> {
            override fun completed(result: Int, attachment: Nothing?) {
                // The socket was closed
                if (result == -1) {
                    callDisconnectOnlyOnce()
                } else if (result == 0 || result < -2) {
                    println("How did this even happen")
                    callDisconnectOnlyOnce()
                } else {
                    //println("Just read data!")
                    // handle reading
                    val justReadValue = String(readBuffer.array(), 0, readBuffer.position(), Charsets.UTF_8)
                    if (!justReadValue.isEmpty()) {
                        val splitByNewline = justReadValue.split("\n")

                        for (i in splitByNewline.indices) {
                            message += splitByNewline[i]
                            val toReadHandler = message
                            if (i < (splitByNewline.size - 1)) {
                                singleThreadedExecutor.submit {
                                    readHandler(toReadHandler, this@Client)
                                }
                                message = ""
                            }
                        }
                    }
                    if (message != "") {
                        //println("Left off buffer with partial message: '$message'")
                    }
                    readBuffer.clear()
                    channel.read<Nothing>(readBuffer, null, this)
                }

            }

            override fun failed(exc: Throwable?, attachment: Nothing?) {
                println("This happened somehow while reading: $exc")
            }
        })
    }

    private var disconnetCalled = false
    private fun callDisconnectOnlyOnce(reason: String = "End of stream reached") {
        //println("Disconnect called")
        if (!disconnetCalled) {
            singleThreadedExecutor.submit {
                if (!disconnetCalled) {
                    disconnetCalled = true
                    channel.close()
                    disconnectCallback(reason, this@Client)
                }
            }
        }
    }

    companion object Factory {
        private var id = 0
        fun create(channel: AsynchronousSocketChannel,
                   singleThreadedExecutor: ExecutorService,
                   connectHandler: (client: Client) -> Unit,
                   readHandler: (message: String, client: Client) -> Unit,
                   disconnectCallback: (reason: String, client: Client) -> Unit): Client {
            val client = Client(channel, singleThreadedExecutor, readHandler, disconnectCallback)
            singleThreadedExecutor.submit {
                connectHandler(client)
                client.readLoop() // begin the readLoop
            }
            return client
        }
    }

    override fun toString(): String {
/*        val remoteAddress = channel.remoteAddress as InetSocketAddress
        val localAddress = channel.localAddress as InetSocketAddress
        val isOpen = channel.isOpen

        return """
            |remoteAddress: $remoteAddress
            |localAddress: $localAddress
            |isOpen: $isOpen
            |
            """.trimMargin()*/
        val nick = this["nick"]
        if(nick != null) {
            return "<$nick>"
        }
        return "<$id>"
    }
}
