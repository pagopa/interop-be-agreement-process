package it.pagopa.interop.agreementprocess.service

import scala.concurrent.Future

trait PDFCreator {
  def create(template: String, eservice: String, producer: String, consumer: String): Future[Array[Byte]]
}
