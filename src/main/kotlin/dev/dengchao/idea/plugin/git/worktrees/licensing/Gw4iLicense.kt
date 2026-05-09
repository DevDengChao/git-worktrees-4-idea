package dev.dengchao.idea.plugin.git.worktrees.licensing

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.ui.LicensingFacade

private const val PRODUCT_CODE = "PGWFI"

/**
 * Central licensing gateway for GW4I paid features.
 *
 * Wraps [LicensingFacade] to determine whether the current IDE session holds a
 * valid GW4I Pro license.  The facade is not available during early IDE startup
 * (before the licensing subsystem initialises), so we treat an uninitialised
 * facade as [LicenseStatus.UNKNOWN] and let the action continue — this avoids
 * false-blocking actions during splash-screen startup.
 */
object Gw4iLicense {
    private val LOG = Logger.getInstance(Gw4iLicense::class.java)

    enum class LicenseStatus {
        /** A valid confirmation stamp exists — user is licensed. */
        ALLOWED,

        /** No confirmation stamp found — user is not licensed. */
        BLOCKED,

        /**
         * [LicensingFacade] is not yet initialised (early startup).
         * Treat as allowed to avoid false negatives during IDE boot.
         */
        UNKNOWN,
    }

    private var checkOverride: (() -> LicenseStatus)? = null

    /** Returns [LicenseStatus] for the GW4I paid product. */
    fun check(): LicenseStatus {
        checkOverride?.let { return it() }

        val facade = try {
            LicensingFacade.getInstance()
        } catch (e: RuntimeException) {
            // IDE application may not be fully initialised yet (early startup).
            LOG.debug("LicensingFacade not yet available", e)
            return LicenseStatus.UNKNOWN
        }

        if (facade == null) {
            // Facade not initialised — IDE is still starting up.
            return LicenseStatus.UNKNOWN
        }

        val stamp = facade.getConfirmationStamp(PRODUCT_CODE)
        return if (stamp != null) LicenseStatus.ALLOWED else LicenseStatus.BLOCKED
    }

    /** Returns `true` when the user is allowed to use paid features. */
    fun isAllowed(): Boolean = check() != LicenseStatus.BLOCKED

    /** Override license check result in tests. */
    internal fun overrideForTests(status: LicenseStatus, parentDisposable: Disposable) {
        checkOverride = { status }
        Disposer.register(parentDisposable) { checkOverride = null }
    }
}
