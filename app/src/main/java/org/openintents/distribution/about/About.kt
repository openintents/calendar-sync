/*   
 * 	 Copyright (C) 2008-2017 pjv (and others, see About dialog)
 * 
 * 	 This file is part of OI About.
 *
 *   OI About is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   OI About is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with OI About.  If not, see <http://www.gnu.org/licenses/>.
 *   
 *   
 *   
 *   The idea, window layout and elements, and some of the comments below are based on GtkAboutDialog. See http://library.gnome.org/devel/gtk/stable/GtkAboutDialog.html and http://www.gtk.org.
 */

package org.openintents.distribution.about

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Configuration
import android.content.res.Resources.NotFoundException
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.text.util.Linkify
import android.text.util.Linkify.TransformFilter
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageSwitcher
import android.widget.TextSwitcher
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import kotlinx.android.synthetic.main.oi_distribution_about.*
import kotlinx.android.synthetic.main.oi_distribution_about_credits.*
import kotlinx.android.synthetic.main.oi_distribution_about_info.*
import kotlinx.android.synthetic.main.oi_distribution_about_license.*
import kotlinx.android.synthetic.main.oi_distribution_about_recent_changes.*
import org.openintents.calendar.sync.R
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import java.util.regex.Pattern

/**
 * About activity.
 *
 */
class About : AppCompatActivity() {


    private var metaDataReader: MetaDataReader? = null

    /* (non-Javadoc)
     * @see android.app.ActivityGroup#onCreate(android.os.Bundle)
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.oi_distribution_about)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val res = resources
        Log.d("identifier", res.getString(res.getIdentifier("string/about_translators", null, packageName)))

        val pkgName = packageName

        val aboutPagerAdapter = AboutPagerAdapter(hasRecentChanges(pkgName, intent), this, supportFragmentManager, pkgName)
        pager.adapter = aboutPagerAdapter
        tab_layout.setupWithViewPager(pager)
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        // Generate any additional actions that can be performed on the
        // overall list. In a normal install, there are no additional
        // actions found here, but this allows other applications to extend
        // our menu with their own actions.
        val intent = Intent(null, intent.data)
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE)
        menu.addIntentOptions(
            Menu.CATEGORY_ALTERNATIVE, 0, 0,
            ComponentName(this, org.openintents.distribution.about.About::class.java), null,
            intent, 0, null
        )

        return true
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onNewIntent(android.content.Intent)
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }


    /* (non-Javadoc)
     * @see android.app.ActivityGroup#onResume()
     */
    override fun onResume() {
        super.onResume()

        // tabHost.setCurrentTabByTag(getString(R.string.l_info));

        //Decode the intent, if any
        val intent = intent
        /*
        if (intent == null) {
        	refuseToShow();
        	return;
        }
        */
        if (intent == null) {
            setIntent(Intent())
        }

        val pkgName = packageName

        Log.i(TAG, "Showing About dialog for package $pkgName")

        setResult(Activity.RESULT_OK)
    }

    /**
     * @return true if recent changes are available
     */
    protected fun hasRecentChanges(pkgName: String, intent: Intent?): Boolean {
        val resourceid = AboutUtils.getResourceIdExtraOrMetadata(
            metaDataReader, AboutMetaData.METADATA_RECENT_CHANGES
        )

        return resourceid != 0
    }

