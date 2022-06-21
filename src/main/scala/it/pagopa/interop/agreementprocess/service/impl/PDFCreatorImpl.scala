package it.pagopa.interop.agreementprocess.service.impl

import com.openhtmltopdf.util.XRLog
import it.pagopa.interop.agreementprocess.service.PDFCreator
import it.pagopa.interop.commons.files.service.PDFManager

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.Future
import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.util.Try

object PDFCreatorImpl extends PDFCreator with PDFManager {

  // Suppressing openhtmltopdf log
  XRLog.listRegisteredLoggers.asScala.foreach((logger: String) =>
    XRLog.setLevel(logger, java.util.logging.Level.SEVERE)
  )

  override def create(template: String, eservice: String, producer: String, consumer: String): Future[File] =
    Future.fromTry {
      for {
        file <- createTempFile
        data = setupData(eservice, producer, consumer)
        pdf <- getPDFAsFile(file.toPath, template, data)
      } yield pdf

    }

  private def createTempFile: Try[File] = {
    Try {
      val fileTimestamp: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
      File.createTempFile(s"${fileTimestamp}_${UUID.randomUUID().toString}_richiesta_di_fruizione.", ".pdf")
    }
  }

  private def setupData(eservice: String, producer: String, consumer: String): Map[String, String] =
    Map("eservice.name" -> eservice, "producer.name" -> producer, "consumer.name" -> consumer)

}
