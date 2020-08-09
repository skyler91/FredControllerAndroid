package one.skydev.fredcontroller

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.action_login -> {
                openGoogleSignInActivity()
                true
            }
            R.id.action_settings -> {
                startSettings()
                true
            }
            R.id.action_test -> {
                startTestActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openGoogleSignInActivity() {
        this.startActivity(Intent(this, GoogleLoginActivity::class.java))
    }

    private fun startTestActivity() {
        this.startActivity(Intent(this, TestActivity::class.java))
    }

    private fun startSettings() {
        print("Starting settings...")
    }
}