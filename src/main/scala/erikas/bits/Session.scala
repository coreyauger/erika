package erikas.bits

import java.io.{IOException, File, FileOutputStream}

import argonaut.Argonaut._
import argonaut.Json
import erikas.bits.Driver.handleRequest
import erikas.bits.ResponseUtils._
import sun.misc.BASE64Decoder

import scala.sys.process.Process

class Session(driver: BaseDriver, desiredCapabilities: Capabilities = Capabilities(),
              requiredCapabilities: Capabilities = Capabilities()) {

  var sessionId = ""
  var sessionUrl = ""
  var globalTimeout = 5000

  def create(): Unit = {

    println(SessionRequest(desiredCapabilities, requiredCapabilities).asJson)

    sessionId = handleRequest("/session", driver.doPost("/session", SessionRequest(desiredCapabilities, requiredCapabilities).asJson))
      .response.decode[CreateSessionResponse].sessionId



    sessionUrl = s"/session/$sessionId"
    println(s"[INFO] creating new session with id: $sessionId")
  }

  def setGlobalTimeout(timeout: Int) = globalTimeout = timeout

  def getGlobalTimeout = globalTimeout

  def visitUrl(url: String) = {
    handleRequest(sessionUrl, driver.doPost(s"$sessionUrl/url", UrlRequest(url).asJson))
    this
  }

  def findElement(by: By): WebElement = {
    val elementId = handleRequest(sessionUrl, driver.doPost(s"$sessionUrl/element", FindElementRequest(by.locatorStrategy, by.value).asJson))
      .response.decode[ElementResponse].value.get("ELEMENT") match {
      case None => throw APIResponseError("oops")
      case Some(ele) => ele
    }

    new WebElement(elementId, sessionId, sessionUrl, driver, this)
  }

  def findElements(by: By): List[WebElement] = {
   val elementIds = handleRequest(sessionUrl, driver.doPost(s"$sessionUrl/elements", FindElementRequest(by.locatorStrategy, by.value).asJson))
      .response.decode[ElementResponses].value.map(er => er.get("ELEMENT"))

    for {
      maybeElementId <- elementIds
      elementId      <- maybeElementId

    } yield new WebElement(elementId,sessionId, sessionUrl, driver, this)
  }

  def elementExists(by: By): Boolean = findElements(by).nonEmpty

  // why does this return the text "active" intead of an elementId?
  def getActiveElement: Option[String] = {
   handleRequest(sessionUrl, driver.doGet(s"$sessionUrl/element/active")).decode[ElementResponse].value.get("ELEMENT")
  }

  def getSessions: List[Sessions] = {
    val url = "/sessions"
    handleRequest(url, driver.doGet(url)).response.decode[SessionResponse].value
  }

  def getStatus: ServerStatus = {
    val url = "/status"
    handleRequest(url, driver.doGet(url)).decode[ServerStatusResponse].value
  }

  def getCapabilities: Capabilities = {
    handleRequest(sessionUrl, driver.doGet(sessionUrl)).decode[CapabilityResponse].value
  }

  def dispose() = handleRequest(sessionUrl, driver.doDelete(sessionUrl))

  def getWindowHandles: List[WindowHandle] = {
    handleRequest(sessionUrl, driver.doGet(s"$sessionUrl/window_handles")).decode[WindowHandlesResponse].value.map(h => WindowHandle(h))
  }

  def getWindowHandle: WindowHandle = {
    WindowHandle(handleRequest(sessionUrl, driver.doGet(s"$sessionUrl/window_handle")).decode[WindowHandleResponse].value)
  }

  def getUrl: Option[String] = handleRequest(sessionUrl, driver.doGet(s"$sessionUrl/url")).decode[StringResponse].value

  def goForward = handleRequest(sessionUrl, driver.doPost(s"$sessionUrl/forward", Json()))

  def goBack = handleRequest(sessionUrl, driver.doPost(s"$sessionUrl/back", Json()))

  def refresh = handleRequest(sessionUrl, driver.doPost(s"$sessionUrl/refresh", Json()))

  def getSource: Option[String] = handleRequest(sessionUrl, driver.doGet(s"$sessionUrl/source")).decode[StringResponse].value

  def getTitle: Option[String] = handleRequest(sessionUrl, driver.doGet(s"$sessionUrl/title")).decode[StringResponse].value

  def executeScript(script: String, args: List[String] = Nil) = {
    handleRequest(sessionUrl, driver.doPost(s"$sessionUrl/execute", ExecuteScriptRequest(script, args).asJson))
  }

  def executeAsyncScript(script: String, args: List[String] = Nil) = {
    handleRequest(sessionUrl, driver.doPost(s"$sessionUrl/execute_async", ExecuteScriptRequest(script, args).asJson))
  }

  def takeScreenshot(outputFile: String = "screenshot.png") = {
    handleRequest(sessionUrl, driver.doGet(s"$sessionUrl/screenshot")).decode[StringResponse].value.foreach { s =>
      val decoded = new BASE64Decoder().decodeBuffer(s)
      val decodedStream = new FileOutputStream(new File(outputFile))
      decodedStream.write(decoded)
      decodedStream.close()
    }
  }

  def setTimeout(timeoutType: TimeoutType.Value, milliseconds: Int) = {
    handleRequest(sessionUrl, driver.doPost(s"$sessionUrl/timeouts", TimeoutRequest(timeoutType, milliseconds).toJson()))
  }

  def setAsyncScriptTimeout(milliseconds: Int) = {
    handleRequest(sessionUrl, driver.doPost(s"$sessionUrl/timeouts/async_script", TimeoutValueRequest(milliseconds).asJson))
  }

  def setImplicitWaitTimeout(milliseconds: Int) = {
    handleRequest(sessionUrl, driver.doPost(s"$sessionUrl/timeouts/implicit_wait", TimeoutValueRequest(milliseconds).asJson))
  }

  def waitFor[T <: Searcher](element: T, condition: Condition, timeout: Int = getGlobalTimeout): WebElement = {
    Waitress(this).waitFor(element, condition, timeout)
  }

  def findFirst[T <: Searcher](element: T, condition: Condition, timeout: Int = getGlobalTimeout): Option[WebElement] = {
     Waitress(this).waitAndFindFirst(element, condition, timeout)
  }

  def findAll(by: By, condition: Condition, timeout: Int = getGlobalTimeout): List[WebElement] = {
      Waitress(this).waitAndFindAll(by, condition, timeout)
  }

  def waitForFunction(runnable: () => Result, timeout: Int = getGlobalTimeout){
    Waitress(this).waitFor(runnable, timeout)
  }

  def waitForUrl(expectedUrl: String, timeout: Int = getGlobalTimeout) = {
    val waitForUrlFunction: () => Result = () => {
      val actualUrl = this.getUrl.getOrElse("")
      val outcome: Boolean = expectedUrl == actualUrl
      if(outcome) Result(outcome, actualUrl) else Result(outcome, s"Error: expected url: $expectedUrl but got url: $actualUrl")
    }
    Waitress(this).waitFor(waitForUrlFunction, timeout)
  }

  def tryIt() = {
    handleRequest(sessionUrl, driver.doGet(s"$sessionUrl/cookie"))
  }

}

