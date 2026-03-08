package ai.rever.boss.app.browser

import ai.rever.boss.ipc.proto.Empty
import ai.rever.boss.ipc.proto.services.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.slf4j.LoggerFactory

/**
 * gRPC implementation of BrowserService.
 *
 * Phase 5 skeleton — logs method calls.
 * Full JxBrowser + FluckEngine integration added when this process moves to production.
 */
class BrowserServiceImpl : BrowserServiceGrpcKt.BrowserServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(BrowserServiceImpl::class.java)

    override suspend fun navigate(request: NavigateBrowserRequest): NavigateBrowserResponse {
        logger.info("navigate: url={}, windowId={}", request.url, request.windowId)
        return NavigateBrowserResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage("JxBrowser integration pending (Phase 5 skeleton)")
            .build()
    }

    override suspend fun executeJS(request: ExecuteJSRequest): ExecuteJSResponse {
        logger.debug("executeJS: windowId={}", request.windowId)
        return ExecuteJSResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage("JxBrowser integration pending (Phase 5 skeleton)")
            .build()
    }

    override fun onNavigationEvent(request: Empty): Flow<BrowserNavigationEvent> {
        logger.debug("onNavigationEvent: subscribing")
        return emptyFlow()
    }

    override suspend fun getFavicon(request: GetFaviconRequest): GetFaviconResponse {
        logger.debug("getFavicon: url={}", request.url)
        return GetFaviconResponse.newBuilder().build()
    }

    override suspend fun getPageInfo(request: Empty): PageInfoResponse {
        return PageInfoResponse.newBuilder().build()
    }

    override suspend fun goBack(request: Empty): Empty = Empty.getDefaultInstance()
    override suspend fun goForward(request: Empty): Empty = Empty.getDefaultInstance()
    override suspend fun reload(request: Empty): Empty = Empty.getDefaultInstance()
}
