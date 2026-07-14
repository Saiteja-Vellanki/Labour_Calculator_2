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
        private const val REQ_PERMS = 42
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

        askPermissions()

        btnSendOtp.setOnClickListener { sendOtp() }
        btnVerify.setOnClickListener { verifyOtp() }
    }

    private fun askPermissions() {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.SEND_SMS)

        if (Build.VERSION.SDK_INT < 30 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), REQ_PERMS)
        }

        // Android 11+ : "All files access" needed for the worker_data folder
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            Toast.makeText(
                this,
                "Please allow storage access for saving Excel data",
                Toast.LENGTH_LONG
            ).show()
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    private fun storageOk(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

    private fun sendOtp() {
        val name = inName.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "Please enter your name first", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), REQ_PERMS)
            return
        }

        generatedOtp = String.format("%06d", Random.nextInt(0, 1000000))
        try {
            val sms = if (Build.VERSION.SDK_INT >= 31)
                getSystemService(SmsManager::class.java)
            else
                @Suppress("DEPRECATION") SmsManager.getDefault()

            sms.sendTextMessage(
                OTP_PHONE, null,
                "Labour Calculator OTP: $generatedOtp . Do not share.",
                null, null
            )
            tvOtpInfo.text = "OTP sent by SMS to $OTP_PHONE.\nEnter the OTP received on that number."
            inOtp.visibility = View.VISIBLE
            btnVerify.visibility = View.VISIBLE
            btnSendOtp.text = "Resend OTP"
        } catch (e: Exception) {
            Toast.makeText(this, "SMS failed: ${e.message}. Check SIM.", Toast.LENGTH_LONG).show()
        }
    }

    private fun verifyOtp() {
        if (!storageOk()) {
            Toast.makeText(this, "Storage permission needed to save data", Toast.LENGTH_LONG).show()
            askPermissions()
            return
        }
        val entered = inOtp.text.toString().trim()
        if (entered.isNotEmpty() && entered == generatedOtp) {
            SetupManager.saveActivation(this, inName.text.toString())
            Toast.makeText(this, "Login successful ✔", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            Toast.makeText(this, "Wrong OTP, please try again", Toast.LENGTH_SHORT).show()
        }
    }
}
