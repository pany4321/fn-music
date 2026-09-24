package com.fnmusic.tv

import com.fnmusic.tv.core.data.repository.SessionState
import com.fnmusic.tv.core.model.AppError
import com.fnmusic.tv.core.model.ServerGuid
import com.fnmusic.tv.core.model.ServerIdentity
import com.fnmusic.tv.core.model.User
import com.fnmusic.tv.core.model.UserGuid
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NetworkRestoreMonitorTest {

    @Test
    fun `only a recovering session retries on network availability`() {
        assertTrue(
            shouldRetrySessionOnNetworkAvailable(
                SessionState.Recovering("nas", "pan", 2, AppError.NetworkUnavailable),
            ),
        )
        assertFalse(shouldRetrySessionOnNetworkAvailable(SessionState.Loading))
        assertFalse(
            shouldRetrySessionOnNetworkAvailable(
                SessionState.SignedOut(error = AppError.NetworkUnavailable),
            ),
        )
        assertFalse(
            shouldRetrySessionOnNetworkAvailable(
                SessionState.SignedIn(
                    ServerIdentity(ServerGuid("s"), "nas", "1.0", "1.0"),
                    User(UserGuid("u"), "pan", nickname = null),
                ),
            ),
        )
    }

    @Test
    @Config(sdk = [28])
    fun `android monitor registers an internet-capability callback without error`() {
        val monitor = AndroidNetworkRestoreMonitor(RuntimeEnvironment.getApplication())
        var notified = false
        monitor.register { notified = true }

        // Registration must not invoke the callback synchronously: delivery belongs
        // to the system, and the coordinator guards on session state per delivery.
        assertFalse(notified)
    }
}
