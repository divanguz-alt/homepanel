package hr.divanguz.homepanel.weather

import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/** Where XML parsers come from: Android's on the phone; unit tests swap in a JVM one (kxml2). */
internal object XmlParsers {
    var factory: () -> XmlPullParser = { Xml.newPullParser() }
}
