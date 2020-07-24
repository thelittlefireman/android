/*
 *
 * Nextcloud Android client application
 *
 * @author Tobias Kaminsky
 * Copyright (C) 2020 Tobias Kaminsky
 * Copyright (C) 2020 Nextcloud GmbH
 * Copyright (C) 2020 Chris Narkiewicz <hello@ezaquarii.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.owncloud.android.ui.fragment

import android.Manifest
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.rule.GrantPermissionRule
import com.facebook.testing.screenshot.Screenshot
import com.nextcloud.client.TestActivity
import com.owncloud.android.AbstractIT
import com.owncloud.android.utils.ScreenshotTest
import org.junit.Rule
import org.junit.Test

class OCFileListFragmentStaticServerIT : AbstractIT() {
    @get:Rule
    val testActivityRule = IntentsTestRule(TestActivity::class.java, true, false)

    @get:Rule
    val permissionRule = GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    @Test
    @ScreenshotTest
    fun showOneFile() {
        val sut = testActivityRule.launchActivity(null)
        sut.addFragment(OCFileListFragment())

        val fragment = (sut.fragment as OCFileListFragment)
        val root = sut.storage.getFileByEncryptedRemotePath("/")

        shortSleep()

        sut.runOnUiThread { fragment.listDirectory(root, false, false) }

        waitForIdleSync()

        Screenshot.snapActivity(sut).record()
    }
}
