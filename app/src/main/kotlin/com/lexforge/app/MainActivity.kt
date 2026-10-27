package com.lexforge.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lexforge.app.ui.LexNav

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val provider = (application as LexApp).dictionary
        val incoming: Uri? = if (intent?.action == Intent.ACTION_VIEW) intent.data else null
        setContent {
            LexNav(provider, initialImage = incoming)
        }
    }
}
