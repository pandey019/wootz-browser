// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import static org.chromium.base.test.util.Restriction.RESTRICTION_TYPE_LOW_END_DEVICE;
import static org.chromium.base.test.util.Restriction.RESTRICTION_TYPE_NON_LOW_END_DEVICE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;

import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsService;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.browser.customtabs.CustomTabsSessionToken;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.test.util.Batch;
import org.chromium.base.test.util.CallbackHelper;
import org.chromium.base.test.util.CommandLineFlags;
import org.chromium.base.test.util.Criteria;
import org.chromium.base.test.util.CriteriaHelper;
import org.chromium.base.test.util.Features.EnableFeatures;
import org.chromium.base.test.util.Restriction;
import org.chromium.chrome.browser.WarmupManager;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.flags.ChromeSwitches;
import org.chromium.chrome.browser.prefetch.settings.PreloadPagesSettingsBridge;
import org.chromium.chrome.browser.prefetch.settings.PreloadPagesState;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.profiles.ProfileManager;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.test.ChromeJUnit4ClassRunner;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.test.util.TestCallbackHelperContainer.OnEvaluateJavaScriptResultHelper;
import org.chromium.content_public.browser.test.util.TestThreadUtils;
import org.chromium.content_public.browser.test.util.WebContentsUtils;
import org.chromium.net.test.EmbeddedTestServer;
import org.chromium.net.test.ServerCertificate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Tests for CustomTabsConnection. */
@RunWith(ChromeJUnit4ClassRunner.class)
@Batch(Batch.PER_CLASS)
public class CustomTabsConnectionTest {
    private CustomTabsConnection mCustomTabsConnection;
    private static final String URL = "http://www.google.com";
    private static final String URL2 = "https://www.android.com";
    private static final String URL3 = "https://example.com";
    private static final String INVALID_SCHEME_URL = "intent://www.google.com";

    @Rule
    public CustomTabActivityTestRule mCustomTabActivityTestRule = new CustomTabActivityTestRule();

    @Before
    public void setUp() {
        LibraryLoader.getInstance().ensureInitialized();
        mCustomTabsConnection = CustomTabsTestUtils.setUpConnection();
    }

    @After
    public void tearDown() {
        CustomTabsTestUtils.cleanupSessions(mCustomTabsConnection);
        TestThreadUtils.runOnUiThreadBlocking(
                () -> WarmupManager.getInstance().destroySpareWebContents());
    }

    /**
     * Tests that we can create a new session. Registering with a null callback fails. Registering a
     * session with an {@linkplain CustomTabsSessionToken#equals equal} session token will update
     * the callback for the session.
     */
    @Test
    @SmallTest
    public void testNewSession() {
        Assert.assertFalse(mCustomTabsConnection.newSession(null));
        CustomTabsSessionToken token = CustomTabsSessionToken.createMockSessionTokenForTesting();
        Assert.assertTrue(mCustomTabsConnection.newSession(token));
        // Request to update callback for the session.
        Assert.assertTrue(mCustomTabsConnection.newSession(token));
    }

    /** Tests that we can create several sessions. */
    @Test
    @SmallTest
    public void testSeveralSessions() {
        CustomTabsSessionToken token = CustomTabsSessionToken.createMockSessionTokenForTesting();
        Assert.assertTrue(mCustomTabsConnection.newSession(token));
        CustomTabsSessionToken token2 = CustomTabsSessionToken.createMockSessionTokenForTesting();
        Assert.assertTrue(mCustomTabsConnection.newSession(token2));
    }

    /**
     * Tests that {@link CustomTabsConnection#warmup(long)} succeeds and can be issued multiple
     * times.
     */
    @Test
    @SmallTest
    public void testCanWarmup() throws Exception {
        CustomTabsTestUtils.warmUpAndWait();
        CustomTabsTestUtils.warmUpAndWait();
    }

