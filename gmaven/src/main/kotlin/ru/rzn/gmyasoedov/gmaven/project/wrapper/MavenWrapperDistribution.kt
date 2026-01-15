package ru.rzn.gmyasoedov.gmaven.project.wrapper

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.Nls
import ru.rzn.gmyasoedov.gmaven.GMavenConstants
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle.message
import ru.rzn.gmyasoedov.gmaven.util.IndicatorUtil
import ru.rzn.gmyasoedov.gmaven.utils.MavenLog
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.io.path.name


class MavenWrapperDistribution {

    companion object {

        @JvmStatic
        @Throws(IOException::class)
        fun getOrDownload(urlString: String): WrapperDistribution {
            val current = getCurrentDistribution(urlString)
            if (current != null) return current

            val taskInfo = IndicatorUtil.getTaskInfo(message("gmaven.wrapper.downloading"))
            val indicator = BackgroundableProcessIndicator(taskInfo)
            try {
                return downloadMavenDistribution(urlString, indicator)
            } finally {
                indicator.finish(taskInfo)
            }
        }

        fun getCurrentDistribution(urlString: String): WrapperDistribution? {
            val zipFile = getZipFile(urlString)
            return getCurrentMavenWrapperPath(zipFile.toFile())?.let { WrapperDistribution(it.toPath(), urlString) }
        }

        private fun downloadMavenDistribution(
            urlString: String,
            indicator: BackgroundableProcessIndicator
        ): WrapperDistribution {
            val zipFilePath = getZipFile(urlString)
            informationEvent(message("gmaven.wrapper.notification.downloading.start"))
            if (!zipFilePath.toFile().isFile) {
                val parent = zipFilePath.parent

                val partFile = parent.resolve("${zipFilePath.name}.part-${System.currentTimeMillis()}")
                indicator.apply { text = message("gmaven.wrapper.downloading.from", urlString) }
                try {
                    HttpRequests.request(urlString)
                        .forceHttps(false)
                        .connectTimeout(30_000)
                        .readTimeout(30_000)
                        .saveToFile(partFile, indicator)
                } catch (t: Throwable) {
                    if (t is ControlFlowException)
                        throw RuntimeException(message("gmaven.wrapper.downloading.canceled"))
                }
                FileUtil.rename(partFile.toFile(), zipFilePath.toFile())
            }

            if (!zipFilePath.toFile().isFile) {
                throw RuntimeException(message("gmaven.wrapper.downloading.cannot.download.zip.from", urlString))
            }
            val home = unpackZipFile(zipFilePath.toFile(), indicator).canonicalFile
            informationEvent(message("gmaven.wrapper.notification.downloading.finish"))
            return WrapperDistribution(home.toPath(), urlString)
        }

        private fun getZipFile(distributionUrl: String): Path {
            val baseName: String = getDistName(distributionUrl)
            val distName: String = FileUtil.getNameWithoutExtension(baseName)
            val md5Hash: String = getMd5Hash(distributionUrl)

            return MavenUtils.resolveM2()
                .resolve("wrapper").resolve("dists")
                .resolve(distName)
                .resolve(md5Hash)
                .resolve(baseName)
        }

        private fun getDistName(distUrl: String): String {
            val position = distUrl.lastIndexOf("/")
            return if (position < 0) distUrl else distUrl.substring(position + 1)
        }

        private fun getMd5Hash(string: String): String {
            return try {
                val messageDigest = MessageDigest.getInstance("MD5")
                val bytes = string.toByteArray()
                messageDigest.update(bytes)
                BigInteger(1, messageDigest.digest()).toString(32)
            } catch (var4: Exception) {
                throw RuntimeException("Could not hash input string.", var4)
            }
        }

        private fun unpackZipFile(zipFile: File, indicator: ProgressIndicator?): File {
            unzip(zipFile, indicator)
            val mavenHome = getMavenDir(zipFile)
            if (!SystemInfo.isWindows) {
                makeMavenBinRunnable(mavenHome)
            }
            return mavenHome
        }

        private fun getMavenDir(zipFile: File): File {
            val dirs = zipFile.parentFile.listFiles { it -> it.isDirectory }
            if (dirs == null || dirs.size != 1) {
                MavenLog.LOG.warn("Expected exactly 1 top level dir in Maven distribution, found: " + dirs?.asList())
                throw IllegalStateException(message("gmaven.wrapper.zip.is.not.correct", zipFile.absoluteFile))
            }
            val mavenHome = dirs[0]
            if (!MavenUtils.isValidMavenHome(mavenHome)) {
                throw IllegalStateException(message("gmaven.distribution.error", mavenHome))
            }
            return mavenHome
        }

        private fun getCurrentMavenWrapperPath(zipFile: File): File? {
            val dirs = zipFile.parentFile.listFiles { it -> it.isDirectory } ?: emptyArray()
            if (dirs.size == 1 && MavenUtils.isValidMavenHome(dirs[0])) {
                return dirs[0]
            }
            return null
        }

        private fun makeMavenBinRunnable(mavenHome: File?) {
            val mvnExe = File(mavenHome, "bin/mvn").canonicalFile
            val permissions = PosixFilePermissions.fromString("rwxr-xr-x")
            Files.setPosixFilePermissions(mvnExe.toPath(), permissions)
        }

        private fun unzip(zip: File, indicator: ProgressIndicator?) {
            val unpackDir = zip.parentFile
            val destinationCanonicalPath = unpackDir.canonicalPath
            var errorUnpacking = true
            try {
                ZipFile(zip).use { zipFile ->
                    val entries: Enumeration<*> = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement() as ZipEntry
                        val destFile = File(unpackDir, entry.name)
                        val canonicalPath = destFile.canonicalPath
                        if (!canonicalPath.startsWith(destinationCanonicalPath)) {
                            FileUtil.delete(zip)
                            throw RuntimeException(
                                "Directory traversal attack detected, " +
                                        "zip file is malicious and IDEA dropped it"
                            )
                        }

                        if (entry.isDirectory) {
                            destFile.mkdirs()
                        } else {
                            destFile.parentFile.mkdirs()
                            BufferedOutputStream(FileOutputStream(destFile)).use {
                                StreamUtil.copy(zipFile.getInputStream(entry), it)
                            }
                        }
                    }

                }
                errorUnpacking = false
            } finally {
                if (errorUnpacking) {
                    indicator?.apply { text = message("gmaven.wrapper.failure") }
                    zip.parentFile.listFiles { it -> it.name != zip.name }?.forEach { FileUtil.delete(it) }
                }
            }
        }

        private fun informationEvent(@Nls content: String) {
            val notificationGroup = NotificationGroupManager.getInstance()
                .getNotificationGroup(GMavenConstants.SYSTEM_ID.readableName) ?: return
            ApplicationManager.getApplication().invokeLater {
                notificationGroup.createNotification(
                    message("gmaven.wrapper.notification.title"), content, NotificationType.INFORMATION
                ).notify(null)
            }
        }
    }
}

data class WrapperDistribution(val path: Path, val url: String)