package com.be.music.platform

import okio.Path
import okio.Path.Companion.toPath

/** DataStore dosyalarının tam yolu (okio.Path). Android'de filesDir/datastore altını koruruz. */
expect fun datastorePath(name: String): Path
