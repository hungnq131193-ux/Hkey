package com.hkey.app.update

import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppUpdaterTest {

    private fun relJson(
        tag: String = "v9.9.9", draft: Boolean? = false, prerelease: Boolean? = false,
        name: String = "HKey-9.9.9.apk",
        url: String = "https://github.com/hungnq131193-ux/Hkey/releases/download/v9.9.9/HKey-9.9.9.apk",
        htmlUrl: String? = "https://github.com/hungnq131193-ux/Hkey/releases/tag/v9.9.9",
        size: Long = 12345, digest: String? = null
    ): String {
        val d = if (digest == null) "" else ",\"digest\":\"$digest\""
        val dr = if (draft == null) "" else "\"draft\":$draft,"
        val pr = if (prerelease == null) "" else "\"prerelease\":$prerelease,"
        val hu = if (htmlUrl == null) "" else "\"html_url\":\"$htmlUrl\","
        return """{${hu}"tag_name":"$tag",${dr}${pr}
            "assets":[{"name":"$name","browser_download_url":"$url","size":$size$d}]}"""
    }

    @Test
    fun compareVersions_numeric() {
        assertTrue(AppUpdater.compareVersions("1.5.6", "1.5.6") == 0)
        assertTrue(AppUpdater.compareVersions("1.5.10", "1.5.6") > 0)
        assertTrue(AppUpdater.compareVersions("1.5.6", "1.5.10") < 0)
        assertTrue(AppUpdater.compareVersions("2.0.0", "1.9.9") > 0)
        assertTrue(AppUpdater.compareVersions("1.4.9", "1.5.0") < 0)
    }

    @Test
    fun parse_validRelease() {
        val r = AppUpdater.parseRelease(relJson())!!
        assertEquals("9.9.9", r.version)
        assertEquals(12345L, r.apkSize)
        assertNull(r.sha256)
    }

    @Test
    fun parse_rejectsBadTags() {
        for (t in listOf(
            "v1.5", "v1.5.6.7", "beta", "1.5.6", "vX.Y.Z", "v1.5.6-beta",
            "v1.5.99999999999"
        )) {
            assertNull("tag $t", AppUpdater.parseRelease(relJson(tag = t, htmlUrl = "https://github.com/hungnq131193-ux/Hkey/releases/tag/$t")))
        }
    }

    @Test
    fun parse_rejectsDraftPrerelease() {
        assertNull(AppUpdater.parseRelease(relJson(draft = true)))
        assertNull(AppUpdater.parseRelease(relJson(prerelease = true)))
        assertNull(AppUpdater.parseRelease(relJson(draft = null)))
        assertNull(AppUpdater.parseRelease(relJson(prerelease = null)))
        assertNull(AppUpdater.parseRelease(relJson(draft = null, prerelease = null)))
    }

    @Test
    fun parse_rejectsBadHtmlUrl() {
        assertNull(AppUpdater.parseRelease(relJson(htmlUrl = null)))
        assertNull(
            AppUpdater.parseRelease(
                relJson(htmlUrl = "https://github.com/hungnq131193-ux/Hkey/releases/tag/v9.9.8")
            )
        )
        assertNull(
            AppUpdater.parseRelease(
                relJson(htmlUrl = "https://github.com/hungnq131193-ux/Hkey/releases")
            )
        )
    }

    @Test
    fun parse_rejectsBadAsset() {
        assertNull(AppUpdater.parseRelease(relJson(name = "other.apk")))
        assertNull(AppUpdater.parseRelease(relJson(name = "HKey-9.9.8.apk")))
        val bad = listOf(
            "http://github.com/hungnq131193-ux/Hkey/releases/download/v9.9.9/HKey-9.9.9.apk",
            "https://evil.com/hungnq131193-ux/Hkey/releases/download/v9.9.9/HKey-9.9.9.apk",
            "https://github.com/other/repo/releases/download/v9.9.9/HKey-9.9.9.apk",
            "https://github.com/hungnq131193-ux/Hkey/releases/tag/v9.9.9",
            "https://github.com/hungnq131193-ux/Hkey/issues/1",
            "https://github.com/hungnq131193-ux/Hkey/releases/download/v9.9.8/HKey-9.9.9.apk",
            "https://github.com/hungnq131193-ux/Hkey/releases/download/v9.9.9/HKey-9.9.8.apk",
            "https://github.com/hungnq131193-ux/Hkey/../../x/HKey-9.9.9.apk",
            "https://user@github.com/hungnq131193-ux/Hkey/releases/download/v9.9.9/HKey-9.9.9.apk",
            "https://github.com:8443/hungnq131193-ux/Hkey/releases/download/v9.9.9/HKey-9.9.9.apk",
            "https://github.com/hungnq131193-ux/Hkey/releases/download/v9.9.9/HKey-9.9.9.apk?x=1",
            "https://github.com/hungnq131193-ux/Hkey/releases/download/v9.9.9/HKey-9.9.9.apk#f"
        )
        for (u in bad) assertNull(u, AppUpdater.parseRelease(relJson(url = u)))
        assertNull(AppUpdater.parseRelease(relJson(size = 0)))
        assertNull(AppUpdater.parseRelease(relJson(size = -5)))
        assertNull(AppUpdater.parseRelease(relJson(size = 300L * 1024 * 1024)))
    }

    @Test
    fun parse_digest() {
        val good = "a".repeat(64)
        val r = AppUpdater.parseRelease(relJson(digest = "sha256:$good"))!!
        assertEquals(good, r.sha256)
        assertNull(AppUpdater.parseRelease(relJson(digest = "md5:$good")))
        assertNull(AppUpdater.parseRelease(relJson(digest = "sha256:xyz")))
    }

    private fun dlDest(): File =
        File(RuntimeEnvironment.getApplication().cacheDir, "updates/HKey-9.9.9.apk")

    @Test
    fun download_successWithDigest() {
        runBlocking {
            val data = ByteArray(50_000) { (it % 251).toByte() }
            val sha = MessageDigest.getInstance("SHA-256").digest(data)
                .joinToString("") { "%02x".format(it) }
            val f = AppUpdater.download(
                "https://github.com/hungnq131193-ux/Hkey/releases/download/v9.9.9/HKey-9.9.9.apk",
                data.size.toLong(), sha, dlDest(),
                { ByteArrayInputStream(data) }
            )
            assertTrue(f.exists())
            assertEquals(data.size.toLong(), f.length())
            f.delete()
        }
    }

    @Test
    fun download_rejectsShortExcessDigestNet() {
        runBlocking {
            val data = ByteArray(100) { 7 }
            val dest = dlDest()
            val short = runCatching {
                AppUpdater.download(dest = dest,
                    url = "https://github.com/hungnq131193-ux/Hkey/releases/download/v9.9.9/HKey-9.9.9.apk",
                    expectedSize = 200, sha256 = null,
                    openStream = { ByteArrayInputStream(data) })
            }
            assertTrue(short.isFailure)
            val excess = runCatching {
                AppUpdater.download(dest = dest,
                    url = "https://github.com/hungnq131193-ux/Hkey/releases/download/v9.9.9/HKey-9.9.9.apk",
                    expectedSize = 50, sha256 = null,
                    openStream = { ByteArrayInputStream(data) })
            }
            assertTrue(excess.isFailure)
            val badsha = runCatching {
                AppUpdater.download(dest = dest,
                    url = "https://github.com/hungnq131193-ux/Hkey/releases/download/v9.9.9/HKey-9.9.9.apk",
                    expectedSize = 100, sha256 = "0".repeat(64),
                    openStream = { ByteArrayInputStream(data) })
            }
            assertTrue(badsha.isFailure)
            val net = runCatching {
                AppUpdater.download(dest = dest,
                    url = "https://github.com/hungnq131193-ux/Hkey/releases/download/v9.9.9/HKey-9.9.9.apk",
                    expectedSize = 100, sha256 = null,
                    openStream = { throw java.io.IOException("offline") })
            }
            assertTrue(net.isFailure)
            assertFalse(dest.exists())
            assertFalse(File(dest.parentFile, dest.name + ".part").exists())
            val badUrl = runCatching {
                AppUpdater.download(dest = dest, url = "https://evil.com/x.apk",
                    expectedSize = 100, sha256 = null,
                    openStream = { ByteArrayInputStream(data) })
            }
            assertTrue(badUrl.isFailure)
        }
    }

    @Test
    fun download_cancelRemovesPartial() {
        runBlocking {
            val started = CountDownLatch(1)
            val gate = CountDownLatch(1)
            val blocking = object : InputStream() {
                override fun read(): Int {
                    started.countDown()
                    gate.await(30, TimeUnit.SECONDS)
                    return -1
                }
                override fun read(b: ByteArray, off: Int, len: Int): Int = read()
            }
            val dest = dlDest()
            val part = File(dest.parentFile, dest.name + ".part")
            val result = java.util.concurrent.atomic.AtomicReference<Result<File>?>()
            val job = launch(Dispatchers.IO) {
                result.set(
                    runCatching {
                        AppUpdater.download(dest = dest,
                            url = "https://github.com/hungnq131193-ux/Hkey/releases/download/v9.9.9/HKey-9.9.9.apk",
                            expectedSize = 100, sha256 = null,
                            openStream = { blocking })
                    }
                )
            }
            assertTrue(started.await(30, TimeUnit.SECONDS))
            job.cancel()
            gate.countDown()
            job.join()
            assertTrue(result.get()!!.isFailure)
            assertFalse(part.exists())
            assertFalse(dest.exists())
        }
    }

    private fun settle(vm: UpdateViewModel) {
        val looper = Shadows.shadowOf(android.os.Looper.getMainLooper())
        var i = 0
        while (i++ < 500) {
            looper.idle()
            val p = vm.state.value.phase
            if (p != UpdateViewModel.Phase.CHECKING &&
                p != UpdateViewModel.Phase.DOWNLOADING
            ) return
            Thread.sleep(5)
        }
    }

    private fun vm(
        fetchJson: (String) -> String = { relJson() },
        openStream: StreamOpener = { ByteArrayInputStream(ByteArray(12345) { 1 }) }
    ) = UpdateViewModel(
        RuntimeEnvironment.getApplication(),
        fetchJson = fetchJson, openStream = openStream
    )

    @Test
    fun vm_fetchRunsOffMain_andConcurrentChecksDedup() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var fetches = 0
        var onIo = false
        val vm = UpdateViewModel(
            RuntimeEnvironment.getApplication(),
            fetchJson = {
                fetches++
                onIo = Thread.currentThread() !=
                    android.os.Looper.getMainLooper().thread
                started.countDown()
                release.await(30, TimeUnit.SECONDS)
                relJson()
            }
        )
        vm.check(); vm.check(); vm.check()
        assertTrue(started.await(30, TimeUnit.SECONDS))
        release.countDown()
        settle(vm)
        assertEquals(1, fetches)
        assertTrue(onIo)
        assertEquals(UpdateViewModel.Phase.AVAILABLE, vm.state.value.phase)
    }

    @Test
    fun vm_defaultFactoryConstructs() {
        val app = RuntimeEnvironment.getApplication()
        val vm = androidx.lifecycle.ViewModelProvider
            .AndroidViewModelFactory.getInstance(app)
            .create(UpdateViewModel::class.java)
        assertNotNull(vm)
        vm.viewModelScope.cancel()
    }

    @Test
    fun vm_repeatedDownloadSingle() {
        var opens = 0
        val vm = vm(openStream = { opens++; ByteArrayInputStream(ByteArray(12345) { 1 }) })
        settle(vm)
        vm.download(); vm.download()
        settle(vm)
        assertEquals(1, opens)
        val st = vm.state.value
        assertNotNull(st.apk)
        assertEquals(UpdateViewModel.Phase.READY, st.phase)
        st.apk?.delete()
    }

    @Test
    fun vm_readyBlocksRecheck_andInstallNoPermissionKeepsApk() {
        val app = RuntimeEnvironment.getApplication()
        var fetches = 0
        val vm = UpdateViewModel(
            app,
            fetchJson = { fetches++; relJson() },
            openStream = { ByteArrayInputStream(ByteArray(12345) { 1 }) }
        )
        settle(vm)
        vm.download()
        settle(vm)
        val apk = vm.state.value.apk!!
        vm.check()
        settle(vm)
        assertEquals(1, fetches)
        assertEquals(UpdateViewModel.Phase.READY, vm.state.value.phase)
        Shadows.shadowOf(app.packageManager).setCanRequestPackageInstalls(false)
        vm.install()
        val intent = Shadows.shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent?.action)
        assertTrue(apk.exists())
        assertTrue(vm.state.value.needPermission)
        Shadows.shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
        vm.install()
        val i2 = Shadows.shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, i2?.action)
        apk.delete()
    }

    @Test
    fun vm_confirmHandler_dismissesAndNavigates() {
        val vm = vm()
        settle(vm)
        assertEquals(UpdateViewModel.Phase.AVAILABLE, vm.state.value.phase)
        var went = false
        com.hkey.app.settings.ui.sections.onUpdateConfirm(vm) { went = true }
        assertTrue(went)
        assertTrue(vm.state.value.dismissed)
    }
}
