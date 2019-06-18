package org.slf4j

class LoggerFactory {

    companion object {
        @JvmField
        var INSTANCE: LoggerFactory = LoggerFactory()

        @JvmStatic
        fun getLogger(clazz: Class<*>): Logger {
            return Logger(clazz)
        }
    }

}
