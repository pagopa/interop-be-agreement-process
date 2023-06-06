package it.pagopa.interop.agreementprocess.service

import spray.json.JsonWriter

import scala.concurrent.Future

trait QueueService {
  def send[T: JsonWriter](message: T): Future[String]
}
