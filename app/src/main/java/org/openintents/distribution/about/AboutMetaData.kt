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
 */

package org.openintents.distribution.about

/**
 * Metadata definition belonging to OI About.
 *
 * @author pjv
 * @author Peli
 * @version 2011-02-07: Add Metadata for recent changes.
 */
object AboutMetaData {

    /**
     * Application metadata: Comments about the program.
     *
     *
     *
     * Usage example:
     * `<meta-data
     * android:name="org.openintents.metadata.COMMENTS"
     * android:value="@string/about_comments" />`
     *
     *
     *
     *
     *
     * This key matches with AboutIntents.EXTRA_COMMENTS.
     *
     *
     *
     *
     *
     * Constant Value: "org.openintents.metadata.COMMENTS"
     *
     */
    val METADATA_COMMENTS = "org.openintents.metadata.COMMENTS"

    /**
     * Application metadata: Copyright information for the program.
     *
     *
     *
     * Usage example:
     * `<meta-data
     * android:name="org.openintents.metadata.COPYRIGHT"
     * android:value="@string/about_comments" />`
     *
     *
     *
     *
     *
     * This key matches with AboutIntents.EXTRA_COPYRIGHT.
     *
     *
     *
     *
     *
     * Constant Value: "org.openintents.metadata.COPYRIGHT"
     *
     */
    val METADATA_COPYRIGHT = "org.openintents.metadata.COPYRIGHT"

    /**
     * Application metadata: The URL for the link to the website of the program.
     *
     *
     *
     *
     * This key matches with AboutIntents.EXTRA_WEBSITE_URL.
     *
     *
     *
     *
     *
     * Constant Value: "org.openintents.metadata.WEBSITE_URL"
     *
     */
    val METADATA_WEBSITE_URL = "org.openintents.metadata.WEBSITE_URL"

    /**
     * Application metadata: The label for the link to the website of the
     * program.
     *
     *
     *
     *
     * This key matches with AboutIntents.EXTRA_WEBSITE_LABEL.
     *
     *
     *
     *
     *
     * Constant Value: "org.openintents.metadata.WEBSITE_LABEL"
     *
     */
    val METADATA_WEBSITE_LABEL = "org.openintents.metadata.WEBSITE_LABEL"

    /**
     * Application metadata: The authors of the program, as an array of strings.
     *
     *
     *
     * Usage example:
     * `<meta-data
     * android:name="org.openintents.metadata.AUTHORS"
     * android:resource="@array/about_authors" />`
     *
     *
     *
     *
     *
     * This key matches with AboutIntents.EXTRA_AUTHORS.
     *
     *
     *
     *
     *
     * Constant Value: "org.openintents.metadata.AUTHORS"
     *
     */
    val METADATA_AUTHORS = "org.openintents.metadata.AUTHORS"

    /**
     * Application metadata: The people documenting the program, as an array of
     * strings.
     *
     *
     *
     *
     * This key matches with AboutIntents.EXTRA_DOCUMENTERS.
     *
     *
     *
     *
     *
     * Constant Value: "org.openintents.metadata.DOCUMENTERS"
     *
     */
    val METADATA_DOCUMENTERS = "org.openintents.metadata.DOCUMENTERS"

    /**
     * Application metadata:: The people who made the translation for the current
     * localization, as an array of strings.
     *
     *
     *
     *
     * This key matches with AboutIntents.EXTRA_TRANSLATORS.
     *
     *
     *
     *
     *
     * Constant Value: "org.openintents.metadata.TRANSLATORS"
     *
     */
    val METADATA_TRANSLATORS = "org.openintents.metadata.TRANSLATORS"

    /**
     * Application metadata: The people who contributed artwork to the program,
     * as an array of strings.
     *
     *
     *
     *
     * This key matches with AboutIntents.EXTRA_ARTISTS.
     *
     *
     *
     *
     *
     * Constant Value: "org.openintents.metadata.ARTISTS"
     *
     */
    val METADATA_ARTISTS = "org.openintents.metadata.ARTISTS"

    /**
     * Application metadata: The raw resource containing the license of the program.
     *
     *
     *
     *
     * This key matches with AboutIntents.EXTRA_LICENSE_RESOURCE.
     *
     *
     *
     *
     *
     * Constant Value: "org.openintents.metadata.LICENSE"
     *
     */
    val METADATA_LICENSE = "org.openintents.metadata.LICENSE"

    /**
     * Application metadata: The primary email address for this application.
     *
     *
     *
     *
     * This key matches with AboutIntents.EXTRA_EMAIL.
     *
     *
     *
     *
     *
     * Constant Value: "org.openintents.metadata.EMAIL"
     *
     */
    val METADATA_EMAIL = "org.openintents.metadata.EMAIL"

    /**
     * Application metadata: The raw resource containing the license of the program.
     *
     *
     *
     *
     * This key matches with AboutIntents.EXTRA_RECENT_CHANGES_RESOURCE.
     *
     *
     *
     *
     *
     * Constant Value: "org.openintents.metadata.RECENT_CHANGES"
     *
     */


    val METADATA_RECENT_CHANGES = "org.openintents.metadata.RECENT_CHANGES"
    /**
     * Application metadata: The xml resource containing the file with other About metadata.
     * When found, all other metadata in manifest are ignored.
     *
     *
     *
     *
     * This key doesn't match with any AboutIntents constant.
     *
     *
     *
     *
     *
     * Constant Value: "org.openintents.about"
     *
     */
    val METADATA_ABOUT = "org.openintents.about"


}
/**
 * Empty, preventing instantiation.
 *///Empty, preventing instantiation.
