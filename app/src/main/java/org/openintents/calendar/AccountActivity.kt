package org.openintents.calendar


import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import kotlinx.android.synthetic.main.activity_account.*
import kotlinx.android.synthetic.main.content_account.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.blockstack.android.sdk.BlockstackSession
import org.openintents.calendar.sync.blockstackConfig
import org.openintents.calendar.sync.executorFactory
import org.openintents.calendar.sync.j2v8Dispatcher


class AccountActivity : AppCompatActivity() {
    private val TAG = AccountActivity::class.java.simpleName

    private var _blockstackSession: BlockstackSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        signInButton.isEnabled = false
        signOutButton.isEnabled = false

        GlobalScope.launch(j2v8Dispatcher) {
            _blockstackSession = BlockstackSession(
                this@AccountActivity,
                blockstackConfig,
                executor = executorFactory(this@AccountActivity)
            )
            onLoaded()
            if (intent?.action == Intent.ACTION_VIEW) {
                handleAuthResponse(intent)
            }
        }



        signInButton.setOnClickListener { _ ->
            GlobalScope.launch(j2v8Dispatcher) {
                blockstackSession().redirectUserToSignIn { _ ->
                    Log.d(TAG, "signed in error!")
                }
            }
        }

        signOutButton.setOnClickListener { _ ->
            GlobalScope.launch(j2v8Dispatcher) {
                blockstackSession().signUserOut()
                Log.d(TAG, "signed out!")
            }
            finish()
        }
    }

    private fun onLoaded() {
        val signedIn = blockstackSession().isUserSignedIn()
        runOnUiThread {
            signInButton.isEnabled = true
            signOutButton.isEnabled = true
            if (signedIn) {
                signInButton.visibility = View.GONE
                signOutButton.visibility = View.VISIBLE
            } else {
                signInButton.visibility = View.VISIBLE
                signOutButton.visibility = View.GONE
            }
        }
        if (signedIn) {
            Log.d(TAG, blockstackSession().loadUserData()?.decentralizedID)
        }

    }

    private fun onSignIn() {
        GlobalScope.launch(j2v8Dispatcher) {
            Log.d(TAG, blockstackSession().loadUserData()?.decentralizedID)
        }
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent")

        if (intent?.action == Intent.ACTION_VIEW) {
            handleAuthResponse(intent)
        }

    }

    private fun handleAuthResponse(intent: Intent?) {
        val response = intent?.data
        Log.d(TAG, "response ${response}")
        if (response != null) {

            val authResponse = response.getQueryParameter("authResponse")
            Log.d(TAG, "authResponse: ${authResponse}")
            blockstackSession().handlePendingSignIn(authResponse) {
                if (it.hasErrors) {
                    Toast.makeText(this, it.error, Toast.LENGTH_SHORT).show()
                } else {
                    Log.d(TAG, "signed in!")
                    runOnUiThread {
                        onSignIn()
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item!!.itemId == android.R.id.home) {
            NavUtils.navigateUpFromSameTask(this)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun blockstackSession(): BlockstackSession {
        val session = _blockstackSession
        if (session != null) {
            return session
        } else {
            throw IllegalStateException("No session.")
        }
    }
}


