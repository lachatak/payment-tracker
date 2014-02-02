package org.kaloz.excercise.payment

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification
import org.specs2.specification.AllExpectations
import org.specs2.mock.Mockito
import akka.testkit.{TestProbe, TestActorRef}
import akka.actor.{Actor, ActorSystem, Props}
import scala.Some
import java.io.ByteArrayInputStream
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

@RunWith(classOf[JUnitRunner])
class PaymentTrackerSpec extends Specification with AllExpectations with Mockito {

  implicit val system = ActorSystem("PaymentSystemTest")

  sequential

  "A PaymentInputExtractor" should {
    "extract a currency and a floating amount separated by space" in {
      val payment = PaymentInputExtractor.unapply("EUR 123.32")

      payment mustEqual Some(Payment(Currency.EUR, BigDecimal(123.32), None))
    }

    "extract a currency and a decimal amount separated by space" in {
      val payment = PaymentInputExtractor.unapply("EUR 200")

      payment mustEqual Some(Payment(Currency.EUR, BigDecimal(200), None))
    }

    "extract a currency, a decimal amount and a decimal exchange rate separated by space" in {
      val payment = PaymentInputExtractor.unapply("EUR 200 2")

      payment mustEqual Some(Payment(Currency.EUR, BigDecimal(200), Some(BigDecimal(2))))
    }

    "extract a currency, a floating amount and a decimal exchange rate separated by space" in {
      val payment = PaymentInputExtractor.unapply("EUR 200.2 2")

      payment mustEqual Some(Payment(Currency.EUR, BigDecimal(200.2), Some(BigDecimal(2))))
    }

    "extract a currency, a floating amount and a floating exchange rate separated by space" in {
      val payment = PaymentInputExtractor.unapply("EUR 200.2 1.2")

      payment mustEqual Some(Payment(Currency.EUR, BigDecimal(200.2), Some(BigDecimal(1.2))))
    }

    "not extract an unknown currency" in {
      val payment = PaymentInputExtractor.unapply("ZLY 200")

      payment mustEqual None
    }
  }

  "A SetupInputExtractor" should {
    "extract schedule time" in {
      val setup = SetupInputExtractor.unapply("setup 5")

      setup mustEqual Some(Setup(5))
    }

    "not extract an unknown time interval" in {
      val setup = SetupInputExtractor.unapply("setup ghj")

      setup mustEqual None
    }
  }

  "A PaymentSummary" should {
    "create a new instance of PaymentSummary when use addAmount method" in {
      val paymentSummary = PaymentSummary(Currency.EUR, BigDecimal(10), None)
      val newPaymentSummary = paymentSummary.addAmount(BigDecimal(10))

      newPaymentSummary mustEqual PaymentSummary(Currency.EUR, BigDecimal(20), None)
      paymentSummary mustEqual PaymentSummary(Currency.EUR, BigDecimal(10), None)
    }

    "create a new instance of PaymentSummary when use setExchangeRate method" in {
      val paymentSummary = PaymentSummary(Currency.EUR, BigDecimal(10), None)
      val newPaymentSummary = paymentSummary.setExchangeRate(Some(BigDecimal(10)))

      newPaymentSummary mustEqual PaymentSummary(Currency.EUR, BigDecimal(10), Some(BigDecimal(10)))
      paymentSummary mustEqual PaymentSummary(Currency.EUR, BigDecimal(10), None)
    }

    "create a new instance of PaymentSummary when use setExchangeRate method but doesn't override exchange rate if it has a value but the input is None" in {
      val paymentSummary = PaymentSummary(Currency.EUR, BigDecimal(10), Some(BigDecimal(10)))
      val newPaymentSummary = paymentSummary.setExchangeRate(None)

      newPaymentSummary mustEqual PaymentSummary(Currency.EUR, BigDecimal(10), Some(BigDecimal(10)))
      paymentSummary mustEqual PaymentSummary(Currency.EUR, BigDecimal(10), Some(BigDecimal(10)))
    }
  }

  "A PaymentHistoryLoaderActor" should {
    "read from the provided stream and send content to the paymentTrackerActor" in {
      val sep = System.getProperty("line.separator")
      val inputStream = new ByteArrayInputStream(s"EUR 200.2${sep}notvalid${sep}CHF 200.3 1.4".getBytes)
      val paymentTrackerActor = TestActorRef[DummyPaymentTrackerActor]
      val paymentHistoryLoaderActor = TestActorRef(Props(classOf[PaymentHistoryLoaderActor], paymentTrackerActor, inputStream), name = "paymentHistoryLoader")

      paymentHistoryLoaderActor ! Load

      paymentTrackerActor.underlyingActor.lastMsg mustEqual Payment(Currency.CHF, BigDecimal(200.3), Some(BigDecimal(1.4)))
      paymentTrackerActor.underlyingActor.messages.size mustEqual 2
    }
  }

  "A PaymentTrackerActor" should {
    "collect Payments group by currency" in {
      val paymentTrackerActor = TestActorRef[PaymentTrackerActor]

      paymentTrackerActor ! Payment(Currency.EUR, BigDecimal(200.3), None)
      paymentTrackerActor ! Payment(Currency.EUR, BigDecimal(100), None)
      paymentTrackerActor ! Payment(Currency.CHF, BigDecimal(100.4), Some(BigDecimal(1.5)))

      paymentTrackerActor.underlyingActor.payments(Currency.EUR) mustEqual PaymentSummary(Currency.EUR, BigDecimal(300.3), None)
      paymentTrackerActor.underlyingActor.payments(Currency.CHF) mustEqual PaymentSummary(Currency.CHF, BigDecimal(100.4), Some(BigDecimal(1.5)))
      paymentTrackerActor.underlyingActor.payments.size mustEqual 2
    }

    "save scheduler period" in {
      val paymentTrackerActor = TestActorRef[PaymentTrackerActor]

      paymentTrackerActor ! Setup(5)
      paymentTrackerActor.underlyingActor.scheduleTime.length mustEqual 5
    }

    "start a printer scheduler" in {
      val paymentTrackerActor = TestActorRef[PaymentTrackerActor]
      val probe = TestProbe()
      probe watch paymentTrackerActor

      val msg = probe.expectMsg(Duration.create(5, TimeUnit.SECONDS), ScheduledPrint)
      msg mustEqual Print
    }
  }

  step(system.shutdown)
}

class DummyPaymentTrackerActor extends Actor {

  var messages: List[Any] = List.empty

  def lastMsg = messages.last

  def receive = {
    case m: Any => messages = messages :+ m
  }
}