package org.kaloz.excercise.payment

import akka.actor.{ActorRef, Props, Actor, ActorSystem}
import scala._
import scala.concurrent.duration._
import scala.io.Source.{fromInputStream, stdin}
import java.io.InputStream

object PaymentTracker {
  def main(args: Array[String]) {
    val fileInputStream = getClass.getClassLoader.getResourceAsStream(if (args.length == 1) args(0) else "defaultHistory.txt")

    val system = ActorSystem("PaymentSystem")
    val paymentTrackerActor = system.actorOf(Props[PaymentTrackerActor], name = "paymentTracker")
    val paymentHistoryLoaderActor = system.actorOf(Props(classOf[PaymentHistoryLoaderActor], paymentTrackerActor, fileInputStream), name = "paymentHistoryLoader")

    new PaymentTracker(paymentTrackerActor, paymentHistoryLoaderActor)
  }
}

class PaymentTracker(paymentTrackerActor: ActorRef, paymentHistoryLoaderActor: ActorRef) {

  paymentHistoryLoaderActor ! Load

  for (ln <- stdin.getLines) ln match {
    case PaymentInputExtractor(payment) => paymentTrackerActor ! payment
    case SetupInputExtractor(setup) => paymentTrackerActor ! setup
    case "print" => paymentTrackerActor ! Print
    case "quit" => sys.exit()
    case "?" => println("exampe:HUF 100 0.0043|print|setup 10|?|quit")
    case _ => println("Wrong format!")
  }
}

object PaymentInputExtractor {
  val paymentInputPattern = """([A-Z]{3}) ([+-]?[1-9][0-9]*\.?[0-9]*)( (?:[1-9]\d*|0)?(?:\.\d+)?)?""".r

  def unapply(row: String): Option[Payment] = row match {
    case paymentInputPattern(currency, amount, exchangeRate) =>
      Some(Payment(Currency.withName(currency), BigDecimal(amount), exchangeRate match {
        case s: String if (s.trim.length > 0) => Some(BigDecimal(s trim))
        case _ => None
      }))
    case _ => None
  }
}

object SetupInputExtractor {
  val setupInputPattern = """setup (\d*)""".r

  def unapply(row: String): Option[Setup] = row match {
    case setupInputPattern(time) => Some(Setup(time.toInt))
    case _ => None
  }
}

class PaymentHistoryLoaderActor(paymentTrackerActor: ActorRef, fileInputStream: InputStream) extends Actor {

  def receive = {
    case Load =>
      for (line <- fromInputStream(fileInputStream).getLines()) line match {
        case PaymentInputExtractor(payment) => paymentTrackerActor ! payment
        case s: String => println("Wrong format in file: " + s)
      }
  }
}

class PaymentTrackerActor extends Actor {

  import context.dispatcher

  var payments = Map.empty[Currency.Value, PaymentSummary]
  var scheduleTime = 10 seconds

  override def preStart() = initializePrinter

  def receive = {
    case Payment(currency, amount, exchangeRate) =>
      val payment = payments.get(currency).getOrElse(PaymentSummary(currency, BigDecimal(0), None))
      payments += (currency -> payment.addAmount(amount).setExchangeRate(exchangeRate))
    case ScheduledPrint =>
      initializePrinter
      printPayments
    case Print => printPayments
    case Setup(time) => scheduleTime = time seconds
  }

  def printPayments {
    payments.values.filter( _.sum > 0).foreach(payment => println(payment))
  }

  def initializePrinter {
    context.system.scheduler.scheduleOnce(scheduleTime, self, ScheduledPrint)
  }
}

object Currency extends Enumeration {
  type Currency = Value
  val HUF, GBP, USD, CHF, EUR = Value
}

case class Payment(currency: Currency.Value, amount: BigDecimal, exchangeRate: Option[BigDecimal])

case object ScheduledPrint

case object Print

case object Load

case class Setup(second: Int)

case class PaymentSummary(currency: Currency.Value, sum: BigDecimal, exchangeRate: Option[BigDecimal]) {
  override def toString = s"${currency} ${sum}${exchangeRate.flatMap( e => Some(s" (${sum * e} USD)")).getOrElse("")}"

  def addAmount(amount: BigDecimal) = PaymentSummary(currency, amount + sum, exchangeRate)

  def setExchangeRate(newExchangeRate: Option[BigDecimal]) = if (newExchangeRate.isDefined) PaymentSummary(currency, sum, newExchangeRate) else PaymentSummary(currency, sum, exchangeRate)
}


