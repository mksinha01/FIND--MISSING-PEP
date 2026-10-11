package com.findmissingpep.app.data.api

import com.findmissingpep.app.domain.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that attaches the Firebase Bearer token
 * to every outgoing request. Lazily retrieves the stored token
 * to avoid circular DI dependency with AuthRepository.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for public endpoints
        val url = originalRequest.url.toString()
        if (url.contains("/api/events/stream")) {
            // SSE uses query param auth instead
            return chain.proceed(originalRequest)
        }

        val token = runBlocking { tokenProvider.getToken() }

        return if (token != null) {
            val authedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(authedRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
