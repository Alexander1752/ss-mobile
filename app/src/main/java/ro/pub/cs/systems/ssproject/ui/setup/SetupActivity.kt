package ro.pub.cs.systems.ssproject.ui.setup

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import ro.pub.cs.systems.ssproject.ui.dashboard.MainActivity
import ro.pub.cs.systems.ssproject.R
import java.util.regex.Pattern

class SetupActivity : ComponentActivity() {
    
    companion object {
        // Hostname validation pattern (DNS names)
        private val HOSTNAME_PATTERN = Pattern.compile(
            "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?$"
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_setup)

        val ipField = findViewById<EditText>(R.id.setup_card_view_ip_field)
        val portField = findViewById<EditText>(R.id.setup_card_view_port_field)
        val connect = findViewById<Button>(R.id.setup_card_view_connect_btn)
        
        connect.setOnClickListener {
            val inputAddress = ipField.text.toString().trim()
            val inputPort = portField.text.toString().trim()

            // Validate IP address or hostname
            if (inputAddress.isEmpty() || !isValidAddressOrHostname(inputAddress)) {
                ipField.error = "Invalid IP address or hostname"
                ipField.requestFocus()
                return@setOnClickListener
            }

            val portNumber = inputPort.toIntOrNull()
            if (portNumber == null || portNumber !in 1..65535) {
                portField.error = "Invalid port"
                portField.requestFocus()
                return@setOnClickListener
            }

            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("brokerIp", inputAddress)
            intent.putExtra("brokerPort", inputPort)
            startActivity(intent)
        }
    }
    
    private fun isValidAddressOrHostname(address: String): Boolean {
        // Check if it's a valid IP address
        if (Patterns.IP_ADDRESS.matcher(address).matches()) {
            return true
        }
        
        // Check if it's a valid hostname/domain name
        if (HOSTNAME_PATTERN.matcher(address).matches()) {
            return true
        }
        
        // Check for localhost
        if (address.equals("localhost", ignoreCase = true)) {
            return true
        }
        
        return false
    }
}