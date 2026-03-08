package ai.rever.boss.service.auth

import ai.rever.boss.ipc.proto.Empty
import ai.rever.boss.ipc.proto.services.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

/**
 * gRPC implementation of the AuthService.
 *
 * This is the service-side implementation that runs in the auth service process.
 * It manages authentication state and provides it to the kernel and other processes.
 *
 * In Phase 2, this initially wraps the same logic as AuthStateManager/SessionManager
 * from composeApp, but running in its own process.
 */
class AuthServiceGrpcImpl : AuthServiceGrpcKt.AuthServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(AuthServiceGrpcImpl::class.java)

    // Auth state (mirrors what AuthStateManager does in monolith mode)
    private val authState = MutableStateFlow(AuthState.AUTH_STATE_NOT_AUTHENTICATED)
    private val currentUser = MutableStateFlow<UserInfo?>(null)
    private val userPermissions = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun getAuthState(request: Empty): AuthStateResponse {
        return AuthStateResponse.newBuilder()
            .setState(authState.value)
            .apply { currentUser.value?.let { setUser(it) } }
            .build()
    }

    override fun watchAuthState(request: Empty): Flow<AuthStateResponse> = flow {
        // Emit current state first
        emit(
            AuthStateResponse.newBuilder()
                .setState(authState.value)
                .apply { currentUser.value?.let { setUser(it) } }
                .build()
        )
        // Then stream changes
        authState.collect { state ->
            emit(
                AuthStateResponse.newBuilder()
                    .setState(state)
                    .apply { currentUser.value?.let { setUser(it) } }
                    .build()
            )
        }
    }

    override suspend fun signIn(request: SignInRequest): SignInResponse {
        logger.info("Sign-in attempt for method: {}", request.authMethod)

        // TODO: Integrate with Supabase auth client
        // For now, return a placeholder response
        return SignInResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage("Auth service Supabase integration pending")
            .build()
    }

    override suspend fun signOut(request: Empty): SignOutResponse {
        logger.info("Sign-out requested")

        authState.value = AuthState.AUTH_STATE_NOT_AUTHENTICATED
        currentUser.value = null
        userPermissions.value = emptySet()

        return SignOutResponse.newBuilder()
            .setSuccess(true)
            .build()
    }

    override suspend fun getCurrentUser(request: Empty): UserInfoResponse {
        val user = currentUser.value
        return UserInfoResponse.newBuilder()
            .setAuthenticated(user != null)
            .apply { user?.let { setUser(it) } }
            .build()
    }

    override fun watchCurrentUser(request: Empty): Flow<UserInfoResponse> = flow {
        // Emit current value
        emit(
            UserInfoResponse.newBuilder()
                .setAuthenticated(currentUser.value != null)
                .apply { currentUser.value?.let { setUser(it) } }
                .build()
        )
        // Stream changes
        currentUser.collect { user ->
            emit(
                UserInfoResponse.newBuilder()
                    .setAuthenticated(user != null)
                    .apply { user?.let { setUser(it) } }
                    .build()
            )
        }
    }

    override suspend fun hasPermission(request: PermissionRequest): PermissionResponse {
        return PermissionResponse.newBuilder()
            .setGranted(request.permission in userPermissions.value)
            .build()
    }

    override suspend fun hasAnyPermission(request: HasAnyPermissionRequest): PermissionResponse {
        val granted = request.permissionsList.any { it in userPermissions.value }
        return PermissionResponse.newBuilder()
            .setGranted(granted)
            .build()
    }

    override suspend fun getUserPermissions(request: Empty): UserPermissionsResponse {
        return UserPermissionsResponse.newBuilder()
            .addAllPermissions(userPermissions.value)
            .build()
    }

    override suspend fun isAdmin(request: Empty): IsAdminResponse {
        return IsAdminResponse.newBuilder()
            .setIsAdmin(currentUser.value?.isAdmin ?: false)
            .build()
    }

    /**
     * Update auth state programmatically (called during session restore, etc.)
     */
    fun updateAuthState(state: AuthState, user: UserInfo?, permissions: Set<String>) {
        authState.value = state
        currentUser.value = user
        userPermissions.value = permissions
    }
}
