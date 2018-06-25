package com.woocommerce.android.ui.orders

import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.util.WooLog.T
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.action.WCOrderAction.FETCH_ORDERS
import org.wordpress.android.fluxc.action.WCOrderAction.UPDATE_ORDER_STATUS
import org.wordpress.android.fluxc.generated.WCOrderActionBuilder
import org.wordpress.android.fluxc.model.WCOrderModel
import org.wordpress.android.fluxc.store.WCOrderStore
import org.wordpress.android.fluxc.store.WCOrderStore.FetchOrdersPayload
import org.wordpress.android.fluxc.store.WCOrderStore.OnOrderChanged
import javax.inject.Inject

class OrderListPresenter @Inject constructor(
    private val dispatcher: Dispatcher,
    private val orderStore: WCOrderStore,
    private val selectedSite: SelectedSite
) : OrderListContract.Presenter {
    companion object {
        private val TAG: String = OrderListPresenter::class.java.simpleName
    }

    private var orderView: OrderListContract.View? = null
    private var isLoadingOrders = false
    private var isLoadingMoreOrders = false
    private var canLoadMore = false

    override fun takeView(view: OrderListContract.View) {
        orderView = view
        dispatcher.register(this)
    }

    override fun dropView() {
        orderView = null
        dispatcher.unregister(this)
    }

    override fun loadOrders(forceRefresh: Boolean) {
        orderView?.setLoadingIndicator(active = true)

        if (forceRefresh) {
            isLoadingOrders = true
            val payload = FetchOrdersPayload(selectedSite.get())
            dispatcher.dispatch(WCOrderActionBuilder.newFetchOrdersAction(payload))
        } else {
            fetchAndLoadOrdersFromDb(clearExisting = false)
        }
    }

    override fun isLoading(): Boolean {
        return isLoadingOrders || isLoadingMoreOrders
    }

    override fun canLoadMore(): Boolean {
        return canLoadMore
    }

    override fun loadMoreOrders() {
        orderView?.setLoadingMoreIndicator(true)
        isLoadingMoreOrders = true
        val payload = FetchOrdersPayload(selectedSite.get())
        payload.loadMore = true
        dispatcher.dispatch(WCOrderActionBuilder.newFetchOrdersAction(payload))
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onOrderChanged(event: OnOrderChanged) {
        if (event.isError) {
            // TODO: Notify the user of the problem
            WooLog.e(T.ORDERS, "$TAG - Error fetching orders : ${event.error.message}")
        } else {
            when (event.causeOfChange) {
                FETCH_ORDERS -> {
                    canLoadMore = event.canLoadMore
                    val clearExisting = !isLoadingMoreOrders
                    fetchAndLoadOrdersFromDb(clearExisting)
                }
                // A child fragment made a change that requires a data refresh.
                UPDATE_ORDER_STATUS -> orderView?.refreshFragmentState()
                else -> {
                }
            }
        }

        if (event.causeOfChange == FETCH_ORDERS) {
            isLoadingOrders = false
            isLoadingMoreOrders = false
            orderView?.setLoadingIndicator(active = false)
            orderView?.setLoadingMoreIndicator(false)
        }
    }

    override fun openOrderDetail(order: WCOrderModel) {
        orderView?.openOrderDetail(order)
    }

    /**
     * Fetch orders from the local database.
     *
     * @param clearExisting True if existing orders should be cleared from the view
     */
    override fun fetchAndLoadOrdersFromDb(clearExisting: Boolean) {
        val orders = orderStore.getOrdersForSite(selectedSite.get())
        orderView?.let { view ->
            if (orders.count() > 0) {
                view.showOrders(orders, clearExisting)
            } else {
                view.showNoOrders()
            }
        }
    }
}
