package com.labourcalc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.random.Random

class SetupActivity : AppCompatActivity() {

    companion object {
        const val OTP_PHONE = "9666144894"
        private const val REQ_SMS = 42
        private const val REQ_STORAGE = 43
    }

    private var generatedOtp: String = ""

    private lateinit var inName: EditText
    private lateinit var inOtp: EditText
    private lateinit var btnSendOtp: Button
    private lateinit var btnVerify: Button
    private lateinit var tvOtpInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        inName = findViewById(R.id.inName)
        inOtp = findViewById(R.id.inOtp)
        btnSendOtp = findViewById(R.id.btnSendOtp)
        btnVerify = findViewById(R.id.btnVerify)
        tvOtpInfo = findViewById(R.id.tvOtpInfo)

        inOtp.visibility = View.GONE
        btnVerify.visibility = View.GONE

        askStoragePermission()

        btnSendOtp.setOnClickListener { sendOtp() }
        btnVerify.setOnClickListener { verifyOtp() }
    }

    private fun askStoragePermission() {
        if (Build.VERSION.SDK_INT < 30 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE
            )
        }
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            Toast.makeText(
                this, "Please allow storage access for saving Excel data", Toast.LENGTH_LONG
            ).show()
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: Exception) {
                    // device without this settings page - ignore
                }
            }
        }
    }

    private fun storageOk(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

    private fun smsManager(): SmsManager {
        // Handles dual-SIM phones: use the default SMS subscription when available
        return try {
            val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                if (Build.VERSION.SDK_INT >= 31)
                    getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
                else
                    @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(subId)
            } else {
                if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java)
                else @Suppress("DEPRECATION") SmsManager.getDefault()
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
        }
    }

    private fun sendOtp() {
        val name = inName.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "Please enter your name first", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), REQ_SMS)
            return
        }

        // 4-digit OTP
        generatedOtp = Random.nextInt(1000, 10000).toString()

        // Show OTP entry immediately - never leave the user stuck
        inOtp.visibility = View.VISIBLE
        btnVerify.visibility = View.VISIBLE
        btnSendOtp.text = "Resend OTP"

        try {
            smsManager().sendTextMessage(
                OTP_PHONE, null,
                "Labour Calculator OTP: $generatedOtp",
                null, null
            )
            tvOtpInfo.text = "OTP sent by SMS to $OTP_PHONE.\nEnter the 4-digit OTP received on that number."
            Toast.makeText(this, "OTP sent to $OTP_PHONE", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            tvOtpInfo.text = "⚠ SMS could not be sent (${e.message}).\nCheck SIM & SMS balance, then tap Resend OTP."
            Toast.makeText(this, "SMS failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun verifyOtp() {
        val entered = inOtp.text.toString().trim()
        if (generatedOtp.isEmpty()) {
            Toast.makeText(this, "Tap Send OTP first", Toast.LENGTH_SHORT).show()
            return
        }
        if (entered != generatedOtp) {
            Toast.makeText(this, "Wrong OTP, please try again", Toast.LENGTH_SHORT).show()
            return
        }
        if (!storageOk()) {
            Toast.makeText(this, "Storage permission needed to save data", Toast.LENGTH_LONG).show()
            askStoragePermission()
            return
        }
        SetupManager.saveActivation(this, inName.text.toString())
        Toast.makeText(this, "Login successful ✔", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_SMS &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            // Auto-continue instead of making the user tap again
            sendOtp()
        }
    }
}
