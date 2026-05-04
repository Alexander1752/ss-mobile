package ro.pub.cs.systems.ssproject.mqtt

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLSocketFactory

class MqttHandler(
    private val context: Context,
    brokerIp: String,
    brokerPort: String,
    private val isConnectedCallback: (Boolean) -> Unit,
    private val onCommandReceived: (String) -> Unit
) : MqttCallback {
    private val brokerUrl = "ssl://$brokerIp:$brokerPort"
    private val clientId = "${Build.MANUFACTURER}_${Build.MODEL}_${MqttClient.generateClientId()}"
    private var client: MqttClient? = null

    private fun getSSLSocketFactory(): SSLSocketFactory {
        try {
            // Load CA certificate from mkcert
            val cf = CertificateFactory.getInstance("X.509")
            val caInput: InputStream = context.assets.open("ca.crt")
            val ca = caInput.use {
                cf.generateCertificate(it)
            }

            // Create KeyStore with CA certificate
            val keyStoreType = KeyStore.getDefaultType()
            val keyStore = KeyStore.getInstance(keyStoreType).apply {
                load(null, null)
                setCertificateEntry("ca", ca)
            }

            // Create TrustManager
            val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
            val tmf = TrustManagerFactory.getInstance(tmfAlgorithm).apply {
                init(keyStore)
            }

            // Load client certificate (PKCS12 from mkcert)
            val clientKeyStore = KeyStore.getInstance("PKCS12")
            val clientCertInput: InputStream = context.assets.open("client.p12")
            // mkcert generates p12 files with empty password by default
            val keyStorePassword = "changeit".toCharArray()
            
            clientCertInput.use {
                clientKeyStore.load(it, keyStorePassword)
            }

            // Create KeyManager
            val kmfAlgorithm = KeyManagerFactory.getDefaultAlgorithm()
            val kmf = KeyManagerFactory.getInstance(kmfAlgorithm).apply {
                init(clientKeyStore, keyStorePassword)
            }

            // Create SSLContext
            val sslContext = SSLContext.getInstance("TLSv1.2").apply {
                init(kmf.keyManagers, tmf.trustManagers, null)
            }

            return sslContext.socketFactory
        } catch (e: Exception) {
            Log.e(MqttConstants.TAG, "SSL setup error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    suspend fun connect() {
        withContext(Dispatchers.IO) {
            if (client?.isConnected == true) {
                return@withContext
            }

            try {
                client = MqttClient(brokerUrl, clientId, MemoryPersistence())
                client?.setCallback(this@MqttHandler)

                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 60
                    socketFactory = getSSLSocketFactory()
                }

                client?.connect(options)
                Log.i(MqttConstants.TAG, "Connected to $brokerUrl with mTLS (mkcert)")

                client?.subscribe(MqttConstants.TOPIC_COMMANDS, 1)
                Log.i(MqttConstants.TAG, "Subscribed to ${MqttConstants.TOPIC_COMMANDS}")
            } catch (e: Exception) {
                Log.e(MqttConstants.TAG, "Connection error: ${e.message}")
                e.printStackTrace()
            } finally {
                isConnectedCallback(isConnected())
            }
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                if (client?.isConnected == true) {
                    client?.disconnect()
                }
                client?.close()
                client = null
                Log.i(MqttConstants.TAG, "Disconnected")
                isConnectedCallback(false)
            } catch (e: Exception) {
                Log.e(MqttConstants.TAG, "Connection error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun isConnected(): Boolean {
        return client?.isConnected == true
    }

    suspend fun publishImage(imageBytes: ByteArray, qos: Int = 0) {
        withContext(Dispatchers.IO) {
            if (isConnected()) {
                try {
                    val message = MqttMessage(imageBytes)
                    message.qos = qos
                    message.isRetained = false

                    client?.publish(MqttConstants.TOPIC_IMAGES, message)
                } catch (e: Exception) {
                    Log.e(MqttConstants.TAG, "Publish error: ${e.message}")
                }
            } else {
                Log.w(MqttConstants.TAG, "Cannot publish: Client is not connected")
            }
        }
    }

    override fun connectionLost(cause: Throwable?) {
        Log.w(MqttConstants.TAG, "Connection lost: ${cause?.message}")
        isConnectedCallback(false)
    }

    override fun messageArrived(topic: String?, message: MqttMessage?) {
        if (topic == MqttConstants.TOPIC_COMMANDS) {
            val command = message?.toString() ?: return
            Log.i(MqttConstants.TAG, "Received command: $command")

            onCommandReceived(command)
        }
    }

    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
}