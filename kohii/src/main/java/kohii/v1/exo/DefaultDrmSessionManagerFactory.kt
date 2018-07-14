/*
 * Copyright (c) 2018 Nam Nguyen, nam@ene.im
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kohii.v1.exo

import androidx.annotation.RequiresApi
import android.text.TextUtils
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager
import com.google.android.exoplayer2.drm.DrmSessionManager
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto
import com.google.android.exoplayer2.drm.FrameworkMediaDrm
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback
import com.google.android.exoplayer2.drm.UnsupportedDrmException
import com.google.android.exoplayer2.drm.UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.util.Util.getDrmUuid
import kohii.media.MediaDrm
import kohii.v1.R
import java.util.Comparator
import java.util.TreeMap
import java.util.UUID

/**
 * @author eneim (2018/06/25).
 */
class DefaultDrmSessionManagerFactory internal constructor(
    private val store: ExoStore) : DrmSessionManagerFactory {

  private val factory = DefaultHttpDataSourceFactory(store.appName) as HttpDataSource.Factory

  override fun createDrmSessionManager(
      mediaDrm: MediaDrm): DrmSessionManager<FrameworkMediaCrypto> {
    var drmSessionManager: DrmSessionManager<FrameworkMediaCrypto>? = cache[mediaDrm]
    if (drmSessionManager != null) return drmSessionManager

    var errorStringId = R.string.error_drm_unknown
    var subString: String? = null
    if (Util.SDK_INT < 18) {
      errorStringId = R.string.error_drm_not_supported
    } else {
      val drmSchemeUuid = getDrmUuid(mediaDrm.type)
      if (drmSchemeUuid == null) {
        errorStringId = R.string.error_drm_unsupported_scheme
      } else {
        try {
          drmSessionManager = buildDrmSessionManagerV18(drmSchemeUuid, mediaDrm.licenseUrl,
              mediaDrm.keyRequestPropertiesArray, mediaDrm.multiSession(), factory)
        } catch (e: UnsupportedDrmException) {
          e.printStackTrace()
          errorStringId = if (e.reason == REASON_UNSUPPORTED_SCHEME)
            R.string.error_drm_unsupported_scheme
          else
            R.string.error_drm_unknown
          if (e.reason == REASON_UNSUPPORTED_SCHEME) {
            subString = mediaDrm.type
          }
        }

      }
    }

    if (drmSessionManager == null) {
      val error =
          if (TextUtils.isEmpty(subString))
            store.context.getString(errorStringId)
          else
            store.context.getString(errorStringId) + ": " + subString
      Toast.makeText(store.context, error, LENGTH_SHORT).show()
    }

    return drmSessionManager!!.also { cache[mediaDrm] = it }
  }

  companion object {

    private val cache = TreeMap<MediaDrm, DrmSessionManager<FrameworkMediaCrypto>>(
        Comparator { o1, o2 -> o1.compareTo(o2) })

    @RequiresApi(18)
    @Throws(UnsupportedDrmException::class)
    private fun buildDrmSessionManagerV18(
        uuid: UUID,
        licenseUrl: String?,
        keyRequestPropertiesArray: Array<String>?,
        multiSession: Boolean,
        httpDataSourceFactory: HttpDataSource.Factory): DrmSessionManager<FrameworkMediaCrypto> {
      val drmCallback = HttpMediaDrmCallback(licenseUrl, httpDataSourceFactory)
      if (keyRequestPropertiesArray != null) {
        var i = 0
        while (i < keyRequestPropertiesArray.size - 1) {
          drmCallback.setKeyRequestProperty(
              keyRequestPropertiesArray[i],
              keyRequestPropertiesArray[i + 1]
          )
          i += 2
        }
      }

      return DefaultDrmSessionManager(
          uuid,
          FrameworkMediaDrm.newInstance(uuid),
          drmCallback,
          null,
          multiSession
      )
    }
  }
}
