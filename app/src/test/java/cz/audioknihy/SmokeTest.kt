package cz.audioknihy

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SmokeTest {
    @Test
    fun app_name_is_czech() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Audioknihy", ctx.getString(R.string.app_name))
    }
}
