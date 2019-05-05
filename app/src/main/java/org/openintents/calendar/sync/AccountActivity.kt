package org.openintents.calendar.sync


import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import kotlinx.android.synthetic.main.activity_account.*
import kotlinx.android.synthetic.main.content_account.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.blockstack.android.sdk.BlockstackSession
import org.blockstack.android.sdk.model.UserData
import org.json.JSONObject.NULL
import org.openintents.distribution.about.About


class AccountActivity : AppCompatActivity() {

    private lateinit var progressTexts: Array<String>
    private var currentUserData: UserData? = null
    private val TAG = AccountActivity::class.java.simpleName

    private var _blockstackSession: BlockstackSession? = null
    private val progressHandler = Handler()
    private var progressTextIndex: Int = 0

    val progressUpdate: Runnable = object : Runnable {
        override fun run() {
            progressTextIndex %= progressTexts.size
            progressText.text = progressTexts[progressTextIndex]
            if (progressText.visibility == View.VISIBLE) {
                progressTextIndex++
                progressHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)
        setSupportActionBar(toolbar)

        account.visibility = View.GONE
        accountName.visibility = View.GONE
        accountDomain.visibility = View.GONE
        signInButton.visibility = View.GONE
        signOutButton.visibility = View.GONE
        calendarButton.visibility = View.GONE
        syncNowButton.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        blockstackHelp.visibility = View.VISIBLE
        progressTexts = resources.getStringArray(R.array.progress_texts)
        progressHandler.postDelayed(progressUpdate, 0)

        accountDomain.text = blockstackConfig.appDomain.authority
        blockstackHelp.text = getString(R.string.blockstack_help, blockstackConfig.appDomain.authority)

        GlobalScope.launch(j2v8Dispatcher) {
            _blockstackSession = BlockstackSession(
                this@AccountActivity,
                blockstackConfig,
                executor = executorFactory(this@AccountActivity)
            )
            if (intent?.action == Intent.ACTION_VIEW) {
                progressTexts = resources.getStringArray(R.array.login_texts)
                handleAuthResponse(intent)
            } else {
                onLoaded()
            }
        }



        signInButton.setOnClickListener { _ ->
            GlobalScope.launch(j2v8Dispatcher) {
                blockstackSession().redirectUserToSignIn { _ ->
                    Log.d(TAG, "signed in error!")
                }
            }
        }

        signOutButton.setOnClickListener {
            GlobalScope.launch(j2v8Dispatcher) {
                blockstackSession().signUserOut()
                Log.d(TAG, "signed out!")
                onLoaded()
            }
        }

        calendarButton.setOnClickListener {
            val builder = CalendarContract.CONTENT_URI.buildUpon()
            builder.appendPath("time")
            ContentUris.appendId(builder, System.currentTimeMillis())
            val intent = Intent(Intent.ACTION_VIEW)
                .setData(builder.build())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        accountName.setOnClickListener {
            val authorities = arrayOf(CalendarContract.AUTHORITY)
            val accountTypes = arrayOf(SyncUtils.ACCOUNT_TYPE)
            val intent = Intent(Settings.ACTION_SYNC_SETTINGS)
            intent.putExtra(Settings.EXTRA_AUTHORITIES, authorities)
            intent.putExtra(Settings.EXTRA_ACCOUNT_TYPES, accountTypes)
            startActivity(intent)
        }
        syncNowButton.setOnClickListener {
            GlobalScope.launch {
                SyncUtils.createSyncAccount(this@AccountActivity)
                SyncUtils.triggerRefresh(this@AccountActivity)
            }
        }
    }

    private fun onLoaded() {
        val signedIn = blockstackSession().isUserSignedIn()
        if (signedIn) {
            currentUserData = blockstackSession().loadUserData()
        }
        runOnUiThread {
            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
            if (signedIn) {
                val username = currentUserData?.json?.opt("username")
                accountName.text = if (username !== NULL) {
                    username.toString()
                } else {
                    currentUserData?.decentralizedID
                }
                signInButton.visibility = View.GONE
                signOutButton.visibility = View.VISIBLE
            } else {
                accountName.text = ""
                signInButton.visibility = View.VISIBLE
                signOutButton.visibility = View.GONE
            }
            calendarButton.visibility = signOutButton.visibility
            syncNowButton.visibility = signOutButton.visibility
            blockstackHelp.visibility = signInButton.visibility
            account.visibility = signOutButton.visibility
            accountName.visibility = signOutButton.visibility
            accountDomain.visibility = signOutButton.visibility
        }


    }

    private fun onSignIn() {
        if (PermissionChecker.checkSelfPermission(
                this,
                Manifest.permission.READ_CALENDAR
            ) == PermissionChecker.PERMISSION_GRANTED
        ) {
            GlobalScope.launch(j2v8Dispatcher) {
                Log.d(TAG, blockstackSession().loadUserData()?.decentralizedID)
                SyncUtils.createSyncAccount(this@AccountActivity)
                onLoaded()
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                REQUEST_CODE_PERMISSIONS
            )
        }
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
            blockstackSession().handlePendingSignIn(authResponse ?: "") {
                if (it.hasErrors) {
                    runOnUiThread {
                        Toast.makeText(this, it.error, Toast.LENGTH_SHORT).show()
                    }
                    onLoaded()
                } else {
                    Log.d(TAG, "signed in!")
                    runOnUiThread {
                        onSignIn()
                    }
                }
            }
        } else {
            onLoaded()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.menu_blockstack -> {
                startActivity(Intent(Intent.ACTION_VIEW, HELP_URI))
                return true
            }
            R.id.menu_about -> {
                startActivity(Intent(this, About::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            for (grantResult in grantResults) {
                if (grantResult != PermissionChecker.PERMISSION_GRANTED) {
                    return
                }
            }
            onSignIn()
        }
    }

    fun blockstackSession(): BlockstackSession {
        val session = _blockstackSession
        if (session != null) {
            return session
        } else {
            throw IllegalStateException("No session.")
        }
    }

    companion object {
        private val HELP_URI: Uri = Uri.parse("http://openintents.org/calendar")
        private val REQUEST_CODE_PERMISSIONS: Int = 1
    }
}