    companion object {
        //TODO BUG rotating screen broken due to TabHost?

        //private static final String LAUNCHPAD_TRANSLATOR_CREDITS_SEPARATOR = ";";
        //private static final String LAUNCHPAD_TRANSLATOR_CREDITS_REGEX = "("+LAUNCHPAD_TRANSLATOR_CREDITS_SEPARATOR+" )|("+LAUNCHPAD_TRANSLATOR_CREDITS_SEPARATOR+")";
        // Replace anything that looks like a link (starts with http) ...
        private val LAUNCHPAD_TRANSLATOR_CREDITS_REGEX_1 = "(http[^ ]*)"
        // ... by surrounding line breaks and a smaller font.
        private val LAUNCHPAD_TRANSLATOR_CREDITS_REGEX_2 = "<br/><small><small>$1</small></small><br/>"
        // for international translations omit the link altogether:
        private val LAUNCHPAD_TRANSLATOR_CREDITS_REGEX_3 = "<br/>"
        private val LAUNCHPAD_TRANSLATOR_CREDITS_HEADER =
            "(Launchpad Contributions: )|" + "(This is a dummy translation so that the credits are counted as translated. Launchpad Contributions: )"
        private val LAUNCHPAD_TRANSLATOR_CREDITS_TAG = "translator-credits"

        private val TAG = "About"

        private val layouts = arrayOf(
            R.layout.oi_distribution_about_info,
            R.layout.oi_distribution_about_credits,
            R.layout.oi_distribution_about_license,
            R.layout.oi_distribution_about_recent_changes
        )
        private val ARG_CONTENT = "content"

        val metaDataNameToTagName = HashMap<String, String>().also {
            it["comments"] = AboutMetaData.METADATA_COMMENTS
            it["copyright"] = AboutMetaData.METADATA_COPYRIGHT
            it["website-url"] = AboutMetaData.METADATA_WEBSITE_URL
            it["website-label"] = AboutMetaData.METADATA_WEBSITE_LABEL
            it["authors"] = AboutMetaData.METADATA_AUTHORS
            it["documenters"] = AboutMetaData.METADATA_DOCUMENTERS
            it["translators"] = AboutMetaData.METADATA_TRANSLATORS
            it["artists"] = AboutMetaData.METADATA_ARTISTS
            it["license"] = AboutMetaData.METADATA_LICENSE
            it["email"] = AboutMetaData.METADATA_EMAIL
            it["recent-changes"] = AboutMetaData.METADATA_RECENT_CHANGES
        }


        /**
         * Menu item id's.
         */
        val MENU_ITEM_ABOUT = Menu.FIRST
    }

