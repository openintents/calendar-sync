package org.openintents.distribution.about

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Resources
import android.text.TextUtils
import android.util.Log

object AboutUtils {
    private val TAG = "AboutUtils"

    fun getTextFromArray(array: Array<String>?): String {
        if (array == null) {
            return ""
        }
        var text = ""
        for (person in array) {
            text += person + "\n"
        }
        if (text.length > 0) {
            // delete last "\n"
            text = text.substring(0, text.length - 1)
        }
        return text
    }

    /**
     * Get String array from Extra or from Meta-data through resources.
     *
     * @param packagename
     * @param intent
     * @param extra
     * @param metadata
     */
    fun getStringArrayExtraOrMetadata(
        metaDataReader: MetaDataReader?,
        context: Context, packagename: String,
        metadata: String
    ): Array<String>? {
        //Try meta data of package
        val md = metaDataReader?.bundle

        if (md != null) {
            var array: Array<String>? = null
            try {
                val id = md.getInt(metadata)
                if (id != 0) {
                    val resources = context.packageManager
                        .getResourcesForApplication(packagename)
                    array = resources.getStringArray(id)
                }
            } catch (e: NameNotFoundException) {
                Log.e(TAG, "Package name not found ", e)
            } catch (e: NumberFormatException) {
                Log.e(TAG, "Metadata not valid id.", e)
            } catch (e: Resources.NotFoundException) {
                Log.e(TAG, "Resource not found.", e)
            }

            return array
        } else {
            return null
        }
    }

    /**
     * Get string from extra or from metadata.
     *
     * @param context
     * @param packagename
     * @param intent
     * @param extra
     * @param metadata
     * @return
     */
    fun getStringExtraOrMetadata(
        metaDataReader: MetaDataReader?, context: Context,
        packagename: String, metadata: String
    ): String {
        //Try meta data of package
        val md = metaDataReader?.bundle

        if (md != null) {
            val value = md.get(metadata)
            if (value is String && !TextUtils.isEmpty(value)) {
                return value
            } else {
                //Still try metadata but don't expect a ready string (get it from the resources).
                try {
                    val id = md.getInt(metadata)
                    if (id != 0) {
                        val resources = context.packageManager
                            .getResourcesForApplication(packagename)
                        val text = resources.getString(id)
                        if (!TextUtils.isEmpty(text)) {
                            return text
                        }
                    }
                } catch (e: NameNotFoundException) {
                    Log.e(TAG, "Package name not found ", e)
                } catch (e: NumberFormatException) {
                    Log.e(TAG, "Metadata not valid id.", e)
                } catch (e: Resources.NotFoundException) {
                    Log.e(TAG, "Resource not found.", e)
                }

            }
        }
        return ""
    }


    /**
     * Get string from metadata in different localization.
     *
     * @param metaDataReader
     * @param metadata
     * @return
     */
    fun getResourceIdMetadata(metaDataReader: MetaDataReader?, metadata: String): Int {
        //Try meta data of package
        val md = metaDataReader?.bundle

        if (md != null) {
            //Still try metadata but don't expect a ready string (get it from the resources).
            try {
                return md.getInt(metadata)
            } catch (e: NumberFormatException) {
                Log.e(TAG, "Metadata not valid id. Using 0 instead", e)
            } catch (e: Resources.NotFoundException) {
                Log.e(TAG, "Resource not found. Using 0 instead", e)
            }

        }
        return 0
    }

    /**
     * Get resource ID from extra or from metadata.
     *
     * @param context
     * @param packagename
     * @param intent
     * @param extra
     * @param metadata
     * @return
     */
    fun getResourceIdExtraOrMetadata(
        metaDataReader: MetaDataReader?, metadata: String
    ): Int {
        return getResourceIdMetadata(metaDataReader, metadata)


    }
}
