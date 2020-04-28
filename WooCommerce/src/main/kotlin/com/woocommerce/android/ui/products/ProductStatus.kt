package com.woocommerce.android.ui.products

import android.content.Context
import androidx.annotation.StringRes
import com.woocommerce.android.R
import org.wordpress.android.fluxc.network.rest.wpcom.wc.product.CoreProductStatus
import java.util.Locale

/**
 * Similar to PostStatus except only draft, pending, private, and publish are supported
 */
enum class ProductStatus(@StringRes val stringResource: Int = 0, val value: String = "") {
    PUBLISH(R.string.product_status_published, CoreProductStatus.PUBLISH.value),
    DRAFT(R.string.product_status_draft, CoreProductStatus.DRAFT.value),
    PENDING(R.string.product_status_pending, CoreProductStatus.PENDING.value),
    PRIVATE(R.string.product_status_private, CoreProductStatus.PRIVATE.value);

    fun toLocalizedString(context: Context): String {
        @StringRes val resId = when (this) {
            PUBLISH -> R.string.product_status_published
            DRAFT -> R.string.product_status_draft
            PENDING -> R.string.product_status_pending
            PRIVATE -> R.string.product_status_private
        }
        return context.getString(resId)
    }

    /**
     * This ensures the status is passed as lowercase when updating a product in FluxC (passing
     * it as uppercase fails with "HTTP 400 Invalid parameter "status")
     */
    override fun toString(): String {
        return super.toString().toLowerCase(Locale.US)
    }

    companion object {
        fun fromString(status: String): ProductStatus? {
            val statusLC = status.toLowerCase(Locale.US)
            values().forEach { value ->
                if (value.toString().toLowerCase(Locale.US) == statusLC) return value
            }
            return null
        }
    }
}
