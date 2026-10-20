package hr.divanguz.homepanel.updates

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdaterTest {
    @Test fun readsVersionAndApkAsset() {
        val r = Updater.parseRelease(
            Json.parseToJsonElement(
                """{"tag_name":"v1.12","assets":[
                {"name":"notes.txt","url":"https://api.github.com/x/1","size":10},
                {"name":"homepanel-1.12.apk","url":"https://api.github.com/x/2","size":25000000}]}""",
            ) as JsonObject,
        )
        assertEquals(112, r.versionCode)
        assertEquals("1.12", r.name)
        assertEquals("https://api.github.com/x/2", r.assetUrl)
        assertEquals(25_000_000L, r.size)
    }
}
