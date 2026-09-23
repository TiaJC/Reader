package com.tianjc.reader.ui.bookshelf

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tianjc.reader.R
import com.tianjc.reader.util.enableEdgeToEdge

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, BookshelfFragment())
                .commit()
        }
    }
}
