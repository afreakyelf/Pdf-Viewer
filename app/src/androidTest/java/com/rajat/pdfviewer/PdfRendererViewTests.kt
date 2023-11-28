
package com.rajat.pdfviewer
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.rajat.sample.pdfviewer.MainActivity
import com.rajat.sample.pdfviewer.databinding.ActivityMainBinding
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
class PdfRendererViewEspressoTests {

    private lateinit var activityScenario: ActivityScenario<MainActivity>
    private lateinit var binding: ActivityMainBinding

    @Before
    fun setUp() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario.onActivity { activity ->
            binding = ActivityMainBinding.inflate(activity.layoutInflater)
            activity.setContentView(binding.root)
        }
    }

    @Test
    fun testLaunchPdfViewerActivity() {
        onView(withId(binding.onlinePdf.id)).check(matches(isDisplayed()))
        onView(withId(binding.onlinePdf.id)).perform(click())
    }

    @Test
    fun testPdfRendererInitializationWithUrl() {
        onView(withId(R.id.pdfView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testPdfRendererUIElementsVisibility() {
        onView(allOf(withId(R.id.pageNumber), isDisplayed()))
        onView(allOf(withId(R.id.progressBar), isDisplayed()))
    }

    @Test
    fun testScrollingFunctionality() {
        onView(withId(R.id.pdfView)).perform(swipeDown())
        onView(withId(R.id.pdfView)).perform(swipeUp())
    }

    @After
    fun tearDown() {
        // Close activities or clean up resources
    }
}