    class AboutPagerAdapter(
        private val recentChanges: Boolean,
        val ctx: Context,
        fm: FragmentManager,
        val pkgName: String
    ) :
        FragmentPagerAdapter(fm) {
        override fun getCount(): Int {
            return if (recentChanges) {
                3
            } else {
                4
            }
        }

        override fun getPageTitle(position: Int): CharSequence? {
            return when (position) {
                0 -> ctx.getString(R.string.l_info)
                1 -> ctx.getString(R.string.l_credits)
                2 -> ctx.getString(R.string.l_license)
                3 -> ctx.getString(R.string.l_recent_changes)
                else -> position.toString()
            }
        }

        override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> InfoFragment(pkgName)
                1 -> CreditsFragment(pkgName)
                2 -> LicenseFragment(pkgName)
                3 -> RecentChangesFragment(pkgName)
                else -> Fragment()
            }
        }
    }

    class InfoFragment(pkgName:String): AboutFragment(pkgName) {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.oi_distribution_about_info, container, false)
        }

        override fun onAttach(context: Context) {
            super.onAttach(context)
            displayLogo(pkgName)
            displayProgramNameAndVersion(pkgName)
            displayComments(pkgName)
            displayCopyright(pkgName)
            displayWebsiteLink(pkgName)
            displayEmail(pkgName)
            checkCreditsAvailable()
        }
    }

    class CreditsFragment(pkgName:String): AboutFragment(pkgName) {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.oi_distribution_about_credits, container, false)
        }
        override fun onAttach(context: Context) {
            super.onAttach(context)
            displayAuthors(pkgName)
            displayDocumenters(pkgName)
            displayTranslators(pkgName)
            displayArtists(pkgName)
            displayInternationalTranslators(pkgName)
        }
    }

    class LicenseFragment(pkgName:String): AboutFragment(pkgName) {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.oi_distribution_about_license, container, false)
        }
        override fun onAttach(context: Context) {
            super.onAttach(context)
          displayLicense(pkgName)
        }
    }

    class RecentChangesFragment(pkgName:String): AboutFragment(pkgName) {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.oi_distribution_about_recent_changes, container, false)
        }
        override fun onAttach(context: Context) {
            super.onAttach(context)
            displayRecentChanges(pkgName)
        }
    }

    open class AboutFragment(val pkgName: String) : Fragment() {
        private lateinit var metaDataReader: MetaDataReader
        private lateinit var packageManager: PackageManager
        /**
         * The views.
         */
        protected lateinit var mLogoImage: ImageSwitcher
        protected lateinit var mProgramNameAndVersionText: TextSwitcher
        protected lateinit var mCommentsText: TextSwitcher
        protected lateinit var mCopyrightText: TextSwitcher
        protected lateinit var mWebsiteText: TextSwitcher
        protected lateinit var mEmailText: TextSwitcher
        protected lateinit var mAuthorsLabel: TextView
        protected lateinit var mAuthorsText: TextView
        protected lateinit var mDocumentersLabel: TextView
        protected lateinit var mDocumentersText: TextView
        protected lateinit var mTranslatorsLabel: TextView
        protected lateinit var mTranslatorsText: TextView
        protected lateinit var mArtistsLabel: TextView
        protected lateinit var mArtistsText: TextView
        protected lateinit var mInternationalTranslatorsLabel: TextView
        protected lateinit var mInternationalTranslatorsText: TextView
        protected lateinit var mNoInformationText: TextView
        protected lateinit var mLicenseText: TextView
        protected lateinit var mRecentChangesText: TextView


        override fun onAttach(context: Context) {
            super.onAttach(context)
            packageManager = context!!.packageManager
            try {
                metaDataReader = MetaDataReader(
                    context.applicationContext,
                    pkgName,
                    metaDataNameToTagName
                )
            } catch (e: NameNotFoundException) {
                throw IllegalArgumentException("Package name '$pkgName' doesn't exist.")
            }
        }

        /**
         * Change the logo image using the resource in the string argument.
         *
         * @param logoString String of a content uri to an image resource
         */
        protected fun changeLogoImageUri(logoString: String) {
            val imageDescriptionUri = Uri.parse(logoString)
            if (imageDescriptionUri != null) {
                mLogoImage.setImageURI(imageDescriptionUri)
            } else { // Not a uri, so invalid.
                throw IllegalArgumentException("Not a valid image.")
            }
        }

        /**
         * Change the logo image using the resource name and package.
         *
         * @param resourceFileName    String of the name of the image resource (as you would append
         * it after "R.drawable.").
         * @param resourcepackageName String of the name of the source package of the image resource
         * (the package name of the calling app).
         */
        protected fun changeLogoImageResource(
            resourceFileName: String,
            resourcepackageName: String
        ) {
            try {
                val resources = packageManager
                    .getResourcesForApplication(resourcepackageName)
                val id = resources
                    .getIdentifier(resourceFileName, null, null)
                i_logo.setImageDrawable(resources.getDrawable(id))
            } catch (e: NumberFormatException) { // Not a resource id
                throw IllegalArgumentException("Not a valid image.")
            } catch (e: NotFoundException) { // Resource not found
                throw IllegalArgumentException("Not a valid image.")
            } catch (e: NameNotFoundException) { //Not a package name
                throw IllegalArgumentException(
                    "Not a valid (image resource) package name."
                )
            }

            /*The idea for this came from:
                android.content.Intent.ShortcutIconResource and related contstants and intents, in android.content.Intent: http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob;f=core/java/android/content/Intent.java;h=39888c1bc0f62effa788815e5b9376969d255766;hb=master
                what's done with this in com.android.launcher.Launcher: http://android.git.kernel.org/?p=platform/packages/apps/Launcher.git;a=blob;f=src/com/android/launcher/Launcher.java;h=928f4caecde593d0fb430718de28d5e52df989ad;hb=HEAD
                    and in android.webkit.gears.DesktopAndroid: http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob;f=core/java/android/webkit/gears/DesktopAndroid.java
            */
        }

        /**
         * Fetch and display artists information.
         *
         * @param intent The intent from which to fetch the information.
         */
        protected fun displayArtists(pkgName: String) {
            val textarray = AboutUtils.getStringArrayExtraOrMetadata(
                metaDataReader,
                activity!!,
                pkgName,

                AboutMetaData.METADATA_ARTISTS
            )

            val text = AboutUtils.getTextFromArray(textarray)

            if (!TextUtils.isEmpty(text)) {
                et_artists.text = text
                l_artists.visibility = View.VISIBLE
                et_artists.visibility = View.VISIBLE
            } else {
                l_artists.visibility = View.GONE
                et_artists.visibility = View.GONE
            }
        }

        /**
         * Fetch and display authors information.
         *
         * @param intent The intent from which to fetch the information.
         */
        protected fun displayAuthors(pkgName: String) {
            val textarray = AboutUtils.getStringArrayExtraOrMetadata(
                metaDataReader,
                activity!!,
                pkgName,
                AboutMetaData.METADATA_AUTHORS
            )

            val text = AboutUtils.getTextFromArray(textarray)

            if (!TextUtils.isEmpty(text)) {
                et_authors.text = text
                l_authors.visibility = View.VISIBLE
                et_authors.visibility = View.VISIBLE
            } else {
                l_authors.visibility = View.GONE
                et_authors.visibility = View.GONE
            }
        }

        /**
         * Fetch and display comments information.
         *
         */
        protected fun displayComments(pkgName: String) {
            val text = AboutUtils.getStringExtraOrMetadata(
                metaDataReader, this.activity!!, pkgName, AboutMetaData.METADATA_COMMENTS
            )

            if (!TextUtils.isEmpty(text)) {
                t_comments.setText(text)
                t_comments.visibility = View.VISIBLE
            } else {
                t_comments.visibility = View.GONE
            }
        }

        /**
         * Fetch and display copyright information.
         *
         * @param intent The intent from which to fetch the information.
         */
        protected fun displayCopyright(pkgName: String) {
            val text = AboutUtils.getStringExtraOrMetadata(
                metaDataReader, this.activity!!, pkgName,  AboutMetaData.METADATA_COPYRIGHT
            )

            if (!TextUtils.isEmpty(text)) {
                t_copyright.setText(text)
                t_copyright.visibility = View.VISIBLE
            } else {
                t_copyright.visibility = View.GONE
            }
        }

        /**
         * Fetch and display documenters information.
         *
         * @param intent The intent from which to fetch the information.
         */
        protected fun displayDocumenters(pkgName: String) {
            val textarray = AboutUtils.getStringArrayExtraOrMetadata(
                metaDataReader, this.activity!!, pkgName,  AboutMetaData.METADATA_DOCUMENTERS
            )
            val text = AboutUtils.getTextFromArray(textarray)

            if (!TextUtils.isEmpty(text)) {
                et_documenters.text = text
                l_documenters.visibility = View.VISIBLE
                et_documenters.visibility = View.VISIBLE
            } else {
                l_documenters.visibility = View.GONE
                et_documenters.visibility = View.GONE
            }
        }


        /**
         * Fetch and display license information.
         *
         * @param intent The intent from which to fetch the information.
         */
        protected fun displayLicense(pkgName: String) {

            val resourceid = AboutUtils.getResourceIdExtraOrMetadata(
                metaDataReader, AboutMetaData.METADATA_LICENSE
            )

            if (resourceid == 0) {
                et_license.setText(R.string.no_information_available)
                return
            }

            val license = getRawResource(pkgName, resourceid, false)

            et_license.text = license
        }

        /**
         * Fetch and display recent changes information.
         *
         * @param intent The intent from which to fetch the information.
         */
        protected fun displayRecentChanges(pkgName: String) {

            val resourceid = AboutUtils.getResourceIdExtraOrMetadata(
                metaDataReader,  AboutMetaData.METADATA_RECENT_CHANGES
            )

            if (resourceid == 0) {
                // Tab is hidden if there are no recent changes.
                //mRecentChangesText.setText(R.string.no_information_available);
                return
            }

            val recentchanges = getRawResource(pkgName, resourceid, true)

            et_recent_changes.text = recentchanges
        }

        private fun getRawResource(pkgName: String, resourceid: Int, preserveLineBreaks: Boolean): String {
            // Retrieve text from resource:
            var text = ""
            try {
                val resources = packageManager
                    .getResourcesForApplication(pkgName)

                //Read in the license file as a big String
                val `in` = BufferedReader(
                    InputStreamReader(
                        resources.openRawResource(resourceid)
                    )
                )
                var line: String?
                val sb = StringBuilder()
                try {
                    while (`in`.run {
                            line = readLine()
                            line
                        } != null) { // Read line per line.
                        if (TextUtils.isEmpty(line)) {
                            // Empty line: Leave line break
                            if (preserveLineBreaks) {
                                sb.append("\n")
                            } else {
                                sb.append("\n\n")
                            }
                        } else {
                            sb.append(line)
                            if (preserveLineBreaks) {
                                sb.append("\n")
                            } else {
                                sb.append(" ")
                            }
                        }
                    }
                    text = sb.toString()
                } catch (e: IOException) {
                    //Should not happen.
                    e.printStackTrace()
                }

            } catch (e: NameNotFoundException) {
                Log.e(TAG, "Package name not found", e)
            }

            return text
        }

        /**
         * Fetch and display logo information.
         *
         */
        protected fun displayLogo(pkgName: String) {
                try {
                    val pi = packageManager.getPackageInfo(
                        pkgName, 0
                    )
                    val resources = packageManager
                        .getResourcesForApplication(pkgName)
                    val resourcename = resources.getResourceName(pi.applicationInfo.icon)
                    changeLogoImageResource(resourcename, pkgName)
                } catch (e: NameNotFoundException) {
                    Log.e(TAG, "Package name not found", e)
                    setErrorLogo()
                } catch (e: NotFoundException) {
                    Log.e(TAG, "Package name not found", e)
                    setErrorLogo()
                } catch (e: IllegalArgumentException) {
                    setErrorLogo()
                }
        }

        private fun setErrorLogo() {
            i_logo.setImageResource(android.R.drawable.ic_menu_info_details)
        }

        /**
         * Fetch and display program name and version information.
         *
         */
        protected fun displayProgramNameAndVersion(pkgName: String) {
            val applicationlabel = getApplicationLabel(pkgName)
            val versionname = getVersionName(pkgName)

            var combined = applicationlabel
            if (!TextUtils.isEmpty(versionname)) {
                combined += " " + versionname!!
            }

            t_program_name_and_version.setText(combined)

            val title = getString(R.string.about_activity_name_extended, applicationlabel)
            // TODO setTitle(title)
        }

        /**
         * Get application label.
         *
         */
        protected fun getApplicationLabel(pkgName: String): String? {
            var applicationlabel: String? = null
                try {
                    val pi = packageManager.getPackageInfo(
                        pkgName, 0
                    )
                    val labelid = pi.applicationInfo.labelRes
                    val resources = packageManager
                        .getResourcesForApplication(pkgName)
                    applicationlabel = resources.getString(labelid)
                } catch (e: NameNotFoundException) {
                    Log.e(TAG, "Package name not found", e)
                }

            return applicationlabel
        }

        /**
         * Get version information.
         *
         * @param intent The intent from which to fetch the information.
         */
        protected fun getVersionName(pkgName: String): String? {
            var versionname: String? = null
                try {
                    val pi = packageManager.getPackageInfo(
                        pkgName, 0
                    )
                    versionname = pi.versionName
                } catch (e: NameNotFoundException) {
                    Log.e(TAG, "Package name not found", e)
                }
            return versionname
        }

        /**
         * Fetch and display translators information.
         *
         */
        protected fun displayTranslators(
            pkgName: String
        ) {

            val textarray = AboutUtils.getStringArrayExtraOrMetadata(
                metaDataReader, this.activity!!,
                pkgName,
                AboutMetaData.METADATA_TRANSLATORS
            )
            var text = AboutUtils.getTextFromArray(textarray)

            if (!TextUtils.isEmpty(text)) {
                et_translators.text = text
                l_translators.visibility = View.VISIBLE
                et_translators.visibility = View.VISIBLE
            } else {
                text = AboutUtils.getStringExtraOrMetadata(
                    metaDataReader, this.activity!!,
                    pkgName,
                    AboutMetaData.METADATA_TRANSLATORS
                )

                // Create string array of translators from translated string
                // from Launchpad or (for English) from the array.
                if (text != LAUNCHPAD_TRANSLATOR_CREDITS_TAG && !TextUtils.isEmpty(text)) {
                    //	textarray = text.replaceFirst(
                    //			LAUNCHPAD_TRANSLATOR_CREDITS_HEADER, "").split(LAUNCHPAD_TRANSLATOR_CREDITS_REGEX);
                    //	text = AboutUtils.getTextFromArray(textarray);
                    //	mTranslatorsText.setText(text);
                    text = text.replaceFirst(LAUNCHPAD_TRANSLATOR_CREDITS_HEADER.toRegex(), "")
                        .replace(LAUNCHPAD_TRANSLATOR_CREDITS_REGEX_1.toRegex(), LAUNCHPAD_TRANSLATOR_CREDITS_REGEX_2)

                    // take away final "<br/>"
                    if (text.length > 5) {
                        text = text.substring(0, text.length - 5)
                    }

                    val styledText = Html.fromHtml(text)

                    et_translators.text = styledText
                    et_translators.linksClickable = true
                    l_translators.visibility = View.VISIBLE
                    et_translators.visibility = View.VISIBLE
                } else {
                    l_translators.visibility = View.GONE
                    et_translators.visibility = View.GONE
                }
            }

        }

        /**
         * Fetch and display international translators information.
         * This is only possible through the string resource directly - not through intent.
         */
        protected fun displayInternationalTranslators(pkgName: String) {

            val id = AboutUtils.getResourceIdMetadata(metaDataReader!!, AboutMetaData.METADATA_TRANSLATORS)

            var text: String? = null

            if (id != 0) {
                // local resources
                val res = resources
                val languages = res.getStringArray(R.array.languages)
                val languagenames = res.getStringArray(R.array.language_names)

                if (languages.size != languagenames.size) {
                    // Language array lengths must agree!
                    throw RuntimeException()
                }
                try {
                    // remote resources:
                    val resources = packageManager
                        .getResourcesForApplication(pkgName)

                    val sb = StringBuilder()

                    val translatorHash = HashMap<String, String>()

                    for (i in languages.indices) {
                        val lang = languages[i].substring(0, 2)
                        var country = ""
                        if (languages[i].length > 3) {
                            country = languages[i].substring(3)
                        }

                        val locale = Locale(lang, country)
                        val config = Configuration()
                        config.locale = locale
                        resources.updateConfiguration(config, null)
                        text = resources.getString(id)

                        // Make sure that text is unique within a language.
                        // e.g. If pt and pt_BR give same translators,
                        //      only pt should be shown.
                        //      If they differ, both should be shown.
                        //      pt: Portugese, pt_BR: Portugese (Brazilian)
                        var showCountry = true
                        if (translatorHash.containsKey(text) && translatorHash[text] == lang) {
                            showCountry = false
                        }
                        if (TextUtils.isEmpty(country)) {
                            // Only add base language translations to hash
                            translatorHash[text] = lang
                        }

                        if (showCountry
                            && text != LAUNCHPAD_TRANSLATOR_CREDITS_TAG
                            && !TextUtils.isEmpty(text)
                        ) {
                            text = text!!.replaceFirst(LAUNCHPAD_TRANSLATOR_CREDITS_HEADER.toRegex(), "").replace(
                                LAUNCHPAD_TRANSLATOR_CREDITS_REGEX_1.toRegex(),
                                LAUNCHPAD_TRANSLATOR_CREDITS_REGEX_3
                            )
                            sb.append("<font color=\"#c0c0c0\"><small>")
                            sb.append(languagenames[i])
                            sb.append("</small></font><br/>")
                            sb.append(text)
                            sb.append("<br/>")
                        }
                    }

                    // take away final "<br/><br/>"
                    if (sb.length > 10) {
                        text = sb.substring(0, sb.length - 10)
                    } else {
                        text = sb.toString()
                    }

                } catch (e: NameNotFoundException) {
                    Log.e(TAG, "Package name not found ", e)
                } catch (e: NumberFormatException) {
                    Log.e(TAG, "Metadata not valid id.", e)
                } catch (e: NotFoundException) {
                    Log.e(TAG, "Resource not found.", e)
                }

            }

            if (!TextUtils.isEmpty(text)) {
                val styledText = Html.fromHtml(text)

                et_international_translators.text = styledText
                mInternationalTranslatorsText.linksClickable = true
                mInternationalTranslatorsLabel.visibility = View.VISIBLE
                mInternationalTranslatorsText.visibility = View.VISIBLE
            } else {
                mInternationalTranslatorsLabel.visibility = View.GONE
                mInternationalTranslatorsText.visibility = View.GONE
            }

        }

        /**
         * Fetch and display website link information.
         *
         */
        protected fun displayWebsiteLink(pkgName: String) {
            val websitelabel = AboutUtils.getStringExtraOrMetadata(
                metaDataReader, this.activity!!, pkgName,
                 AboutMetaData.METADATA_WEBSITE_LABEL
            )
            val websiteurl = AboutUtils.getStringExtraOrMetadata(
                metaDataReader, this.activity!!, pkgName,
                 AboutMetaData.METADATA_WEBSITE_URL
            )

            setAndLinkifyWebsiteLink(websitelabel, websiteurl)
        }

        /**
         * Set the website link TextView and linkify.
         *
         * @param websiteLabel The label to set.
         * @param websiteUrl   The URL that the label links to.
         */
        protected fun setAndLinkifyWebsiteLink(websiteLabel: String, websiteUrl: String) {
            if (!TextUtils.isEmpty(websiteUrl)) {
                if (TextUtils.isEmpty(websiteLabel)) {
                    mWebsiteText.setText(websiteUrl)
                } else {
                    mWebsiteText.setText(websiteLabel)
                }
                mWebsiteText.visibility = View.VISIBLE

                //Create TransformFilter
                val tf = TransformFilter { matcher, url -> websiteUrl }

                //Allow a label and url through Linkify
                Linkify.addLinks(
                    mWebsiteText.getChildAt(0) as TextView, Pattern
                        .compile(".*"), "", null, tf
                )
                Linkify.addLinks(
                    mWebsiteText.getChildAt(1) as TextView, Pattern
                        .compile(".*"), "", null, tf
                )
            } else {
                mWebsiteText.visibility = View.GONE
            }
        }

        /**
         * Fetch and display website link information.
         *
         */
        protected fun displayEmail(pkgName: String) {
            val email = AboutUtils.getStringExtraOrMetadata(
                metaDataReader, this.activity!!, pkgName,
             AboutMetaData.METADATA_EMAIL
            )

            if (!TextUtils.isEmpty(email)) {
                mEmailText.setText(email)
                mEmailText.visibility = View.VISIBLE
            } else {
                mEmailText.visibility = View.GONE
            }
        }

        /**
         * Check whether any credits are available.
         * If not, display "no information available".
         */
        internal fun checkCreditsAvailable() {
            if (mAuthorsLabel.visibility == View.GONE
                && mAuthorsLabel.visibility == View.GONE
                && mAuthorsLabel.visibility == View.GONE
                && mAuthorsLabel.visibility == View.GONE
            ) {
                mNoInformationText.visibility = View.VISIBLE
            } else {
                mNoInformationText.visibility = View.GONE
            }

        }
    }

}