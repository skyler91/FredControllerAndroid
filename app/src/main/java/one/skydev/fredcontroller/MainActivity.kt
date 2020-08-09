package one.skydev.fredcontroller

import android.content.Intent
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.time.LocalDateTime
import java.util.*

class MainActivity() : AppCompatActivity() {
    lateinit var mGoogleSignInClient: GoogleSignInClient
    private var optionsMenu : Menu? = null
    private val accountObserver : MutableLiveData<GoogleSignInAccount> = MutableLiveData()
    val RC_SIGN_IN: Int = 1
    private val MSG_KEY = "message"
    private val STATUS_KEY = "status"

    enum class FredStatus {
        UNKNOWN,
        ONLINE,
        OFFLINE,
        LOADING,
        NO_GOOGLE_AUTH
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        optionsMenu = menu
        menuInflater.inflate(R.menu.main_menu, menu)
        updateLoginMenuItems()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.action_login -> {
                signInToGoogle()
                true
            }
            R.id.action_logout -> {
                signOutFromGoogle()
                true
            }
            R.id.action_refresh -> {
                getFredStatus()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private val refreshHandler = Handler(Looper.getMainLooper())

    private val updateFredStatusHandler : Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val bundle = msg.data
            val status = bundle.get(STATUS_KEY) as FredStatus

            val statusTextView = findViewById<TextView>(R.id.statusTextView)
            val statusSpinner = findViewById<ProgressBar>(R.id.statusSpinner)
            val statusIcon = findViewById<ImageView>(R.id.statusIcon)

            when(status) {
                FredStatus.UNKNOWN -> {
                    statusTextView.text = getString(R.string.status_unknown)
                    statusSpinner.visibility = View.INVISIBLE
                    statusIcon.setImageResource(R.drawable.ic_offline)
                    statusIcon.visibility = View.VISIBLE
                }
                FredStatus.ONLINE -> {
                    statusTextView.text = getString(R.string.status_online)
                    statusSpinner.visibility = View.INVISIBLE
                    statusIcon.setImageResource(R.drawable.ic_online)
                    statusIcon.visibility = View.VISIBLE
                }
                FredStatus.OFFLINE -> {
                    statusTextView.text = getString(R.string.status_offline)
                    statusSpinner.visibility = View.INVISIBLE
                    statusIcon.setImageResource(R.drawable.ic_offline)
                    statusIcon.visibility = View.VISIBLE
                }
                FredStatus.LOADING -> {
                    statusTextView.text = getString(R.string.status_loading)
                    statusSpinner.visibility = View.VISIBLE
                    statusIcon.visibility = View.INVISIBLE
                }
                FredStatus.NO_GOOGLE_AUTH -> {
                    statusTextView.text = getString(R.string.status_no_google_auth)
                    statusSpinner.visibility = View.INVISIBLE
                    statusIcon.setImageResource(R.drawable.ic_offline)
                    statusIcon.visibility = View.VISIBLE
                }
            }

            findViewById<TextView>(R.id.statusMessage).text = bundle.get(MSG_KEY) as String
            findViewById<TextView>(R.id.lastUpdatedTime).text = Calendar.getInstance().time.toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        accountObserver.observe(this, Observer {
            updateGoogleSignInStatus()
        })

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(BuildConfig.CLIENT_API_KEY)
            .build()
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        silentSignInToGoogle()
        accountObserver.value = GoogleSignIn.getLastSignedInAccount(this)
        if (accountObserver.value == null) {
            signInToGoogle()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(task : Task<GoogleSignInAccount>) {
        if (!task.isSuccessful) {
            val msg = "Failed to sign in: ${task.exception?.message}"
            Log.e("ERROR", msg)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            accountObserver.value = null
            return
        }
        try {
            accountObserver.value = task.getResult(ApiException::class.java)
        }
        catch (e: ApiException) {
            val msg = "Failed to sign in: ${e.message} (${e.statusCode})"
            Log.e("ERROR", msg)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            accountObserver.value = null
        }
    }

    private fun updateGoogleSignInStatus() {
        invalidateOptionsMenu()
        if (accountObserver.value != null){
            // Login successful!
            getFredStatus()
            // TODO: Create setting for refresh delay
            refreshHandler.removeCallbacksAndMessages(null)
            refreshHandler.post(object : Runnable {
                override fun run() {
                    getFredStatus()
                    refreshHandler.postDelayed(this, 5000)
                }
            })
        } else {
            updateFredStatus(FredStatus.NO_GOOGLE_AUTH, getString(R.string.message_no_google_auth))
            refreshHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun getFredStatus() {
        if (accountObserver.value == null) {
            return
        }

        updateFredStatus(FredStatus.LOADING)
        val url = URL("https://us-central1-rythmaddon.cloudfunctions.net/fredstatus")
        val httpClient = OkHttpClient()
        val request = Request.Builder().url(url).header("Authorization", "Bearer " + accountObserver.value?.idToken).build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                updateFredStatus(FredStatus.UNKNOWN,"Failed to get Fred status: (${e.message})")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        updateFredStatus(FredStatus.UNKNOWN, "Unexpected return from Fred status: $response")
                        // TODO: remove throw?
                        throw IOException("Unexpected return code: $response")
                    }
                    val jsonObj = JSONObject(response.body()!!.string())
                    val status = when(jsonObj.getString("status")) {
                        "Online" -> FredStatus.ONLINE
                        "Offline" -> FredStatus.OFFLINE
                        else -> FredStatus.UNKNOWN
                    }
                    updateFredStatus(status, jsonObj.getString("message"))
                }
            }
        })
     }

    private fun updateFredStatus(status : FredStatus, msg : String = "") {
        val message = Message.obtain()
        val bundle = Bundle()
        bundle.putString(MSG_KEY, msg)
        bundle.putSerializable(STATUS_KEY, status)
        message.data = bundle
        updateFredStatusHandler.sendMessage(message)
    }

    private fun signInToGoogle() {
        val signInIntent = mGoogleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private fun silentSignInToGoogle() {
        mGoogleSignInClient.silentSignIn().addOnCompleteListener(
            this
        ) { task -> handleSignInResult(task) }
    }

    private fun signOutFromGoogle() {
        mGoogleSignInClient.signOut()
        accountObserver.value = null
    }

    private fun updateLoginMenuItems() {
        optionsMenu?.findItem(R.id.action_login)?.isVisible = accountObserver.value == null
        optionsMenu?.findItem(R.id.action_logout)?.isVisible = accountObserver.value != null
        optionsMenu?.findItem(R.id.action_refresh)?.isVisible = accountObserver.value != null
    }
}