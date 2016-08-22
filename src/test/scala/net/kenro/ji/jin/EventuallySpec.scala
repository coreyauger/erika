package net.kenro.ji.jin

import io.shaka.http.Response
import io.shaka.http.Status.OK
import org.scalatest.{FreeSpec, Matchers}

import scala.collection.mutable

class EventuallySpec extends FreeSpec with Matchers {

  class TestResponse[A,B] {

    private var responses = new mutable.Queue[A]()
    private var captures: List[B] = List.empty

    def setResponses(values: List[A]) = {
       values.foreach(v => responses += v)
    }

    def respond(): A = {
      if(responses.isEmpty){
        throw QueueEmptyError("Error: The response queue was empty")
      }
      responses.dequeue()
    }

    def recordResult(value: B) = {
      captures = value :: captures
    }

    def getResult = captures.reverse

    def reset = {
      captures = List.empty
      responses = new mutable.Queue[A]()
    }

  }

  object TestResponse {
    def apply[A,B]() = new TestResponse[A,B]()
  }

  "Eventually" - {

    "reTryFunction should retry" in {
      val stub = TestResponse[() => Int,Boolean]()

      val unhappy: () => Int = () => { throw APIResponseError("something failed") }
      val timeout: () => Int = () => { throw new java.net.SocketTimeoutException }
      val happy1: () => Int = () => { 1 }
      val happy2: () => Int = () => { 2 }
      val happy3: () => Int = () => { 3 }

      stub.setResponses(List(unhappy, timeout, happy1, happy2, happy3))

      val tryFunc: () => FunctionResult[String] = () => {
        val outcome = stub.respond()() > 2
        stub.recordResult(outcome)
        if (outcome) FunctionResult[String](outcome, "cool") else FunctionResult[String](outcome, "yikes")
      }

      val result: FunctionResult[String] = Eventually().reTryFunction(tryFunc, 5)
      result.wasSatisfied should be(true)
      result.payLoad should be("cool")
      stub.getResult should be(List(false,false,true))
    }

    "reTryFunction should return FunctionResult set to false if exception is thrown in runnable" in {
      val tryFunc: () => FunctionResult[String] = () => {
        throw APIResponseError("runnable threw exception")
      }

      val result: FunctionResult[String] = Eventually().reTryFunction(tryFunc, 1)
      result.wasSatisfied should be(false)
      result.payLoad should be("[reTryFunction] Exception happened when calling function - exception was: net.kenro.ji.jin.APIResponseError: runnable threw exception")
    }

    "reTryHttp should retry" in {
      val stub = TestResponse[() => Response,String]()

      val unhappy: () => Response = () => { stub.recordResult("error") ; throw APIResponseError("something failed") }
      val timeout: () => Response = () => { stub.recordResult("timeout") ; throw new java.net.SocketTimeoutException }
      val happy: () => Response = () => { stub.recordResult("happy") ; Response.respond("happy") }

      stub.setResponses(List(unhappy, timeout, happy))

      val httpFunc: () => Response = () => {
        stub.respond()()
      }

      val result: Response = Eventually().reTryHttp(httpFunc)
      result.status should be(OK)
      stub.getResult should be(List("error","timeout","happy"))

    }

    "tryExecute should retry" in {
      val stub = TestResponse[() => Unit,String]()

      val unhappy: () => Unit = () => { stub.recordResult("error") ; throw APIResponseError("something failed") }
      val timeout: () => Unit = () => { stub.recordResult("timeout") ; throw new java.net.SocketTimeoutException }
      val happy: () => Unit = () => { stub.recordResult("happy") }

      stub.setResponses(List(unhappy, timeout, happy))

      val execFunc: () => Unit = () => {
        stub.respond()()
      }

      Eventually().tryExecute(execFunc)
      stub.getResult should be(List("error","timeout","happy"))

    }


  }

}




