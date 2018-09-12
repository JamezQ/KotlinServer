package testing

import sun.security.x509.*
import sun.tools.tree.ArrayExpression
import java.math.BigInteger
import java.nio.charset.Charset
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KProperty

/**
 *
 *
 * @author Your Name
 */
fun main(args: Array<String>) {
    println("Lets create an SSLContext")
    val context = SSLContext.getInstance("TLSv1.2")
    println("Now we must give it a KeyManager")
    val keyManagerFactory = KeyManagerFactory.getInstance("PKIX")
    println("Cool, now lets give it a KeyStore")
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    // Load *nothing* into the keystore
    keyStore.load(null, null)


    // Now we make a private and public key
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    // Trying with large key
    keyPairGenerator.initialize(4096)
    val pair = keyPairGenerator.genKeyPair()

    // Finally, lets create a certificate and put it in the keystore.
    // https://bfo.com/blog/2011/03/08/odds_and_ends_creating_a_new_x_509_certificate/

    val privateKey = pair.private

    val info = X509CertInfo()
    // valid for 30 days
    val certificateValidity = CertificateValidity(Date(), Date.from(Instant.now().plus(30, ChronoUnit.DAYS)))
    val serialNumber = BigInteger(64, SecureRandom())

    val owner = X500Name("CN=localhost, L=Dog, C=Cat")
    // Lets add the info the the cert

    info.set(X509CertInfo.VALIDITY, certificateValidity)
    info.set(X509CertInfo.SERIAL_NUMBER, CertificateSerialNumber(serialNumber))
    info.set(X509CertInfo.SUBJECT, owner)
    info.set(X509CertInfo.ISSUER, owner)
    info.set(X509CertInfo.KEY, CertificateX509Key(pair.public))
    info.set(X509CertInfo.VERSION, CertificateVersion(CertificateVersion.V3))
    val algoId = AlgorithmId(AlgorithmId.sha512WithRSAEncryption_oid)
    info.set(X509CertInfo.ALGORITHM_ID, CertificateAlgorithmId(algoId))

    // Sign the cert to see the actual Algo
    val cert = X509CertImpl(info)
    cert.sign(pair.private, "SHA512withRSA")

    println("Algo was: $algoId")
    val newAlgoId = cert.get(X509CertImpl.SIG_ALG) as AlgorithmId
    println("New alg: $newAlgoId")

    info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, newAlgoId)
    val newCert = X509CertImpl(info)
    newCert.sign(pair.private, "SHA512withRSA")


    // We should now have a valid cert

    val toRet = newCert as X509Certificate
    println("We have a cert!: $toRet")

    keyStore.setKeyEntry("localhost", pair.private, "".toCharArray(), arrayOf(toRet))
    println("${keyStore.getCertificate("i-did-this")}")
    // Now we have a keyStore, lets put it in the keyManagerFactory
    keyManagerFactory.init(keyStore, "".toCharArray())

    // We don't need a trustManager, we are trusting our clients.
    // We don't need to define our own secure random either.
    context.init(keyManagerFactory.keyManagers, null, null)
    println("Created the SSLContext with the self-signed key (I hope)")

    println("LETS CREATE THE SERVER AGAIN!")

    val serverSocketFactory = context.serverSocketFactory

    val secureSocket = serverSocketFactory.createServerSocket(8081) as SSLServerSocket
    println("""
        We have a server, it accepts:
        Protocols: ${secureSocket.enabledProtocols.joinToString()}
        Ciphers: ${secureSocket.enabledCipherSuites.joinToString()}
        
        I think this is too many lets move it down
    """.trimIndent())
    secureSocket.enabledProtocols = arrayOf("TLSv1.2")
    secureSocket.enabledCipherSuites = secureSocket.enabledCipherSuites.filter {
        it.contains("256") && it.contains("DHE") && !it.contains("128")
    }.toTypedArray()

    // Only allow TLSv1.2 and strong ciphers

    println("""
        After changing, it now accepts.
        Protocols: ${secureSocket.enabledProtocols.joinToString()}
        Ciphers: ${secureSocket.enabledCipherSuites.joinToString()}
        
        Much better!
    """.trimIndent())
    var count = 0
    while (true) {
        println("server waiting...")
        val socket = secureSocket.accept() as SSLSocket
        println("GOT A CLIENT!")
        socket.addHandshakeCompletedListener {
            println("We are using ${it.cipherSuite}")
        }
        val inStream = socket.inputStream
        val buffer = ByteArray(80)

        val safeRead = { inStream.read(buffer, 0, min(max(inStream.available(), 1), 80)) }

        var numRead = safeRead()
        if(numRead == -1) {
            println("dead now")
            continue
        }
        var request = String(buffer, 0, numRead, Charsets.UTF_8)
        while (!request.contains("\r\n\r\n")) {
            numRead = safeRead()
            if(numRead > 1) {
                println("We read $numRead bytes")
            }
            if(numRead == -1) {
                println("Whoops, died")
                break
            }
            request += String(buffer, 0, numRead, Charsets.UTF_8)
        }
        println("The entire request")
        println(request)

        // Lets read until delim
/*        while (inStream.read(buffer) != -1) {
            println("Just read: '${String(buffer, Charsets.UTF_8)}'")
            println("originalSession = session?: ${originalSession.contentEquals(socket.session.id)}")
        }*/
        println("Lets respond")
        
        socket.outputStream.write("""
            HTTP/1.1 200 OK
            Content-Type: text
            
            Hello ${++count}
        """.trimIndent().toByteArray())
        
        if(!socket.isClosed) {
            println("Closing socket")
            socket.close()
        }
        println("Client must have quit! ${socket.isClosed}")
        println("bye everyone!")
    }
    // Also thanks to https://github.com/wisdom-framework/wisdom/commit/15c4bb45a734ce07baf3fb461afbdb19c09254ab
}