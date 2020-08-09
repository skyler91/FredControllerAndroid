package one.skydev.fredcontroller

import android.content.Intent
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import android.widget.TextView
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

class GoogleLoginActivity() : AppCompatActivity() {
    lateinit var mGoogleSignInClient: GoogleSignInClient
    val RC_SIGN_IN: Int = 1
    private val MSG_KEY = "message"
    private val STATUS_KEY = "status"

    enum class FredStatus {
        UNKNOWN,
        ONLINE,
        OFFLINE,
        LOADING
    }
// TODO: Move all strings to res!
    private val updateFredStatusHandler : Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val bundle = msg.data
            val status = bundle.get(STATUS_KEY) as FredStatus

            findViewById<TextView>(R.id.statusTextView).text = when (status) {
                FredStatus.UNKNOWN -> getString(R.string.status_unknown)
                FredStatus.ONLINE -> getString(R.string.status_online)
                FredStatus.OFFLINE -> getString(R.string.status_offline)
                FredStatus.LOADING -> getString(R.string.status_loading)
            }

            findViewById<TextView>(R.id.statusMessage).text = bundle.get(MSG_KEY) as String
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_google_login)

        // TODO: Add silentSignIn (https://developers.google.com/identity/sign-in/android/backend-auth)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            // TODO: store Oauth id somewhere better?
            .requestIdToken(BuildConfig.CLIENT_API_KEY)
            .build()
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        // TODO: Remove this!
        //mGoogleSignInClient.signOut()

        silentSignInToGoogle()
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
            signInToGoogle()
        }
        try {
            val account = task.getResult(ApiException::class.java)
            updateUI(account)
        }
        catch (e: ApiException) {
            Log.w("ERROR", "signInResult: failed code = " + e.statusCode)
            updateUI(null)
        }
    }

    private fun updateUI(account : GoogleSignInAccount?) {
        if (account == null) {
            // Failed login!
            Toast.makeText(this, "Google sign in failed: (", Toast.LENGTH_LONG).show()
        } else {
            // Login successful!
            getFredStatus(account)
        }
    }

    private fun getFredStatus(account : GoogleSignInAccount) {
        updateFredStatus(FredStatus.LOADING)
        val url = URL("https://us-central1-rythmaddon.cloudfunctions.net/fredstatus")
        val httpClient = OkHttpClient()
        val request = Request.Builder().url(url).header("Authorization", "Bearer " + account.idToken).build()

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
                    // TODO: Cloud Function should return Json object!
                    // TODO: Parse response body and set FredStatus correctly!
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
}