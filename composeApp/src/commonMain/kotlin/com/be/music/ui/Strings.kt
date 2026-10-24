package com.be.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Compose olmayan yerlerde (ViewModel vb.) kaynak metni almak için suspend yardımcı. */
suspend fun getStringResource(res: StringResource): String = getString(res)

/**
 * ViewModel gibi suspend olmayan bağlamlarda string lazım olduğunda:
 * Composable ağacı ilk oluştuğunda ilgili anahtarlar ısınır; sonrasında senkron okunabilir.
 */
object AppStrings {
    val cache = androidx.compose.runtime.mutableStateMapOf<StringResource, String>()

    @Composable
    fun warm(vararg keys: StringResource) {
        keys.forEach { k ->
            if (!cache.containsKey(k)) cache[k] = stringResource(k)
        }
    }

    fun get(res: StringResource): String = cache[res] ?: ""
}

/** Composable içinde kullanım (kısayol, orijinal stringResource ile aynı davranış). */
@Composable
fun resString(res: StringResource): String = stringResource(res)

@Composable
fun resString(res: StringResource, vararg args: Any): String = stringResource(res, *args)
