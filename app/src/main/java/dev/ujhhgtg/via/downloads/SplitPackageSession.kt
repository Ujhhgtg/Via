package dev.ujhhgtg.via.downloads

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class PackageInstallOutcome { CONFIRMATION, SUCCESS, ABORTED, FAILURE }

/** Some installers return the legacy code in the message with a generic failure status. */
internal fun packageInstallOutcome(status: Int, message: String?): PackageInstallOutcome = when {
    status == PackageInstaller.STATUS_PENDING_USER_ACTION -> PackageInstallOutcome.CONFIRMATION
    status == PackageInstaller.STATUS_SUCCESS -> PackageInstallOutcome.SUCCESS
    status == PackageInstaller.STATUS_FAILURE_ABORTED || message?.contains("INSTALL_FAILED_ABORTED") == true -> PackageInstallOutcome.ABORTED
    else -> PackageInstallOutcome.FAILURE
}

/** Same public, unprivileged Session flow used by InstallerX's NoneAppInstallerRepoImpl. */
internal object SplitPackageSession {
    suspend fun stage(context: Context, parts: List<ApkPart>): Int {
        require(ApkSplitSelection.valid(parts, Build.VERSION.SDK_INT))
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(parts.first().manifest.packageName)
            setSize(parts.sumOf { it.file.length() })
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                val buffer = ByteArray(64 * 1024)
                parts.forEachIndexed { index, part ->
                    val name = if (part.isBase) "base.apk" else "split-$index.apk"
                    session.openWrite(name, 0, part.file.length()).use { output ->
                        part.file.inputStream().use { input ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                        session.fsync(output)
                    }
                }
            }
            return id
        } catch (error: Throwable) { installer.abandonSession(id); throw error }
    }
}