    @Test
    @SmallTest
    @Restriction(RESTRICTION_TYPE_NON_LOW_END_DEVICE)
    public void testCreateSpareRenderer() throws Exception {
        CustomTabsTestUtils.warmUpAndWait();
        // On UI thread because:
        // 1. takeSpareWebContents needs to be called from the UI thread.
        // 2. warmup() is non-blocking and posts tasks to the UI thread, it ensures proper ordering.
        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    WarmupManager warmupManager = WarmupManager.getInstance();
                    Assert.assertTrue(warmupManager.hasSpareWebContents());
                    WebContents webContents = warmupManager.takeSpareWebContents(false, false);
                    Assert.assertNotNull(webContents);
                    Assert.assertFalse(warmupManager.hasSpareWebContents());
                    webContents.destroy();
                });
    }

    @Test
    @SmallTest
    @Restriction(RESTRICTION_TYPE_LOW_END_DEVICE)
    public void testDoNotCreateSpareRendererOnLowEnd() throws Exception {
        CustomTabsTestUtils.warmUpAndWait();
        // On UI thread because:
        // 1. takeSpareWebContents needs to be called from the UI thread.
        // 2. warmup() is non-blocking and posts tasks to the UI thread, it ensures proper ordering.
        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    WarmupManager warmupManager = WarmupManager.getInstance();
                    Assert.assertFalse(warmupManager.hasSpareWebContents());
                });
    }

    @Test
    @SmallTest
    @Restriction(RESTRICTION_TYPE_NON_LOW_END_DEVICE)
    public void testCreateSpareRendererCanBeRecreated() throws Exception {
        CustomTabsTestUtils.warmUpAndWait();
        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    assertSpareWebContentsNotNullAndDestroy();
                    Assert.assertFalse(WarmupManager.getInstance().hasSpareWebContents());
                });
        CustomTabsTestUtils.warmUpAndWait();
        TestThreadUtils.runOnUiThreadBlocking(this::assertSpareWebContentsNotNullAndDestroy);
    }

    @Test
    @SmallTest
    @Restriction(RESTRICTION_TYPE_NON_LOW_END_DEVICE)
    public void testHiddenTabTakessSpareRenderer() throws Exception {
        final CustomTabsSessionToken token =
                CustomTabsSessionToken.createMockSessionTokenForTesting();
        mCustomTabsConnection.newSession(token);
        mCustomTabsConnection.setShouldSpeculateLoadOnCellularForSession(token, true);
        assertWarmupAndMayLaunchUrl(token, URL, true);
        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertFalse(WarmupManager.getInstance().hasSpareWebContents());
                });
    }

    /*
     * Tests that when the disconnection notification comes from a non-UI thread, Chrome doesn't
     * crash. Non-regression test for crbug.com/623128.
     */
    @Test
    @SmallTest
    @Restriction(RESTRICTION_TYPE_NON_LOW_END_DEVICE)
    public void testPrerenderAndDisconnectOnOtherThread() throws Exception {
        final CustomTabsSessionToken token = assertWarmupAndMayLaunchUrl(null, URL, true);
        final Thread otherThread = new Thread(() -> mCustomTabsConnection.cleanUpSession(token));

        TestThreadUtils.runOnUiThreadBlocking(otherThread::start);
        // Should not crash, hence no assertions below.
    }

    @Test
    @SmallTest
    @Restriction(RESTRICTION_TYPE_NON_LOW_END_DEVICE)
    public void testMayLaunchUrlKeepsSpareRendererWithoutHiddenTab() throws Exception {
        CustomTabsTestUtils.warmUpAndWait();
        final CustomTabsSessionToken token =
                CustomTabsSessionToken.createMockSessionTokenForTesting();
        Assert.assertTrue(mCustomTabsConnection.newSession(token));

        mCustomTabsConnection.setShouldSpeculateLoadOnCellularForSession(token, true);
        mCustomTabsConnection.setCanUseHiddenTabForSession(token, false);
        Assert.assertTrue(mCustomTabsConnection.mayLaunchUrl(token, Uri.parse(URL), null, null));

        TestThreadUtils.runOnUiThreadBlocking(() -> assertSpareWebContentsNotNullAndDestroy());
    }

    @Test
    @SmallTest
    public void testMayLaunchUrlNullOrEmptyUrl() throws Exception {
        assertWarmupAndMayLaunchUrl(null, null, true);
        CustomTabsTestUtils.cleanupSessions(mCustomTabsConnection); // Resets throttling.
        assertWarmupAndMayLaunchUrl(null, "", true);
    }

    /** Tests that a new mayLaunchUrl() call destroys the previous hidden tab. */
    @Test
    @SmallTest
    @Restriction(RESTRICTION_TYPE_NON_LOW_END_DEVICE)
    @CommandLineFlags.Add(ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE)
    public void testOnlyOneHiddenTab() throws Exception {
        Assert.assertTrue("Failed warmup()", mCustomTabsConnection.warmup(0));
        CustomTabsSessionToken token = CustomTabsSessionToken.createMockSessionTokenForTesting();
        Assert.assertTrue("Failed newSession()", mCustomTabsConnection.newSession(token));
        mCustomTabsConnection.setCanUseHiddenTabForSession(token, true);

        // First hidden tab, add an observer to check that it's destroyed.
        Assert.assertTrue(
                "Failed first mayLaunchUrl()",
                mCustomTabsConnection.mayLaunchUrl(token, Uri.parse(URL), null, null));
        final CallbackHelper tabDestroyedHelper = new CallbackHelper();
        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertNotNull(
                            "Null speculation, first one",
                            mCustomTabsConnection.getSpeculationParamsForTesting());
                    Tab tab = mCustomTabsConnection.getSpeculationParamsForTesting().tab;
                    Assert.assertNotNull("No first tab", tab);
                    tab.addObserver(
                            new EmptyTabObserver() {
                                @Override
                                public void onDestroyed(Tab destroyedTab) {
                                    tabDestroyedHelper.notifyCalled();
                                }
                            });
                });

        // New hidden tab.
        mCustomTabsConnection.resetThrottling(Process.myUid());
        Assert.assertTrue(
                "Failed second mayLaunchUrl()",
                mCustomTabsConnection.mayLaunchUrl(token, Uri.parse(URL2), null, null));
        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertNotNull(
                            "Null speculation, new hidden tab",
                            mCustomTabsConnection.getSpeculationParamsForTesting());
                    Assert.assertNotNull(
                            "No second tab",
                            mCustomTabsConnection.getSpeculationParamsForTesting().tab);
                    Assert.assertEquals(
                            URL2, mCustomTabsConnection.getSpeculationParamsForTesting().url);
                });
        tabDestroyedHelper.waitForCallback("The first hidden tab should have been destroyed", 0);

        // Clears the second hidden tab.
        mCustomTabsConnection.resetThrottling(Process.myUid());
        Assert.assertTrue(
                "Failed cleanup mayLaunchUrl()",
                mCustomTabsConnection.mayLaunchUrl(token, null, null, null));
    }

    /** Tests that if the renderer backing a hidden tab is killed, the speculation is canceled. */
    @Test
    @SmallTest
    @Restriction(RESTRICTION_TYPE_NON_LOW_END_DEVICE)
    @CommandLineFlags.Add(ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE)
    public void testKillHiddenTabRenderer() throws Exception {
        Assert.assertTrue("Failed warmup()", mCustomTabsConnection.warmup(0));
        CustomTabsSessionToken token = CustomTabsSessionToken.createMockSessionTokenForTesting();
        Assert.assertTrue("Failed newSession()", mCustomTabsConnection.newSession(token));
        mCustomTabsConnection.setShouldSpeculateLoadOnCellularForSession(token, true);
        Assert.assertTrue(
                "Failed first mayLaunchUrl()",
                mCustomTabsConnection.mayLaunchUrl(token, Uri.parse(URL), null, null));
        final CallbackHelper tabDestroyedHelper = new CallbackHelper();
        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertNotNull(
                            "Null speculation",
                            mCustomTabsConnection.getSpeculationParamsForTesting());
                    Tab speculationTab = mCustomTabsConnection.getSpeculationParamsForTesting().tab;
                    Assert.assertNotNull("Null speculation tab", speculationTab);
                    speculationTab.addObserver(
                            new EmptyTabObserver() {
                                @Override
                                public void onDestroyed(Tab tab) {
                                    tabDestroyedHelper.notifyCalled();
                                }
                            });
                    WebContentsUtils.simulateRendererKilled(speculationTab.getWebContents());
                });
        tabDestroyedHelper.waitForCallback("The speculated tab was not destroyed", 0);
    }

    @Test
    @SmallTest
    @Restriction(RESTRICTION_TYPE_NON_LOW_END_DEVICE)
    public void testUnderstandsLowConfidenceMayLaunchUrl() {
        final CustomTabsSessionToken token =
                CustomTabsSessionToken.createMockSessionTokenForTesting();
        Assert.assertTrue(mCustomTabsConnection.newSession(token));
        List<Bundle> urls = new ArrayList<>();
        Bundle urlBundle = new Bundle();
        urlBundle.putParcelable(CustomTabsService.KEY_URL, Uri.parse(URL));
        urls.add(urlBundle);
        mCustomTabsConnection.mayLaunchUrl(token, null, null, urls);

        TestThreadUtils.runOnUiThreadBlocking(this::assertSpareWebContentsNotNullAndDestroy);
    }

    @Test
    @SmallTest
    public void testLowConfidenceMayLaunchUrlOnlyAcceptUris() throws Exception {
        final CustomTabsSessionToken token =
                CustomTabsSessionToken.createMockSessionTokenForTesting();
        Assert.assertTrue(mCustomTabsConnection.newSession(token));
        CustomTabsTestUtils.warmUpAndWait();

        final List<Bundle> urlsAsString = new ArrayList<>();
        Bundle urlStringBundle = new Bundle();
        urlStringBundle.putString(CustomTabsService.KEY_URL, URL);
        urlsAsString.add(urlStringBundle);

        final List<Bundle> urlsAsUri = new ArrayList<>();
        Bundle urlUriBundle = new Bundle();
        urlUriBundle.putParcelable(CustomTabsService.KEY_URL, Uri.parse(URL));
        urlsAsUri.add(urlUriBundle);

        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    Assert.assertFalse(
                            mCustomTabsConnection.lowConfidenceMayLaunchUrl(urlsAsString));
                    Assert.assertTrue(mCustomTabsConnection.lowConfidenceMayLaunchUrl(urlsAsUri));
                });
    }

    @Test
    @SmallTest
    public void testLowConfidenceMayLaunchUrlDoesntCrash() throws Exception {
        final CustomTabsSessionToken token =
                CustomTabsSessionToken.createMockSessionTokenForTesting();
        Assert.assertTrue(mCustomTabsConnection.newSession(token));
        CustomTabsTestUtils.warmUpAndWait();

        final List<Bundle> invalidBundles = new ArrayList<>();
        Bundle invalidBundle = new Bundle();
        invalidBundle.putParcelable(CustomTabsService.KEY_URL, new Intent());
        invalidBundles.add(invalidBundle);

        TestThreadUtils.runOnUiThreadBlockingNoException(
                () -> mCustomTabsConnection.lowConfidenceMayLaunchUrl(invalidBundles));
    }

    @Test
    @SmallTest
    @Restriction(RESTRICTION_TYPE_NON_LOW_END_DEVICE)
    public void testStillHighConfidenceMayLaunchUrlWithSeveralUrls() {
        final CustomTabsSessionToken token =
                CustomTabsSessionToken.createMockSessionTokenForTesting();
        Assert.assertTrue(mCustomTabsConnection.newSession(token));
        mCustomTabsConnection.setShouldSpeculateLoadOnCellularForSession(token, true);
        List<Bundle> urls = new ArrayList<>();
        Bundle urlBundle = new Bundle();
        urlBundle.putParcelable(CustomTabsService.KEY_URL, Uri.parse(URL));
        urls.add(urlBundle);

        mCustomTabsConnection.mayLaunchUrl(token, Uri.parse(URL), null, urls);
        TestThreadUtils.runOnUiThreadBlocking(
                () ->
                        Assert.assertNull(
                                WarmupManager.getInstance().takeSpareWebContents(false, false)));
    }

    /**
     * Tests that the tab used my mayLaunchUrl's high confidence mode uses a separate, empty, cookie
     * jar from the normal navigations.
     */
    @Test
    @SmallTest
    @Restriction(RESTRICTION_TYPE_NON_LOW_END_DEVICE)
    @EnableFeatures(ChromeFeatureList.MAYLAUNCHURL_USES_SEPARATE_STORAGE_PARTITION)
    @CommandLineFlags.Add(ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE)
    public void testMayLaunchUrlUsesSeparateCookieJar() throws Exception {
        // This test has 4 major parts.
        // 1. Launch a custom tab and set a cookie.
        // 2. Launch a hidden tab to the same site via mayLaunchUrl, set a different cookie, and
        // confirm only that cookie is accessible.
        // 3. Launch a second hidden tab, set a third cookie, and confirm only that cookie is
        // accessible.
        // 4. Launch a second custom tab and confirm that it sees the third cookie.
        // 5. Launch a third custom tab and confirm that it sees the first cookie.

        Context context = ApplicationProvider.getApplicationContext();

        Assert.assertTrue("Failed warmup()", mCustomTabsConnection.warmup(0));

        EmbeddedTestServer server =
                EmbeddedTestServer.createAndStartHTTPSServer(context, ServerCertificate.CERT_OK);
        final String url = server.getURL("/chrome/test/data/android/simple.html");

        final OnEvaluateJavaScriptResultHelper JsHelper = new OnEvaluateJavaScriptResultHelper();

        // Launch a custom tab and load the url.
        Assert.assertTrue("Failed warmup()", mCustomTabsConnection.warmup(0));
        Intent intent = CustomTabsIntentTestUtils.createMinimalCustomTabIntent(context, url);
        mCustomTabActivityTestRule.launchActivity(intent);
        Tab normalTab = mCustomTabActivityTestRule.getActivity().getActivityTab();

        // We can check if the page title is correct to know if the tab is done loading.
        CriteriaHelper.pollUiThread(
                () ->
                        Criteria.checkThat(
                                normalTab.getWebContents().getTitle(),
                                Matchers.is("Activity test page")));
        // Set a cookie.
        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    JsHelper.evaluateJavaScriptForTests(
                            normalTab.getWebContents(),
                            "document.cookie = \"foo=bar; max-age = 1000 \";" + " document.cookie");
                });

        JsHelper.waitUntilHasValue(5, TimeUnit.SECONDS);
        Assert.assertTrue("Failed to retrieve JavaScript evaluation results.", JsHelper.hasValue());
        // Verify the tab has the expected cookie.
        Assert.assertEquals("\"foo=bar\"", JsHelper.getJsonResultAndClear());
        mCustomTabActivityTestRule.getActivity().finish();

        // Launch the first hidden tab. This tab should use a separate storage partition and
        // therefore shouldn't see the first cookie.
        Assert.assertTrue("Failed warmup()", mCustomTabsConnection.warmup(0));
        Intent intent2 = CustomTabsIntentTestUtils.createMinimalCustomTabIntent(context, url);
        CustomTabsSessionToken token = CustomTabsSessionToken.getSessionTokenFromIntent(intent2);
        Assert.assertTrue("Failed newSession()", mCustomTabsConnection.newSession(token));
        mCustomTabsConnection.setCanUseHiddenTabForSession(token, true);

        Assert.assertTrue(
                "Failed first mayLaunchUrl()",
                mCustomTabsConnection.mayLaunchUrl(token, Uri.parse(url), null, null));

        CriteriaHelper.pollUiThread(
                () ->
                        Criteria.checkThat(
                                mCustomTabsConnection.getSpeculationParamsForTesting(),
                                Matchers.notNullValue()));
        Tab hiddenTab = mCustomTabsConnection.getSpeculationParamsForTesting().tab;
        CriteriaHelper.pollUiThread(
                () ->
                        Criteria.checkThat(
                                hiddenTab.getWebContents().getTitle(),
                                Matchers.is("Activity test page")));
        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    JsHelper.evaluateJavaScriptForTests(
                            hiddenTab.getWebContents(),
                            "document.cookie = \"foo_hidden=bar; max-age =1000 \";"
                                    + " document.cookie");
                });

        JsHelper.waitUntilHasValue(5, TimeUnit.SECONDS);
        Assert.assertTrue("Failed to retrieve JavaScript evaluation results.", JsHelper.hasValue());
        // The hidden tab should only see the cookie it set.
        Assert.assertEquals("\"foo_hidden=bar\"", JsHelper.getJsonResultAndClear());

        // Launch another hidden tab. Doing this closes the first hidden tab and causes the cookie
        // jar to be cleared.
        mCustomTabsConnection.resetThrottling(Process.myUid());
        Assert.assertTrue(
                "Failed second mayLaunchUrl()",
                mCustomTabsConnection.mayLaunchUrl(token, Uri.parse(url), null, null));

        CriteriaHelper.pollUiThread(
                () ->
                        Criteria.checkThat(
                                mCustomTabsConnection.getSpeculationParamsForTesting(),
                                Matchers.notNullValue()));
        Tab hiddenTab2 = mCustomTabsConnection.getSpeculationParamsForTesting().tab;
        CriteriaHelper.pollUiThread(
                () ->
                        Criteria.checkThat(
                                hiddenTab2.getWebContents().getTitle(),
                                Matchers.is("Activity test page")));
        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    JsHelper.evaluateJavaScriptForTests(
                            hiddenTab2.getWebContents(), "document.cookie=\"foo_hidden2=baz\"");
                });

        JsHelper.waitUntilHasValue(5, TimeUnit.SECONDS);
        Assert.assertTrue("Failed to retrieve JavaScript evaluation results.", JsHelper.hasValue());
        // The second hidden tab should only have the cookie it set.
        Assert.assertEquals("\"foo_hidden2=baz\"", JsHelper.getJsonResultAndClear());

        // Launch the second custom tab. Because there is already a hidden tab for the same url this
        // custom tab should just re-use the hidden tab. This means that this tab will use the same
        // storage partition and therefore access the same cookie.
        mCustomTabActivityTestRule.launchActivity(intent2);
        Tab normalTab2 = mCustomTabActivityTestRule.getActivity().getActivityTab();

        CriteriaHelper.pollUiThread(
                () ->
                        Criteria.checkThat(
                                normalTab2.getWebContents().getTitle(),
                                Matchers.is("Activity test page")));
        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    JsHelper.evaluateJavaScriptForTests(
                            normalTab2.getWebContents(), "document.cookie");
                });

        JsHelper.waitUntilHasValue(5, TimeUnit.SECONDS);
        Assert.assertTrue("Failed to retrieve JavaScript evaluation results.", JsHelper.hasValue());
        // This custom tab should see the third cookie set.
        Assert.assertEquals("\"foo_hidden2=baz\"", JsHelper.getJsonResultAndClear());

        // Finally, launch a third custom tab. Because there isn't an associated mayLaunchUrl this
        // custom tab will use the default storage partition and will access the first cookie.
        Assert.assertTrue("Failed warmup()", mCustomTabsConnection.warmup(0));
        Intent intent3 = CustomTabsIntentTestUtils.createMinimalCustomTabIntent(context, url);
        mCustomTabActivityTestRule.launchActivity(intent3);
        Tab normalTab3 = mCustomTabActivityTestRule.getActivity().getActivityTab();

        CriteriaHelper.pollUiThread(
                () ->
                        Criteria.checkThat(
                                normalTab3.getWebContents().getTitle(),
                                Matchers.is("Activity test page")));
        TestThreadUtils.runOnUiThreadBlocking(
                () -> {
                    JsHelper.evaluateJavaScriptForTests(
                            normalTab3.getWebContents(), "document.cookie");
                });

        JsHelper.waitUntilHasValue(5, TimeUnit.SECONDS);
        Assert.assertTrue("Failed to retrieve JavaScript evaluation results.", JsHelper.hasValue());
        // This custom tab should see the third cookie set.
        Assert.assertEquals("\"foo=bar\"", JsHelper.getJsonResultAndClear());
    }

    private void assertSpareWebContentsNotNullAndDestroy() {
        WebContents webContents = WarmupManager.getInstance().takeSpareWebContents(false, false);
        Assert.assertNotNull(webContents);
        webContents.destroy();
    }

    /**
     * Calls warmup() and mayLaunchUrl(), checks for the expected result (success or failure) and
     * returns the result code.
     */
    private CustomTabsSessionToken assertWarmupAndMayLaunchUrl(
            CustomTabsSessionToken token, String url, boolean shouldSucceed) throws Exception {
        CustomTabsTestUtils.warmUpAndWait();
        if (token == null) {
            token = CustomTabsSessionToken.createMockSessionTokenForTesting();
            mCustomTabsConnection.newSession(token);
        }
        Uri uri = url == null ? null : Uri.parse(url);
        boolean succeeded = mCustomTabsConnection.mayLaunchUrl(token, uri, null, null);
        Assert.assertEquals(shouldSucceed, succeeded);
        return shouldSucceed ? token : null;
    }

    /**
     * Tests that {@link CustomTabsConnection#mayLaunchUrl( CustomTabsSessionToken, Uri,
     * android.os.Bundle, java.util.List)} returns an error when called with an invalid session ID.
     */
    @Test
    @SmallTest
    public void testNoMayLaunchUrlWithInvalidSessionId() throws Exception {
        assertWarmupAndMayLaunchUrl(
                CustomTabsSessionToken.createMockSessionTokenForTesting(), URL, false);
    }

    /**
     * Tests that {@link CustomTabsConnection#mayLaunchUrl(CustomTabsSessionToken, Uri, Bundle,
     * List)} rejects invalid URL schemes.
     */
    @Test
    @SmallTest
    public void testNoMayLaunchUrlWithInvalidScheme() throws Exception {
        assertWarmupAndMayLaunchUrl(null, INVALID_SCHEME_URL, false);
    }

    /**
     * Tests that {@link CustomTabsConnection#mayLaunchUrl(CustomTabsSessionToken, Uri, Bundle,
     * List)} succeeds.
     */
    @Test
    @SmallTest
    public void testMayLaunchUrl() throws Exception {
        assertWarmupAndMayLaunchUrl(null, URL, true);
    }

    /**
     * Tests that {@link CustomTabsConnection#mayLaunchUrl(CustomTabsSessionToken, Uri, Bundle,
     * List)} can be called several times with the same, and different URLs.
     */
    @Test
    @SmallTest
    public void testMultipleMayLaunchUrl() throws Exception {
        CustomTabsSessionToken token = assertWarmupAndMayLaunchUrl(null, URL, true);
        mCustomTabsConnection.resetThrottling(Process.myUid());
        assertWarmupAndMayLaunchUrl(token, URL, true);
        mCustomTabsConnection.resetThrottling(Process.myUid());
        assertWarmupAndMayLaunchUrl(token, URL2, true);
    }

    /** Tests that sessions are forgotten properly. */
    @Test
    @SmallTest
    public void testForgetsSession() throws Exception {
        CustomTabsSessionToken token = assertWarmupAndMayLaunchUrl(null, URL, true);
        CustomTabsTestUtils.cleanupSessions(mCustomTabsConnection);
        assertWarmupAndMayLaunchUrl(token, URL, false);
    }

    /** Tests that whether we can detect access rights to /proc/pid/. */
    @Test
    @SmallTest
    public void testCanGetSchedulerGroup() {
        // self is always accessible.
        Assert.assertTrue(CustomTabsConnection.canGetSchedulerGroup(Process.myPid()));
        // PID 1 always exists, yet should never be accessible by regular apps.
        Assert.assertFalse(CustomTabsConnection.canGetSchedulerGroup(1));
    }

    /** Tests that predictions are throttled. */
    @Test
    @SmallTest
    public void testThrottleMayLaunchUrl() throws Exception {
        CustomTabsSessionToken token = assertWarmupAndMayLaunchUrl(null, URL, true);
        int successfulRequests = 0;
        // Send a burst of requests instead of checking for precise delays to avoid flakiness.
        while (successfulRequests < 10) {
            if (!mCustomTabsConnection.mayLaunchUrl(token, Uri.parse(URL), null, null)) break;
            successfulRequests++;
        }
        Assert.assertTrue("10 requests in a row should not all succeed.", successfulRequests < 10);
    }

    /** Tests that the mayLaunchUrl() throttling is reset after a long enough wait. */
    @Test
    @SmallTest
    public void testThrottlingIsReset() throws Exception {
        CustomTabsSessionToken token = assertWarmupAndMayLaunchUrl(null, URL, true);
        // Depending on the timing, the delay should be 100 or 200ms here.
        assertWarmupAndMayLaunchUrl(token, URL, true);
        // assertWarmUpAndMayLaunchUrl() can take longer than the throttling delay.
        Assert.assertFalse(mCustomTabsConnection.mayLaunchUrl(token, Uri.parse(URL), null, null));
        // Wait for more than 2 * MAX_POSSIBLE_DELAY to clear the delay
        try {
            Thread.sleep(450); // 2 * MAX_POSSIBLE_DELAY + 50ms
        } catch (InterruptedException e) {
            Assert.fail();
            return;
        }
        assertWarmupAndMayLaunchUrl(token, URL, true);
        // Check that the delay has been reset, by waiting for 100ms.
        try {
            Thread.sleep(150); // MIN_DELAY + 50ms margin
        } catch (InterruptedException e) {
            Assert.fail();
            return;
        }
        assertWarmupAndMayLaunchUrl(token, URL, true);
    }

    /** Tests that throttling applies across sessions. */
    @Test
    @SmallTest
    public void testThrottlingAcrossSessions() throws Exception {
        CustomTabsSessionToken token = assertWarmupAndMayLaunchUrl(null, URL, true);
        mCustomTabsConnection.resetThrottling(Process.myUid());
        CustomTabsSessionToken token2 = assertWarmupAndMayLaunchUrl(null, URL, true);
        mCustomTabsConnection.resetThrottling(Process.myUid());
        for (int i = 0; i < 10; i++) {
            mCustomTabsConnection.mayLaunchUrl(token, Uri.parse(URL), null, null);
        }
        Assert.assertFalse(mCustomTabsConnection.mayLaunchUrl(token2, Uri.parse(URL), null, null));
    }

    @Test
    @SmallTest
    @Restriction(RESTRICTION_TYPE_NON_LOW_END_DEVICE)
    public void testBanningWorks() {
        mCustomTabsConnection.ban(Process.myUid());
        final CustomTabsSessionToken token =
                CustomTabsSessionToken.createMockSessionTokenForTesting();
        Assert.assertTrue(mCustomTabsConnection.newSession(token));

        Assert.assertTrue(mCustomTabsConnection.mayLaunchUrl(token, Uri.parse(URL), null, null));
        TestThreadUtils.runOnUiThreadBlocking(this::assertSpareWebContentsNotNullAndDestroy);
    }

    @Test
    @SmallTest
    @Restriction(RESTRICTION_TYPE_NON_LOW_END_DEVICE)
    public void testBanningDisabledForCellular() {
        mCustomTabsConnection.ban(Process.myUid());
        final CustomTabsSessionToken token =
                CustomTabsSessionToken.createMockSessionTokenForTesting();
        Assert.assertTrue(mCustomTabsConnection.newSession(token));
        mCustomTabsConnection.setShouldSpeculateLoadOnCellularForSession(token, true);

        Assert.assertTrue(mCustomTabsConnection.mayLaunchUrl(token, Uri.parse(URL), null, null));
        TestThreadUtils.runOnUiThreadBlocking(
                () ->
                        Assert.assertNull(
                                WarmupManager.getInstance().takeSpareWebContents(false, false)));
    }

    @Test
    @SmallTest
    @Restriction(RESTRICTION_TYPE_NON_LOW_END_DEVICE)
    public void testCellularPrerenderingDoesntOverrideSettings() throws Exception {
        CustomTabsSessionToken token = CustomTabsSessionToken.createMockSessionTokenForTesting();
        Assert.assertTrue(mCustomTabsConnection.newSession(token));
        mCustomTabsConnection.setShouldSpeculateLoadOnCellularForSession(token, true);
        CustomTabsTestUtils.warmUpAndWait();

        // Needs the browser process to be initialized.
        @PreloadPagesState
        int state =
                TestThreadUtils.runOnUiThreadBlocking(
                        () -> {
                            Profile profile = ProfileManager.getLastUsedRegularProfile();
                            @PreloadPagesState
                            int oldState = PreloadPagesSettingsBridge.getState(profile);
                            PreloadPagesSettingsBridge.setState(
                                    profile, PreloadPagesState.NO_PRELOADING);
                            return oldState;
                        });

        try {
            Assert.assertTrue(
                    mCustomTabsConnection.mayLaunchUrl(token, Uri.parse(URL), null, null));
            TestThreadUtils.runOnUiThreadBlocking(this::assertSpareWebContentsNotNullAndDestroy);
        } finally {
            TestThreadUtils.runOnUiThreadBlocking(
                    () ->
                            PreloadPagesSettingsBridge.setState(
                                    ProfileManager.getLastUsedRegularProfile(), state));
        }
    }

    @Test
    @SmallTest
    @Restriction(RESTRICTION_TYPE_NON_LOW_END_DEVICE)
    public void testHiddenTabTakesSpareRenderer() throws Exception {
        CustomTabsSessionToken token = CustomTabsSessionToken.createMockSessionTokenForTesting();
        Assert.assertTrue(mCustomTabsConnection.newSession(token));
        mCustomTabsConnection.setShouldSpeculateLoadOnCellularForSession(token, true);
        CustomTabsTestUtils.warmUpAndWait();
        TestThreadUtils.runOnUiThreadBlocking(
                () -> Assert.assertTrue(WarmupManager.getInstance().hasSpareWebContents()));
        Assert.assertTrue(mCustomTabsConnection.mayLaunchUrl(token, Uri.parse(URL), null, null));
        TestThreadUtils.runOnUiThreadBlocking(
                () -> Assert.assertFalse(WarmupManager.getInstance().hasSpareWebContents()));
    }

    @Test
    @SmallTest
    public void testWarmupNotificationIsSent() throws Exception {
        final AtomicReference<CustomTabsClient> clientReference = new AtomicReference<>(null);
        final CallbackHelper waitForConnection = new CallbackHelper();
        CustomTabsClient.bindCustomTabsService(
                ApplicationProvider.getApplicationContext(),
                ApplicationProvider.getApplicationContext().getPackageName(),
                new CustomTabsServiceConnection() {
                    @Override
                    public void onServiceDisconnected(ComponentName name) {}

                    @Override
                    public void onCustomTabsServiceConnected(
                            ComponentName name, CustomTabsClient client) {
                        clientReference.set(client);
                        waitForConnection.notifyCalled();
                    }
                });
        waitForConnection.waitForCallback(0);
        CustomTabsClient client = clientReference.get();
        final CallbackHelper warmupWaiter = new CallbackHelper();
        newSessionWithWarmupWaiter(client, warmupWaiter);
        newSessionWithWarmupWaiter(client, warmupWaiter);

        // Both sessions should be notified.
        Assert.assertTrue(mCustomTabsConnection.warmup(0));
        warmupWaiter.waitForCallback(0, 2);

        // Notifications should be sent even if warmup() has already been called.
        Assert.assertTrue(mCustomTabsConnection.warmup(0));
        warmupWaiter.waitForCallback(2, 2);
    }

    private static CustomTabsSession newSessionWithWarmupWaiter(
            CustomTabsClient client, final CallbackHelper waiter) {
        return client.newSession(
                new CustomTabsCallback() {
                    @Override
                    public void extraCallback(String callbackName, Bundle args) {
                        if (callbackName.equals(CustomTabsConnection.ON_WARMUP_COMPLETED)) {
                            waiter.notifyCalled();
                        }
                    }
                });
    }
}
