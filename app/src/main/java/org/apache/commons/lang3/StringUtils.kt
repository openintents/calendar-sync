package org.apache.commons.lang3

class StringUtils {
    fun isNotBlank(string: String?): Boolean {
        return string != null && string.isNotEmpty()
    }

    companion object {
        @JvmField
        var INSTANCE: StringUtils = StringUtils()

        @JvmStatic
        fun join(s1: Array<String>, sep: String): String {
            return s1.joinToString(sep)
        }

        @JvmStatic
        fun startsWithIgnoreCase(s: String, prefix: String): Boolean {
            return s.toLowerCase().startsWith(prefix.toLowerCase())
        }

        @JvmStatic
        fun startsWith(s: String, prefix: String): Boolean {
            return s.startsWith(prefix)
        }

        @JvmStatic
        fun endsWith(s: String, postfix: String): Boolean {
            return s.endsWith(postfix)
        }
    }
}
