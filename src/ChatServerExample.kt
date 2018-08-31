import client.Client
import server.Server

/**
 *
 * Create a simple chat server
 * @author James McClain
 */

fun main(args: Array<String>) {
    println("Starting server")

    val channelList: MutableMap<String, MutableList<Client>> = HashMap()


    fun Any?.whenNull(block: () -> Unit): Unit {
        if (this == null) {
            block()
        }
    }

    val server = Server(8080,
            connectHandler = { c ->
                c.sendln("Hello, welcome to my server")
                c.sendln("You have been placed in the GENERAL chat channel")
                channelList.getOrPut("GENERAL", { ArrayList() }).add(c)
                channelList["GENERAL"]?.filterNot { it == c }?.forEach {
                    it.sendln("$c has joined (New connection).")
                }
            },
            readHandler = { message, c ->
                c.sendln("You just sent: '$message'")
                listOf(
                        "join" to { channelName: String ->
                            val oldChannel = channelList.filterValues {
                                it.remove(c)
                            }.entries.first().key
                            channelList.getOrPut(channelName, { ArrayList() }).add(c)
                            c.sendln("You have been removed from $oldChannel and placed in $channelName")
                            channelList[channelName]?.filterNot { it == c }?.forEach {
                                it.sendln("$c has joined.")
                            }
                            channelList[oldChannel]?.forEach {
                                it.sendln("$c has left this channel (Joined another channel).")
                            }
                            Unit // to avoid annoying intellij formatting
                        },
                        "quit" to { quitMessage: String ->
                            c.sendln("You can't quit yet unless you leave lol.")
                        },
                        "nick" to { newNick: String ->
                            val oldNick = "$c"
                            c["nick"] = newNick
                            c.sendln("You are now known as $c")
                            channelList.filterValues { it.contains(c) }.entries.first().value.forEach {
                                if (it != c) {
                                    it.sendln("$oldNick is now known as $c")
                                }
                            }
                        },
                        "msg" to { usernameToPrivateMessage: String ->
                            c.sendln("Unsupported feature")
                        },
                        "help" to { _: String ->
                            c.sendln("""
                                |Currently supported commands:
                                |   join <channel_name> joins a channel and leaves the current one
                                |   nick <new_name> changes your name to new_name
                                |   help I won't tell you want this does.
                            """.trimMargin())
                        }
                ).find { message.startsWith(it.first + " ", true) }?.let {
                    val restOfMessage = message.removePrefix(it.first + " ")
                    it.second(restOfMessage)
                }.whenNull {
                    channelList.filterValues { it.contains(c) }.entries.first().value.forEach {
                        if (it != c) {
                            it.sendln("$c: $message")
                        }
                    }
                }
            },
            disconnectCallback = { reason, c ->
                channelList.filterValues {
                    it.remove(c)
                }.values.first().forEach {
                    it.sendln("$c has left this channel (Disconnected).")
                }
                println("User left: $reason")
            })
    println("Server started on port: ${server.port}")

    var lastSize = server.userSize
    while (true) {
        Thread.sleep(1000)
        if (server.userSize != lastSize) {
            println(server)
            lastSize = server.userSize
        }
    }
}