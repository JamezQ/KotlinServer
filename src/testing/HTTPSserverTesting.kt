package testing

import javax.crypto.Cipher
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocket

/**
 *
 *
 * @author Your Name
 */
fun main(args: Array<String>) {
    println("MaxKeylen = ${Cipher.getMaxAllowedKeyLength("AES")}")
    println("Lets try to process a https request")
    val sslFactory = SSLServerSocketFactory.getDefault() as SSLServerSocketFactory
/*    for(cipher in sslFactory.defaultCipherSuites) {
        println(cipher)
    }*/
    val server = sslFactory.createServerSocket(8081) as SSLServerSocket
    println("Lets print out info about the server")
    for (protocols in server.enabledProtocols) {
        println(protocols)
    }
    println("""
        EnableSessionCreation?: ${server.enableSessionCreation}
        NeedClientAuth?: ${server.needClientAuth}
        useClientMode?: ${server.useClientMode}
    """.trimIndent())
    val params = server.sslParameters
    println("Lets print out the params")
    params?.let {
        println("""
            AlgorithmConstrains: ${it.algorithmConstraints}
            Protocols: ${it.protocols.joinToString()}
            ServerNames: ${it.serverNames}
        """.trimIndent())
    }
    val socket = server.accept() as SSLSocket
    Thread.sleep(1000)
    
    println("We have a socket?")
    
    println("Server enabled cipher suites?:")
    println("""
        ciphers: ${server.enabledCipherSuites.joinToString()}
        want client auth: ${server.wantClientAuth}
        supported ciphers: ${server.supportedCipherSuites.joinToString()}
    """.trimIndent())
    
    
    println("isClosed?: ${socket.isClosed}")
    val inputStream = socket.inputStream
    println("Gunna try handshake?")
    
    val handshakeSession = socket.session
    println("""
        ${handshakeSession.cipherSuite}
        ${handshakeSession.localCertificates}
    """.trimIndent())
    Thread.sleep(3000)
    println("after sleep: Socket: ${socket.isClosed}")
    Thread.sleep(1000)
    socket.startHandshake()
/*    println("Gunna try to read without handshake")
    inputStream.read()
    println("We read something I think?")*/


}