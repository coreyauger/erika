package net.kenro.ji.jin

import argonaut.Argonaut._
import org.scalatest.{FreeSpec, Matchers}

class ModelSpec extends FreeSpec with Matchers {

  "Model" - {

    "Capabilities should not include optional fields set to None" in {
      val toEncode = Capabilities(browserStack = Some(BrowserStackCapabilities(name = Some("name"))))
      val expected = """{"name":"name","browserConnectionEnabled":true,"databaseEnabled":true,"nativeEvents":true,"webStorageEnabled":true,"takesScreenshot":true,"applicationCacheEnabled":true,"locationContextEnabled":true,"acceptSslCerts":true,"browserName":"phantomjs","version":"phantomjs","rotatable":true,"javascriptEnabled":true,"handlesAlerts":true,"platform":"MAC","cssSelectorsEnabled":true}"""
      toEncode.asJson.toString() should be(expected)
    }

    "Capabilities should decode browserstack capabilities correctly when there are None" in {
      val toDecode = """{"browserConnectionEnabled":true,"databaseEnabled":true,"nativeEvents":true,"webStorageEnabled":true,"takesScreenshot":true,"applicationCacheEnabled":true,"locationContextEnabled":true,"acceptSslCerts":true,"browserName":"phantomjs","version":"phantomjs","rotatable":true,"javascriptEnabled":true,"handlesAlerts":true,"platform":"MAC","cssSelectorsEnabled":true}"""
      toDecode.decode[Capabilities].right.toOption should be(Some(Capabilities()))
    }

    "Capabilities should decode browserstack capabilities correctly when there are Some" in {
      val toDecode = """{"name":"name","browserConnectionEnabled":true,"databaseEnabled":true,"nativeEvents":true,"webStorageEnabled":true,"takesScreenshot":true,"applicationCacheEnabled":true,"locationContextEnabled":true,"acceptSslCerts":true,"browserName":"phantomjs","version":"phantomjs","rotatable":true,"javascriptEnabled":true,"handlesAlerts":true,"platform":"MAC","cssSelectorsEnabled":true}"""
      toDecode.decode[Capabilities].right.toOption should be(Some(Capabilities(browserStack = Some(BrowserStackCapabilities(name = Some("name"))))))
    }

  }

}