object Port {
  def freePort = {
    withPort(0)
  }

  def withPort(port: Int) = {
    val p = new java.net.ServerSocket(port)
    p.close()
    p.getLocalPort
  }

  import util.control.Breaks._
  def freePort4: Int = {
    var p = 1025
    breakable {
      (1025 to 9999).foreach(port => {
        try{
          p = withPort(port)
          break
        } catch {
          case ioe: IOException =>
        }
      })
    }
    p
  }

}

object PhantomJsSession {

  def apply(desiredCapabilities: Capabilities = Capabilities(),
            requiredCapabilities: Capabilities = Capabilities(),
            phantomJsOptions: PhantomJsOptions = PhantomJsOptions(),
            pathToPhantom: String = "/usr/local/bin/phantomjs",
            host: String = "127.0.0.1",
            port: Int = Port.freePort
           )(block: (Session) => Unit) = {



    val commands = s"$pathToPhantom --webdriver=$host:$port ${phantomJsOptions.getOptions}"

    println(commands)

    var process: Process = null

    Eventually(10000).tryExecute(() => {
      process = Process(commands).run()
    })

    val session = new Session(Driver(host,port),desiredCapabilities, requiredCapabilities)

    Eventually(10000).tryExecute(() => {
      session.create()
    })

    try {
      block(session)
    } finally {
      session.dispose()
      process.destroy()
    }

  }

}

