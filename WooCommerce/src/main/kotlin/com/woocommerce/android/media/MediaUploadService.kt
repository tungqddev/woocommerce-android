package com.woocommerce.android.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.JobIntentService
import com.woocommerce.android.JobServiceIds.JOB_UPLOAD_PRODUCT_MEDIA_SERVICE_ID
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.util.WooLog
import dagger.android.AndroidInjection
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.MediaActionBuilder
import org.wordpress.android.fluxc.generated.WCProductActionBuilder
import org.wordpress.android.fluxc.model.MediaModel
import org.wordpress.android.fluxc.store.MediaStore
import org.wordpress.android.fluxc.store.MediaStore.OnMediaUploaded
import org.wordpress.android.fluxc.store.MediaStore.UploadMediaPayload
import org.wordpress.android.fluxc.store.SiteStore
import org.wordpress.android.fluxc.store.WCProductStore
import org.wordpress.android.fluxc.store.WCProductStore.OnProductImagesChanged
import org.wordpress.android.fluxc.store.WCProductStore.UpdateProductImagesPayload
import java.util.concurrent.CountDownLatch
import javax.inject.Inject

/**
 * service which uploads photos to the WP media library
 */
class MediaUploadService : JobIntentService() {
    companion object {
        private const val KEY_PRODUCT_ID = "key_product_id"
        private const val KEY_LOCAL_MEDIA_URI = "key_media_uri"

        class OnProductMediaUploadEvent(
            var remoteProductId: Long,
            val isError: Boolean
        )

        fun uploadProductMedia(context: Context, productId: Long, localMediaUri: Uri) {
            val intent = Intent(context, MediaUploadService::class.java)
            intent.putExtra(KEY_PRODUCT_ID, productId)
            intent.putExtra(KEY_LOCAL_MEDIA_URI, localMediaUri)
            enqueueWork(
                    context,
                    MediaUploadService::class.java,
                    JOB_UPLOAD_PRODUCT_MEDIA_SERVICE_ID,
                    intent
            )
        }
    }

    @Inject lateinit var dispatcher: Dispatcher
    @Inject lateinit var siteStore: SiteStore
    @Inject lateinit var mediaStore: MediaStore
    @Inject lateinit var productStore: WCProductStore
    @Inject lateinit var selectedSite: SelectedSite

    private val doneSignal = CountDownLatch(1)

    // TODO: move to prefs?
    private val stripImageLocation = true
    private val optimizeImages = true
    private val maxImageSize = 2000
    private val imageQuality = 85

    override fun onCreate() {
        WooLog.i(WooLog.T.MEDIA, "media upload service > created")
        AndroidInjection.inject(this)
        dispatcher.register(this)
        super.onCreate()
    }

    override fun onDestroy() {
        WooLog.i(WooLog.T.MEDIA, "media upload service > destroyed")
        dispatcher.unregister(this)
        super.onDestroy()
    }

    override fun onHandleWork(intent: Intent) {
        WooLog.i(WooLog.T.MEDIA, "media upload service > onHandleWork")

        val productId = intent.getLongExtra(KEY_PRODUCT_ID, 0L)
        val localMediaUri = intent.getParcelableExtra<Uri>(KEY_LOCAL_MEDIA_URI)

        // TODO
        /*if (optimizeImages) {
            filename = ImageUtils.optimizeImage(this, filename, maxImageSize, imageQuality)
        }*/

        val media = MediaUploadUtils.mediaModelFromLocalUri(
                this,
                selectedSite.get().id,
                localMediaUri,
                mediaStore
        )

        // TODO: handle null media
        media?.let {
            it.postId = productId
            it.setUploadState(MediaModel.MediaUploadState.UPLOADING)
            dispatchUploadAction(it)
        }
    }

    override fun onStopCurrentWork(): Boolean {
        super.onStopCurrentWork()
        WooLog.i(WooLog.T.MEDIA, "media upload service > onStopCurrentWork")
        return true
    }

    private fun dispatchUploadAction(media: MediaModel) {
        val site = siteStore.getSiteByLocalId(media.localSiteId)
        val payload = UploadMediaPayload(site, media, stripImageLocation)
        dispatcher.dispatch(MediaActionBuilder.newUploadMediaAction(payload))
        doneSignal.await()
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMediaUploaded(event: OnMediaUploaded) {
        if (event.isError) {
            WooLog.w(
                    WooLog.T.MEDIA,
                    "MediaUploadService > error uploading media: ${event.error.type}, ${event.error.message}"
            )
            val remoteProductId = event.media?.postId ?: 0L
            dispatchFailure(remoteProductId)
            doneSignal.countDown()
        } else {
            WooLog.i(WooLog.T.MEDIA, "MediaUploadService > uploaded media ${event.media?.id}")
            // media has been uploaded to assign it to the product
            dispatchEditProductAction(event.media)
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProductImagesChanged(event: OnProductImagesChanged) {
        val remoteProductId = event.product?.remoteProductId ?: 0L
        if (event.isError) {
            // TODO handle "Error getting remote image . Error: Invalid URL Provided."
            WooLog.w(
                    WooLog.T.MEDIA,
                    "MediaUploadService > error changing product images: ${event.error.type}, ${event.error.message}"
            )
            dispatchFailure(remoteProductId)
        } else {
            WooLog.i(WooLog.T.MEDIA, "MediaUploadService > product images changed")
            dispatchSuccess(remoteProductId)
        }

        doneSignal.countDown()
    }

    /**
     * Called after media has been uploaded to dispatch a request to assign the uploaded media
     * to the product
     */
    private fun dispatchEditProductAction(media: MediaModel) {
        val product = productStore.getProductByRemoteId(selectedSite.get(), media.postId)
        if (product == null) {
            WooLog.i(WooLog.T.MEDIA, "MediaUploadService > product is null")
            dispatchFailure(media.postId)
            doneSignal.countDown()
        } else {
            val mediaList = ArrayList<MediaModel>().also { it.add(media) }
            val site = siteStore.getSiteByLocalId(media.localSiteId)
            val payload = UpdateProductImagesPayload(site, media.postId, mediaList)
            dispatcher.dispatch(WCProductActionBuilder.newUpdateProductImagesAction(payload))
        }
    }

    private fun dispatchSuccess(remoteProductId: Long) {
        EventBus.getDefault().post(OnProductMediaUploadEvent(remoteProductId, true))
    }

    private fun dispatchFailure(remoteProductId: Long) {
        EventBus.getDefault().post(OnProductMediaUploadEvent(remoteProductId, true))    }
}
