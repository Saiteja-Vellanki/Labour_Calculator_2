package com.labourcalc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SetupActivity : AppCompatActivity() {

    companion object {
        private const val SECRET = "AbhiLabour@2026#Secret"
        private const val REQ_STORAGE = 43

        /** 32-bit FNV-1a hash - identical logic exists in generator.html */
        fun fnv1a(s: String): Int {
            var h = 0x811C9DC5.toInt()
            for (ch in s) {
                h = h xor ch.code
                h *= 16777619
            }
            return h
        }

        fun deviceCode(androidId: String): String =
            String.format("%08X", fnv1a(androidId))

        fun activationCode(devCode: String): String {
            val n = (fnv1a(devCode + SECRET).toLong() and 0xFFFFFFFFL) % 900000L + 100000L
            return n.toString()
        }
    }

    private lateinit var inName: EditText
    private lateinit var inCode: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        inName = findViewById(R.id.inName)
        inCode = findViewById(R.id.inOtp)

        val devCode = deviceCode(SetupManager.deviceId(this))
        findViewById<TextView>(R.id.tvDeviceCode).text = devCode
        findViewById<TextView>(R.id.tvOtpInfo).text =
            "Send the above Device Code to the admin.\nEnter the 6-digit Activation Code you receive."

        askStoragePermission()

        findViewById<Button>(R.id.btnVerify).setOnClickListener { verify(devCode) }
    }

    private fun verify(devCode: String) {
        val name = inName.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            return
        }
        val entered = inCode.text.toString().trim()
        if (entered != activationCode(devCode)) {
            Toast.makeText(this, "Wrong activation code", Toast.LENGTH_SHORT).show()
            return
        }
        if (!storageOk()) {
            Toast.makeText(this, "Storage permission needed to save data", Toast.LENGTH_LONG).show()
            askStoragePermission()
            return
        }
        SetupManager.saveActivation(this, name)
        Toast.makeText(this, "Login successful ✔", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
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
                } catch (e2: Exception) { }
            }
        }
    }

    private fun storageOk(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
}
