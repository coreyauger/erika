package erikas.bits

import argonaut.Argonaut._
import argonaut.Json
import erikas.bits.Driver.handleFailedRequest
import erikas.bits.ResponseUtils._

class Session(driver: PhantomDriver, desiredCapabilities: Capabilities = Capabilities(),
              requiredCapabilities: Capabilities = Capabilities()) {

  var sessionId = ""
  var sessionUrl = ""

  def create(): Unit = {
    val response = driver.doPost("/session", RequestSession(desiredCapabilities, requiredCapabilities).asJson)
    handleFailedRequest("/session", response)

    sessionId = response.decode[SessionResponse].sessionId

    sessionUrl = s"/session/$sessionId"

    println(s"[INFO] creating new session with id: $sessionId")

  }

  def visitUrl(url: String) = {
    handleFailedRequest(sessionUrl, driver.doPost(s"$sessionUrl/url", RequestUrl(url).asJson))
    this
  }

  def findElement(by: By): String = {
    val asJson: Json = RequestFindElement(by.locatorStrategy, by.value).asJson
    println(asJson)
    val response = driver.doPost(s"$sessionUrl/element", asJson)
    handleFailedRequest(sessionUrl, response)
    println(response.entityAsString)

    val elementId = response.decode[ElementResponse].value.get("ELEMENT") match {
      case None => throw APIResponseError("oops")
      case Some(ele) => ele
    }

    elementId
    //    new WebElement(elementId, sessionId, sessionUrl, driver, this)
  }


}


object Session extends App {

  val session = new Session(Driver("127.0.0.1", 7878))
  session.create()
  session.visitUrl("http://www.southwark.gov.uk/doitonline")
  session.findElement(By.id("SearchSite"))

}
