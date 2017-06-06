package com.juliansparber.vpnMITM;

import android.os.Message;
import android.support.test.espresso.Espresso;
import android.support.test.espresso.ViewInteraction;
import android.support.test.espresso.matcher.ViewMatchers;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import junit.framework.Assert;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import xyz.hexene.localvpn.LocalVPNService;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.scrollTo;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static android.support.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by jSparber on 6/2/17.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class InterceptorActivityTest {
    public InterceptorActivityTest() {
        super();
    }
    public static final String STRING_TO_BE_TYPED = "Espresso";

    @Rule
    public ActivityTestRule<InterceptorActivity> mActivityRule = new ActivityTestRule<>(
            InterceptorActivity.class);

    @Test
    public void floatingButtonTest() {
        //Perform click on the floating button
        onView(withId(R.id.fab)).perform(click());
        //onView(withId(R.id.fab)).check(matches(withId(R.id.fab)));
        //Check if the snackbar shows the right text
        onView(allOf(withId(android.support.design.R.id.snackbar_text), withText(R.string.start_notification)))
                .check(matches(withEffectiveVisibility(
                        ViewMatchers.Visibility.VISIBLE
                )));

        onView(withId(R.id.fab)).perform(click());
        //onView(withId(R.id.fab)).check(matches(withId(R.id.fab)));
        //Check if the snackbar shows the right text
        onView(allOf(withId(android.support.design.R.id.snackbar_text), withText(R.string.stop_notification)))
                .check(matches(withEffectiveVisibility(
                        ViewMatchers.Visibility.VISIBLE
                )));
    }

    @Test
    public void startVpnServiceTest() throws InterruptedException {
        //start vpnService
        mActivityRule.getActivity().startInterceptor();
        //wait that the service has started
        Thread.sleep(1000);
        assertTrue(LocalVPNService.isRunning());
        //stop the service after 5s
        Thread.sleep(5000);
        //test if the service actually stops
        mActivityRule.getActivity().stopInterceptor();
        Thread.sleep(1000);
        assertFalse(LocalVPNService.isRunning());
    }

    @Test
    public void middleServerTest() throws IOException {
        ExecutorService executorService;
        executorService = Executors.newFixedThreadPool(1);
        BufferServer server = new BufferServer(0, 20, null);
        executorService.submit(server);
        //assure that the server has been created
        Assert.assertNotNull(server.getPort());
        String strUrl = "http://localhost:" + server.getPort();

        try {
            URL url = new URL(strUrl);
            HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
            urlConn.connect();

            //the server blockes all traffic which has no original destination,
            //since we make a direct call to the server nobody saves the original destination
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, urlConn.getResponseCode());
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public void userDialogAllow() {
        Message msg = Message.obtain();
        msg.what = Messenger.ALERT_DIALOG;

        String[] payload = new String[4];
        payload[0] = "Test news";
        payload[1] =  "Test APP does create a test dialog.";
        payload[2] = "Test APP";
        payload[3] = "ipAddress" + ":" + "0000";
        msg.obj = payload;
        mActivityRule.getActivity().showWarningToUser(msg);
        // This view is in a different Activity, no need to tell Espresso.
        //onView(withId(R.id.fab)).check(matches(withText(STRING_TO_BE_TYPED)));
        ViewInteraction allowBtn = onView(allOf(withId(android.R.id.button1), withText("Allow")));
        allowBtn.perform(scrollTo(), click());
    }

    @Test
    public void userDialogLater() {
        Message msg = Message.obtain();
        msg.what = Messenger.ALERT_DIALOG;

        String[] payload = new String[4];
        payload[0] = "Test news";
        payload[1] =  "Test APP does create a test dialog.";
        payload[2] = "Test APP";
        payload[3] = "ipAddress" + ":" + "0000";
        msg.obj = payload;
        mActivityRule.getActivity().showWarningToUser(msg);
        // This view is in a different Activity, no need to tell Espresso.
        //onView(withId(R.id.fab)).check(matches(withText(STRING_TO_BE_TYPED)));
        ViewInteraction laterBtn = onView(allOf(withId(android.R.id.button3), withText("Later")));
        laterBtn.perform(scrollTo(), click());
    }

    @Test
    public void userDialogNever() {
        Message msg = Message.obtain();
        msg.what = Messenger.ALERT_DIALOG;

        String[] payload = new String[4];
        payload[0] = "Test news";
        payload[1] =  "Test APP does create a test dialog.";
        payload[2] = "Test APP";
        payload[3] = "ipAddress" + ":" + "0000";
        msg.obj = payload;
        mActivityRule.getActivity().showWarningToUser(msg);
        // This view is in a different Activity, no need to tell Espresso.
        //onView(withId(R.id.fab)).check(matches(withText(STRING_TO_BE_TYPED)));
        ViewInteraction neverBtn = onView(allOf(withId(android.R.id.button2), withText("Never")));
        neverBtn.perform(scrollTo(), click());
    }
    @Test
    public void multipleUserDialog() throws InterruptedException {
        Message msg = Message.obtain();
        msg.what = Messenger.ALERT_DIALOG;

        String[] payload = new String[4];
        payload[0] = "Test news";
        payload[1] =  "Test APP does create a test dialog.";
        payload[2] = "Test APP";
        payload[3] = "ipAddress" + ":" + "0000";
        msg.obj = payload;
        mActivityRule.getActivity().showWarningToUser(msg);
        Thread.sleep(1000);
        mActivityRule.getActivity().finish();
        Thread.sleep(1000);
        mActivityRule.getActivity().showWarningToUser(msg);
        Thread.sleep(1000);
        mActivityRule.getActivity().showWarningToUser(msg);
        // This view is in a different Activity, no need to tell Espresso.
        //onView(withId(R.id.fab)).check(matches(withText(STRING_TO_BE_TYPED)));
        ViewInteraction neverBtn = onView(allOf(withId(android.R.id.button2), withText("Never")));
        neverBtn.perform(scrollTo(), click());
        neverBtn.perform(scrollTo(), click());
    }
}