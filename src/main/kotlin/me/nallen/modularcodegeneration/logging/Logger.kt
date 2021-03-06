package me.nallen.modularcodegeneration.logging

import org.slf4j.LoggerFactory

class Logger {
    companion object {
        private val logger = LoggerFactory.getLogger("modularcodegeneration")

        fun debug(s: String) {
            logger.debug(s)
        }

        fun info(s: String) {
            logger.info(s)
        }

        fun warn(s: String) {
            logger.warn(s)
        }

        fun error(s: String) {
            logger.error(s)
        }
    }
}