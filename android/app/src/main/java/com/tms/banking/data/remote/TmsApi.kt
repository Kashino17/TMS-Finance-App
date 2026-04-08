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
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
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

    @GET("api/categories")
    suspend fun getCategories(): List<CategoryDto>

    @GET("api/sync/status")
    suspend fun getSyncStatus(): List<SyncStatusDto>

    @POST("api/sync/trigger")
    suspend fun triggerSync(): Response<Map<String, String>>

    @POST("api/notifications")
    suspend fun postNotification(@Body notification: NotificationDto): Response<Map<String, String>>

    companion object {
        fun create(baseUrl: String): TmsApi {
            val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
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
