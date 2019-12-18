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
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.activity_account.*
import kotlinx.android.synthetic.main.content_account.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.blockstack.android.sdk.BlockstackSession
import org.blockstack.android.sdk.BlockstackSignIn
import org.blockstack.android.sdk.model.UserData
import org.json.JSONObject.NULL
import org.openintents.distribution.about.About


class AccountActivity : AppCompatActivity() {

    private lateinit var blockstackSignIn: BlockstackSignIn
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
        blockstackHelp.text =
            getString(R.string.blockstack_help, blockstackConfig.appDomain.authority)

        val sessionStore = SessionStoreProvider.getInstance(this)
        _blockstackSession = BlockstackSession(
            sessionStore,
            blockstackConfig
        )
        blockstackSignIn = BlockstackSignIn(sessionStore, blockstackConfig)
        if (intent?.action == Intent.ACTION_VIEW) {
            progressTexts = resources.getStringArray(R.array.login_texts)
            handleAuthResponse(intent)
        } else {
            onLoaded()
        }



        signInButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                blockstackSignIn.redirectUserToSignIn(this@AccountActivity)
            }
        }

        signOutButton.setOnClickListener {
            SyncUtils.removeAccount(this, getAccountName(currentUserData))
            blockstackSession().signUserOut()
            Log.d(TAG, "signed out!")
            onLoaded()
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
                // sync account should have been created already
                val account = SyncUtils.createSyncAccount(
                    this@AccountActivity,
                    getAccountName(currentUserData)
                )
                if (account != null) {
                    try {
                        val c = contentResolver.query(
                            CalendarContract.Events.CONTENT_URI,
                            arrayOf(CalendarContract.Events._ID, CalendarContract.Events._SYNC_ID),
                            "${CalendarContract.Events.DIRTY} = ?",
                            arrayOf("1"),
                            null
                        )
                        if (c?.count == 0) {
                            SyncUtils.triggerRefresh(account)
                        } else {
                            runOnUiThread {
                                Toast.makeText(
                                    this@AccountActivity,
                                    "Unsynced changes",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (e:SecurityException) {
                        Log.w(TAG, "failed to check dirty events", e)
                    }
                }
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
                accountName.text = getAccountName(currentUserData)
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

    private fun getAccountName(userData: UserData?): String? {
        val username = userData?.json?.opt("username")
        return if (username !== NULL) {
            username.toString()
        } else {
            userData?.decentralizedID
        }
    }

    private fun onSignIn() {
        if (PermissionChecker.checkSelfPermission(
                this,
                Manifest.permission.READ_CALENDAR
            ) == PermissionChecker.PERMISSION_GRANTED
        ) {

            val userData = blockstackSession().loadUserData()
            Log.d(TAG, userData.decentralizedID)
            SyncUtils.createSyncAccount(this@AccountActivity, getAccountName(userData))
            onLoaded()
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
            lifecycleScope.launch(Dispatchers.IO) {
                val it = blockstackSession().handlePendingSignIn(authResponse ?: "")
                if (it.hasErrors) {
                    runOnUiThread {
                        Toast.makeText(
                            this@AccountActivity,
                            it.error?.message ?: "Sign in failed :-/",
                            Toast.LENGTH_SHORT
                        ).show()
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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