object ChromeSession {

  def apply(desiredCapabilities: Capabilities = Capabilities(browserName = "chrome", proxy = Some(Proxy())),
            requiredCapabilities: Capabilities = Capabilities(browserName = "chrome", proxy = Some(Proxy())),
            seleniumServerOptions: SeleniumServerOptions = SeleniumServerOptions(),
            pathToSeleniumServerStandalone: String = "/usr/local/bin/",
            pathToChromeDriver: String = "/usr/local/bin/chromedriver",
            host: String = "127.0.0.1",
            port: Int = Port.freePort4,
            transport: String = "http://",
            serverSuffix: String = "/wd/hub",
            pathToJava: String = "java"
           )(block: (Session) => Unit) = {

    val commands = s"$pathToJava -jar $pathToSeleniumServerStandalone -port $port ${seleniumServerOptions.getOptions} -Dwebdriver.chrome.driver=$pathToChromeDriver"

    println(commands)

    var process: Process = null

    Eventually(10000).tryExecute(() => {
      process = Process(commands).run()
    })

    val session = new Session(Driver(host, port, serverSuffix, transport), desiredCapabilities, requiredCapabilities)

    Eventually(10000).tryExecute(() => {
      session.create()
    })

    try {
      block(session)
    } finally {
      session.dispose()
      process.destroy()
    }

  }

}

object FirefoxSession {

  def apply(desiredCapabilities: Capabilities = Capabilities(browserName = "firefox"),
            requiredCapabilities: Capabilities = Capabilities(browserName = "firefox"),
            seleniumServerOptions: SeleniumServerOptions = SeleniumServerOptions(),
            pathToSeleniumServerStandalone: String = "/usr/local/bin/",
            pathToFirefoxDriver: String = "/usr/local/bin/wires",
            host: String = "127.0.0.1",
            port: Int = Port.freePort4,
            transport: String = "http://",
            serverSuffix: String = "/wd/hub",
            pathToJava: String = "java"
           )(block: (Session) => Unit) = {

    val commands = s"$pathToJava -jar $pathToSeleniumServerStandalone -port $port ${seleniumServerOptions.getOptions} -Dwebdriver.gecko.driver=$pathToFirefoxDriver"

    println(commands)

    var process: Process = null

    Eventually(10000).tryExecute(() => {
      process = Process(commands).run()
    })

    val session = new Session(Driver(host, port, serverSuffix, transport), desiredCapabilities, requiredCapabilities)

    Eventually(10000).tryExecute(() => {
      session.create()
    })

    try {
      block(session)
    } finally {
      session.dispose()
      process.destroy()
    }

  }

}

// nativeEvents = true for IE
// resolution =

object BrowserStackSession {

  def apply(desiredCapabilities: Capabilities = Capabilities(browserName = "chrome"),
            requiredCapabilities: Capabilities = Capabilities(browserName = "chrome"),
            url: String = "http://#{ENV['BS_USERNAME']}:#{ENV['BS_AUTHKEY']}@hub-cloud.browserstack.com/wd/hub"
           )(block: (Session) => Unit) = {

    val session = new Session(new UrlDriver(url), desiredCapabilities, requiredCapabilities)

//    Eventually(10000).tryExecute(() => {
      session.create()
//    })

    try {
      block(session)
    } finally {
      session.dispose()
    }

  }

}

