package dev.ujhhgtg.via.downloads

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Test

class PackageInstallOutcomeTest {
    @Test fun userCancellationIsNotAnInstallationError() {
        assertEquals(PackageInstallOutcome.ABORTED, packageInstallOutcome(PackageInstaller.STATUS_FAILURE_ABORTED, "User rejected permissions"))
        assertEquals(PackageInstallOutcome.ABORTED, packageInstallOutcome(PackageInstaller.STATUS_FAILURE, "INSTALL_FAILED_ABORTED: User rejected permissions"))
    }

    @Test fun systemConfirmationAndSuccessAreNotFailures() {
        assertEquals(PackageInstallOutcome.CONFIRMATION, packageInstallOutcome(PackageInstaller.STATUS_PENDING_USER_ACTION, null))
        assertEquals(PackageInstallOutcome.SUCCESS, packageInstallOutcome(PackageInstaller.STATUS_SUCCESS, null))
        assertEquals(PackageInstallOutcome.FAILURE, packageInstallOutcome(PackageInstaller.STATUS_FAILURE_INVALID, "INSTALL_PARSE_FAILED_NO_CERTIFICATES"))
    }
}
