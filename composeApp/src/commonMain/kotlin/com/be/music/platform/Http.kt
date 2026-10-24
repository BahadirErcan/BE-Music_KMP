package com.be.music.platform

/** Platform-bağımsız basit HTTP istemcisi (Ktor: OkHttp engine Android/JVM, Darwin iOS). */
expect object SimpleHttp {
    /** GET; timeout ms; hata/başarısızlık durumunda null */
    suspend fun get(url: String, timeoutMs: Long = 10000): String?

    /** POST body xml/json metni; sonuç olarak yanıt gövdesi string */
    suspend fun post(url: String, body: String, contentType: String = "application/json", timeoutMs: Long = 15000): String?

    /** URL için HTTP GET; 30x davranışını otomatik takip eder; başarısızsa null */
    suspend fun getWithFollowRedirects(url: String, timeoutMs: Long = 15000): String?
}
