package ru.pumpsms.net

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import ru.pumpsms.Config
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

data class ResponseData<T>(
    @SerializedName("data") val data: T?,
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String?,
    @SerializedName("errors") val errors: List<Any>?
)

data class SmsOutbox(
    @SerializedName("id") val id: Int,
    @SerializedName("phone") val phone: String,
    @SerializedName("message_text") val messageText: String,
    @SerializedName("status") val status: String?
)

data class MarkFailedBody(
    @SerializedName("error_code") val errorCode: String?,
    @SerializedName("error_reason") val errorReason: String?
)

interface OutboxService {
    @GET("sms/outbox")
    suspend fun getOutbox(@Query("sender_phone") senderPhone: String): ResponseData<SmsOutbox>

    @POST("sms/outbox/{id}/delivered")
    suspend fun markDelivered(@Path("id") id: Int): ResponseData<SmsOutbox>

    @POST("sms/outbox/{id}/failed")
    suspend fun markFailed(@Path("id") id: Int, @Body body: MarkFailedBody): ResponseData<SmsOutbox>
}

object OutboxApi {
    fun create(baseUrl: String = Config.BASE_URL): OutboxService {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OutboxService::class.java)
    }
}
