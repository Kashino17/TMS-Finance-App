package com.tms.banking.data.remote

import com.tms.banking.data.remote.dto.AccountDto
import com.tms.banking.data.remote.dto.CategoryDto
import com.tms.banking.data.remote.dto.NotificationDto
import com.tms.banking.data.remote.dto.SyncStatusDto
import com.tms.banking.data.remote.dto.TransactionDto
import com.tms.banking.data.remote.dto.UpdateCategoryRequest
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface TmsApi {

    @GET("health")
    suspend fun health(): Response<Map<String, String>>

    @GET("api/accounts")
    suspend fun getAccounts(): List<AccountDto>

    @GET("api/transactions")
    suspend fun getTransactions(
        @Query("account_id") accountId: Int? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): List<TransactionDto>

    @PATCH("api/transactions/{id}")
    suspend fun updateTransaction(
        @Path("id") id: Int,
        @Body request: UpdateCategoryRequest
    ): TransactionDto

    @PUT("api/transactions/{id}")
    suspend fun editTransaction(
        @Path("id") id: Int,
        @Body request: Map<String, @JvmSuppressWildcards Any?>
    ): Response<TransactionDto>

    @HTTP(method = "DELETE", path = "api/transactions/{id}", hasBody = false)
    suspend fun deleteTransaction(
        @Path("id") id: Int
    ): Response<Map<String, String>>

    @GET("api/categories")
    suspend fun getCategories(): List<CategoryDto>

    @GET("api/sync/status")
    suspend fun getSyncStatus(): List<SyncStatusDto>

    @POST("api/sync/trigger")
    suspend fun triggerSync(): Response<Map<String, String>>

    @POST("api/sync/enbd")
    suspend fun syncEnbd(@Body credentials: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, String>>

    @GET("api/sync/enbd/status")
    suspend fun getEnbdSyncStatus(): Response<Map<String, String>>

    @POST("api/ai/test-key")
    suspend fun testKimiKey(@Body body: Map<String, String>): Response<Map<String, String>>

    @POST("api/ai/categorize")
    suspend fun aiCategorize(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, @JvmSuppressWildcards Any>>

    @GET("api/loans")
    suspend fun getLoans(): List<Map<String, @JvmSuppressWildcards Any?>>

    @POST("api/loans")
    suspend fun createLoan(@Body loan: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, @JvmSuppressWildcards Any>>

    @retrofit2.http.DELETE("api/loans/{id}")
    suspend fun deleteLoan(@Path("id") id: Int): Response<Map<String, String>>

    @POST("api/notifications")
    suspend fun postNotification(@Body notification: NotificationDto): Response<Map<String, String>>

    @GET("api/analytics/monthly-summary")
    suspend fun getMonthlySummary(): List<Map<String, @JvmSuppressWildcards Any?>>

    @GET("api/recurring")
    suspend fun getRecurring(): List<Map<String, @JvmSuppressWildcards Any?>>

    @GET("api/budgets")
    suspend fun getBudgets(): List<Map<String, @JvmSuppressWildcards Any?>>

    @POST("api/budgets")
    suspend fun createBudget(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, @JvmSuppressWildcards Any>>

    @HTTP(method = "DELETE", path = "api/budgets/{id}", hasBody = false)
    suspend fun deleteBudget(@Path("id") id: Int): Response<Map<String, String>>

    companion object {
        fun sanitizeUrl(input: String): String {
            var url = input.trim()
            if (url.isBlank()) return ""
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }
            if (!url.endsWith("/")) url = "$url/"
            return url
        }

        fun create(baseUrl: String): TmsApi {
            val url = sanitizeUrl(baseUrl)
            if (url.isBlank()) throw IllegalArgumentException("Empty URL")
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(url)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TmsApi::class.java)
        }
    }
}
