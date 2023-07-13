package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.agreementprocess.service.QueueService
import it.pagopa.interop.commons.queue.impl.SQSHandler
import spray.json.JsonWriter

import scala.concurrent.Future

final class QueueServiceImpl(sqsHandler: SQSHandler) extends QueueService {

  override def send[T: JsonWriter](message: T): Future[String] = sqsHandler.send(message)
}
