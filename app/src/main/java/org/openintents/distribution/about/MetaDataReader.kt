package org.openintents.distribution.about

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Resources
import android.content.res.Resources.NotFoundException
import android.content.res.XmlResourceParser
import android.os.Bundle
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

import java.io.IOException

/* To make the app work with both meta-data tags and about.xml files */
class MetaDataReader @Throws(NameNotFoundException::class)
constructor(
    private val ctx: Context,
    private val packagename: String,
    private val tagNameToMetadataName: Map<String, String>
) {
    internal var bundle: Bundle? = null

    private val packageManager: PackageManager
        get() = ctx.packageManager

    private//It will never get here, the packagename validity is checked in the constructor
    val manifestMetaData: Bundle?
        get() {
            try {
                return ctx.packageManager.getApplicationInfo(
                    packagename,
                    PackageManager.GET_META_DATA
                ).metaData
            } catch (e: NameNotFoundException) {
                return null
            }

        }

    init {
        //Check if the packagename exists
        ctx.packageManager.getApplicationInfo(packagename, PackageManager.GET_META_DATA)
    }

    fun getBundle(): Bundle? {
        if (bundle == null)
            bundle = createBundle()
        return bundle
    }

    private fun createBundle(): Bundle? {
        val manifestMD = manifestMetaData ?: return null

        if (manifestMD.containsKey(AboutMetaData.METADATA_ABOUT)) {
            val id = manifestMD.getInt(AboutMetaData.METADATA_ABOUT, -1)
            if (id == -1)
                return null
            val xml: XmlResourceParser
            try {
                xml = ctx.packageManager.getResourcesForApplication(packagename).getXml(id)
            } catch (e: NotFoundException) {
                Log.d("error", "About.xml file not found.")
                return null
            } catch (e: NameNotFoundException) {
                // Should never get here
                return null
            }

            return createAboutFileBundle(xml)
        } else {
            /*Bundle ret = new Bundle(manifestMD.size());
			Set<String> keySet = manifestMD.keySet();
			for(String key : keySet){
				String newKey = key;
				if(metaDataNameToTagName.containsKey(key)){
					newKey = metaDataNameToTagName.get(key);
				}
				Parcelable value = manifestMD.getParcelable(key);
				ret.putParcelable(newKey, value);
			}
			return ret;*/
            return manifestMD
        }
    }

    private fun createAboutFileBundle(xml: XmlResourceParser): Bundle {
        val bundle = Bundle()
        val pm = packageManager
        var resources: Resources? = null
        try {
            resources = pm.getResourcesForApplication(packagename)
        } catch (e: NameNotFoundException) {
            // It should never get here, the packagename existence is checked in constructor.
        }

        var inAbout = false

        try {
            var tagType = xml.next()
            while (XmlPullParser.END_DOCUMENT != tagType) {
                val name = xml.name
                if (XmlPullParser.START_TAG == tagType) {
                    if (inAbout) {
                        if (tagNameToMetadataName.containsKey(name)) {
                            val attr = Xml.asAttributeSet(xml)
                            val resIdName = attr.getAttributeValue(SCHEMA, ATTR_RESOURCE)
                            if (resIdName != null) {
                                var resId = 0
                                if (resIdName.startsWith("@")) {
                                    // oi:resource="@type/name"
                                    resId =
                                        resources!!.getIdentifier(resIdName.substring(1), null, packagename)// Cut the @
                                } else {
                                    // oi:resource="123456"
                                    try {
                                        resId = Integer.parseInt(resIdName)
                                    } catch (ignored: NumberFormatException) {
                                    }

                                }
                                bundle.putInt(tagNameToMetadataName[name], resId)
                            } else {
                                val value = attr.getAttributeValue(SCHEMA, ATTR_VALUE)
                                if (value.startsWith("@")) {
                                    // oi:value="@type/name"
                                    val valId = resources!!.getIdentifier(value.substring(1), null, packagename)
                                    if (value.startsWith("@string/")) {
                                        // oi:value="@string/name"
                                        val valString = resources.getString(valId)
                                        bundle.putString(tagNameToMetadataName[name], valString)
                                    } else {
                                        //Cannot process the type, treat it as a resource
                                        // oi:value="@integer/name" or other resource types
                                        Log.w(
                                            TAG,
                                            String.format("attribute %s must be a string or string resource", name)
                                        )
                                        bundle.putInt(tagNameToMetadataName[name], valId)
                                    }
                                } else {
                                    // oi:value="a value"
                                    bundle.putString(tagNameToMetadataName[name], value)
                                }
                            }
                        }
                    }
                    if (ELEM_ABOUT == name) {
                        inAbout = true
                    }
                } else if (XmlPullParser.END_TAG == tagType) {
                    if (ELEM_ABOUT == name) {
                        inAbout = false
                        //Allow only one <about> tag
                        break
                    }
                }
                tagType = xml.next()
            }
        } catch (ex: XmlPullParserException) {
            Log.d("About.xml", "XmlPullParserException")
        } catch (ex: IOException) {
            Log.d("About.xml", "IOException")
        }

        xml.close()

        return bundle
    }

    companion object {
        val ELEM_ABOUT = "about"
        val SCHEMA = "http://schemas.openintents.org/android/about"
        val ATTR_VALUE = "value"
        val ATTR_RESOURCE = "resource"
        private val TAG = org.openintents.distribution.about.MetaDataReader::class.java.simpleName
    }
}