object Run extends App {

  val caps = Capabilities(
    name = Some("Kingsley"),
    browser = Some("Chrome"),
    browserVersion = Some("50"),
    os = Some("Windows"),
    osVersion = Some("10"),
    resolution = Some("1920x1080"),
    nativeEvents = true,
    project = Some("BOOST [CB]"),
    browserStackLocal = Some(false)

  )
  BrowserStackSession(url = "@hub-cloud.browserstack.com/wd/hub",
    desiredCapabilities = caps, requiredCapabilities = caps
  )(session => {

    session.setGlobalTimeout(20000)
    session.setTimeout(TimeoutType.IMPLICIT, 20000)
    session.setTimeout(TimeoutType.PAGE_LOAD, 20000)

//    session.visitUrl("http://www.google.co.uk")

//    session.waitFor(By.className("cb-username"), Condition.isClickable)
//      .toTextInput.setValue("matt.cully@barclays.com")
//      .waitFor(By.className("cb-password"), Condition.isClickable)
//      .toTextInput.setValue("Password!")
//      .waitFor(By.className("cb-login"), Condition.isClickable)
//      .toButton.click()

    println(session.getCapabilities)

  })

//  FirefoxSession(
//    pathToSeleniumServerStandalone = "/Users/hendrkin/Downloads/selenium-server-standalone-2.53.0.jar",
//    pathToFirefoxDriver = "/Users/hendrkin/Downloads/wires"
//  )(session => {
//
//    session.setGlobalTimeout(20000)
//    session.setTimeout(TimeoutType.IMPLICIT, 20000)
//    session.setTimeout(TimeoutType.PAGE_LOAD, 20000)
//
//    session.visitUrl("http://localhost:10270/cb/#login")
//
//    session.waitFor(By.className("cb-username"), Condition.isClickable)
//      .toTextInput.setValue("matt.cully@barclays.com")
//      .waitFor(By.className("cb-password"), Condition.isClickable)
//      .toTextInput.setValue("Password!")
//      .waitFor(By.className("cb-login"), Condition.isClickable)
//      .toButton.click()
//
//    println(session.getCapabilities)
//
//  })
//
//
//  ChromeSession(
//    pathToSeleniumServerStandalone = "/Users/hendrkin/Downloads/selenium-server-standalone-2.53.0.jar",
//    pathToChromeDriver = "/Users/hendrkin/Downloads/chromedriver"
//  )(session => {
//
//    session.setGlobalTimeout(20000)
//    session.setTimeout(TimeoutType.IMPLICIT, 20000)
//    session.setTimeout(TimeoutType.PAGE_LOAD, 20000)
//
//    session.visitUrl("http://localhost:10270/cb/#login")
//
//    session.waitFor(By.className("cb-username"), Condition.isClickable)
//      .toTextInput.setValue("matt.cully@barclays.com")
//      .waitFor(By.className("cb-password"), Condition.isClickable)
//      .toTextInput.setValue("Password!")
//      .waitFor(By.className("cb-login"), Condition.isClickable)
//      .toButton.click()
//
//    println(session.getCapabilities)
//
//  })

//
//  PhantomJsSession()(session => {
//
//    session.setGlobalTimeout(20000)
//    session.setTimeout(TimeoutType.IMPLICIT, 20000)
//    session.setTimeout(TimeoutType.PAGE_LOAD, 20000)
//
//    session.visitUrl("http://localhost:10270/cb/#login")
//
//    session.waitFor(By.className("cb-username"), Condition.isClickable)
//      .toTextInput.setValue("matt.cully@barclays.com")
//      .waitFor(By.className("cb-password"), Condition.isClickable)
//      .toTextInput.setValue("Password!")
//      .waitFor(By.className("cb-login"), Condition.isClickable)
//      .toButton.click()
//
//    println(session.getCapabilities)
//
//  })

}


